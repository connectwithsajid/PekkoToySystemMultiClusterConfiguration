package com.pekko.toy;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.actors.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import com.typesafe.config.Config;

public class ActorPoolCoordinator extends AbstractBehavior<ActorPoolCoordinator.Command> {

    public interface Command {}

    public static class StartProcessing implements Command {}
    public static class ProcessQuery implements Command {
        public final String queryId;
        public ProcessQuery(String queryId) { this.queryId = queryId; }
    }

    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final List<ActorRef<FilterVertexActor.Command>> vertexRouters = new ArrayList<>();
    private final List<ActorRef<FilterEdgeActor.Command>> edgeRouters = new ArrayList<>();
    private final List<ActorRef<FilterProjectPropertyActor.Command>> propertyRouters = new ArrayList<>();
    private final int vertexChunkSize;
    private final int edgeChunkSize;
    private final int propertyChunkSize;

    public static Behavior<Command> create(Config config) {
        return Behaviors.setup(context -> new ActorPoolCoordinator(context, config));
    }

    private final ActorContext<Command> context;
    private final Config config;

    public static int getChunkSize(Config config, String actorType) {
        List<? extends Config> pools = config.getConfigList("pekko.toy-system.actor-pools");
        for (Config pool : pools) {
            if (pool.getString("type").equals(actorType)) {
                return pool.getInt("chunk-size");
            }
        }
        throw new IllegalArgumentException("No chunk-size for actor type: " + actorType);
    }

    public ActorPoolCoordinator(ActorContext<Command> context, Config config) {
        super(context);
        this.context = context;
        this.config = config;

        this.vertexChunkSize = getChunkSize(config, "vertex");
        this.edgeChunkSize = getChunkSize(config, "edge");
        this.propertyChunkSize = getChunkSize(config, "property");

        createActorPools();
        setupActorCommunication();
    }

    private void createActorPools() {
        vertexRouters.add(context.spawn(FilterVertexActor.create(vertexChunkSize), "vertex-router"));
        edgeRouters.add(context.spawn(FilterEdgeActor.create(edgeChunkSize), "edge-router"));
        propertyRouters.add(context.spawn(FilterProjectPropertyActor.create(propertyChunkSize, totalCount), "property-router"));
    }

    private void setupActorCommunication() {
        context.scheduleOnce(
                Duration.ofSeconds(1),
                context.getSelf(),
                new StartProcessing()
        );
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartProcessing.class, this::onStartProcessing)
                .onMessage(ProcessQuery.class, this::onProcessQuery)
                .build();
    }

    private Behavior<Command> onStartProcessing(StartProcessing command) {
        totalCount.set(0);

        // Here you could parse or generate the network packet from JSON or config
        context.getSelf().tell(new ProcessQuery("query-1"));
        return this;
    }

    private Behavior<Command> onProcessQuery(ProcessQuery command) {
        buildAndSendInitialPacket(command.queryId);
        return this;
    }

    private void buildAndSendInitialPacket(String queryId) {
        ActorRef<FilterVertexActor.Command> vertexRouter = getRandomRouter(vertexRouters);
        ActorRef<FilterEdgeActor.Command> edgeRouter = getRandomRouter(edgeRouters);
        ActorRef<FilterProjectPropertyActor.Command> propertyRouter = getRandomRouter(propertyRouters);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode()
                .put("Vid", "long")
                .put("Count", "int")
                .put("Name", "string");

        //todo: Change this to JSON Object
        ObjectNode metadata =  mapper.createObjectNode();
        metadata.set("schema", schema);
        metadata.put("sortAttribute", "Vid");
        metadata.put("queryId", queryId);
        metadata.put("source", "coordinator");
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("packetType", "vertex");
        // NOTE: No nextOperator yet for initial packet

        ArrayNode data = mapper.createArrayNode();

        ObjectNode networkPacket = mapper.createObjectNode();
        networkPacket.set("metadata", metadata);
        networkPacket.set("data", data);

        if (vertexRouter != null && edgeRouter != null && propertyRouter != null) {
            vertexRouter.tell(new FilterVertexActor.ProduceVertices(
                    networkPacket,
                    edgeRouter,
                    propertyRouter
            ));
            context.getLog().info("=== DISTRIBUTED QUERY {} PROCESSING STARTED ===", queryId);
        } else {
            context.getLog().warn("One or more router pools are empty");
        }
    }

    private <T> ActorRef<T> getRandomRouter(List<ActorRef<T>> routers) {
        if (routers.isEmpty()) return null;
        return routers.get(ThreadLocalRandom.current().nextInt(routers.size()));
    }
}

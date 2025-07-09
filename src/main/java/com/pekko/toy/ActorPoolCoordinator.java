package com.pekko.toy;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.actors.*;
import com.typesafe.config.Config;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.routing.SmallestMailboxRoutingLogic;

public class ActorPoolCoordinator extends AbstractBehavior<ActorPoolCoordinator.Command> {

    public interface Command {}
    public static class StartProcessing implements Command {}
    public static class ProcessQuery implements Command {
        public final String queryId;
        public ProcessQuery(String queryId) {
            this.queryId = queryId;
        }
    }
    private final AtomicInteger totalCount = new AtomicInteger(0);

    private final List<ActorRef<FilterVertexActor.Command>> vertexRouters = new ArrayList<>();
    private final List<ActorRef<FilterEdgeActor.Command>> edgeRouters = new ArrayList<>();
    private final List<ActorRef<FilterProjectPropertyActor.Command>> propertyRouters = new ArrayList<>();

    public static Behavior<Command> create(Config config) {
        return Behaviors.setup(context -> new ActorPoolCoordinator(context, config));
    }

    private final ActorContext<Command> context;
    private final Config config;



    public ActorPoolCoordinator(ActorContext<Command> context, Config config) {
        super(context);
        this.context = context;
        this.config = config;
        createActorPools();
        setupActorCommunication();
    }

    private void createActorPools() {
        List<? extends Config> pools = config.getConfigList("pekko.toy-system.actor-pools");

        for (Config poolConfig : pools) {
            String type = poolConfig.getString("type");
            int instances = poolConfig.getInt("instances");
            int poolCount = poolConfig.hasPath("pools") ? poolConfig.getInt("pools") : 1;

            for (int poolIndex = 1; poolIndex <= poolCount; poolIndex++) {
                switch (type) {
                    case "vertex":
                        vertexRouters.add(createRouterPool(
                                FilterVertexActor.create(poolIndex, 0),
                                instances,
                                "vertex-pool-" + poolIndex
                        ));
                        break;

                    case "edge":
                        edgeRouters.add(createRouterPool(
                                FilterEdgeActor.create(poolIndex, 0),
                                instances,
                                "edge-pool-" + poolIndex
                        ));
                        break;

                    case "property":
                        propertyRouters.add(createRouterPool(
                                FilterProjectPropertyActor.create(poolIndex, 0, totalCount),
                                instances,
                                "property-pool-" + poolIndex
                        ));
                        break;
                }
            }

            getContext().getLog().info("Created {} pools of {} {} actors ({} instances each)",
                    poolCount, instances, type, instances);
        }
    }

    private <T> ActorRef<T> createRouterPool(Behavior<T> behavior, int instances, String name) {
        PoolRouter<T> pool = Routers.pool(instances, behavior).withRoundRobinRouting();
        return getContext().spawn(pool, name);
    }

    private void setupActorCommunication() {
        getContext().scheduleOnce(
                Duration.ofSeconds(3),
                getContext().getSelf(),
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
        // Start multiple queries
        totalCount.set(0);

        for (int i = 1; i <= 1; i++) {
            getContext().getSelf().tell(new ProcessQuery("query-" + i));
        }
        return this;
    }

    private Behavior<Command> onProcessQuery(ProcessQuery command) {
        processDistributed(command.queryId);
        return this;
    }

    private void processDistributed(String queryId) {
        // Randomly select routers
        ActorRef<FilterVertexActor.Command> vertexRouter = getRandomRouter(vertexRouters);
        ActorRef<FilterEdgeActor.Command> edgeRouter = getRandomRouter(edgeRouters);
        ActorRef<FilterProjectPropertyActor.Command> propertyRouter = getRandomRouter(propertyRouters);

        if (vertexRouter != null && edgeRouter != null && propertyRouter != null) {
            vertexRouter.tell(new FilterVertexActor.ProduceVertices(
                    edgeRouter,
                    propertyRouter
            ));
            getContext().getLog().info("=== DISTRIBUTED QUERY {} PROCESSING STARTED ===", queryId);
        } else {
            getContext().getLog().warn("One or more router pools are empty");
        }
    }

    private <T> ActorRef<T> getRandomRouter(List<ActorRef<T>> routers) {
        if (routers.isEmpty()) return null;
        return routers.get(ThreadLocalRandom.current().nextInt(routers.size()));
    }



}

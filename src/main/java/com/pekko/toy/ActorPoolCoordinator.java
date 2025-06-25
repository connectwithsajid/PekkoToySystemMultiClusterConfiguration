package com.pekko.toy;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.actors.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;

import java.util.List;
import java.util.ArrayList;
import org.apache.pekko.actor.typed.receptionist.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist.*;

import java.time.Duration;

public class ActorPoolCoordinator extends AbstractBehavior<ActorPoolCoordinator.Command> {

    public interface Command {}
    public static class StartProcessing implements Command {}


    public static Behavior<Command> create(Config config) {
        return Behaviors.setup(context -> new ActorPoolCoordinator(context, config));
    }

    private final ActorContext<Command> context;
    private final Config config;

    private final List<ActorRef<FilterEdgeActor.Command>> edgeActors = new ArrayList<>();
    private final List<ActorRef<FilterProjectPropertyActor.Command>> propertyActors = new ArrayList<>();

    private final ServiceKey<FilterEdgeActor.Command> edgeKey =
            ServiceKey.create(FilterEdgeActor.Command.class, "edge-actors");
    private final ServiceKey<FilterProjectPropertyActor.Command> propertyKey =
            ServiceKey.create(FilterProjectPropertyActor.Command.class, "property-actors");
    private final ServiceKey<FilterVertexActor.Command> vertexKey =
            ServiceKey.create(FilterVertexActor.Command.class, "vertex-actors");

    private ActorPoolCoordinator(ActorContext<Command> context, Config config) {
        super(context);
        this.context = context;
        this.config = config;
        spawnActorPools();
    }

    private void spawnActorPools() {
        List<? extends Config> pools = config.getConfigList("pekko.toy-system.actor-pools");

        for (Config poolConfig : pools) {
            String type = poolConfig.getString("type");
            int instances = poolConfig.getInt("instances");
            int poolCount = poolConfig.hasPath("pools") ? poolConfig.getInt("pools") : 1;

            for (int p = 1; p < poolCount + 1; p++) {
                for (int i = 1; i < instances + 1; i++) {
                    switch (type) {
                        case "vertex":
                            ActorRef<FilterVertexActor.Command> vertexActor =
                                    context.spawn(FilterVertexActor.create(p, i),
                                            "vertex-actor-" + p + "-" + i);
                            context.getSystem().receptionist().tell(Receptionist.register(vertexKey, vertexActor));
                            break;
                        case "edge":
                            ActorRef<FilterEdgeActor.Command> edgeActor =
                                    context.spawn(FilterEdgeActor.create(p, i),
                                            "edge-actor-" + p + "-" + i);

                            context.getSystem().receptionist().tell(Receptionist.register(edgeKey, edgeActor));
                            edgeActors.add(edgeActor);

                            break;
                        case "property":
                            ActorRef<FilterProjectPropertyActor.Command> propertyActor =
                                    context.spawn(FilterProjectPropertyActor.create(p, i),
                                            "property-actor-" + p + "-" + i);
                            context.getSystem().receptionist().tell(Receptionist.register(propertyKey, propertyActor));
                            propertyActors.add(propertyActor);

                            break;
                    }
                }
            }

            context.getLog().info("Spawned {} pools of {} {} actors ({} instances each)",
                    poolCount, instances, type, instances);
        }
        context.getSelf().tell(new StartProcessing());

//        setupActorCommunication();

    }
    private Behavior<Command> onStartProcessing(StartProcessing command) {
        // Create routers AFTER all actors are registered
        // Create vertex router
        ActorRef<FilterVertexActor.Command> vertexRouter =
                context.spawn(Routers.group(vertexKey), "vertex-router");


        ActorRef<FilterEdgeActor.Command> edgeRouter =
                context.spawn(Routers.group(edgeKey), "edge-router");

        ActorRef<FilterProjectPropertyActor.Command> propertyRouter =
                context.spawn(Routers.group(propertyKey), "property-router");

        // Add a delay before sending the first message to the router
        context.scheduleOnce(
                Duration.ofSeconds(3), // 500ms delay
                vertexRouter,
                new FilterVertexActor.ProduceVertices(edgeRouter, propertyRouter)
        );

        // Start vertex processing
//        vertexRouter.tell(new FilterVertexActor.ProduceVertices(edgeRouter,propertyRouter));

        context.getLog().info("=== PROCESSING STARTED ===");
        return this;
    }
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartProcessing.class, this::onStartProcessing)
                .build();
    }
}
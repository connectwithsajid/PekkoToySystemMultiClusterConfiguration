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
import java.util.concurrent.ThreadLocalRandom;


import java.time.Duration;

public class ActorPoolCoordinator extends AbstractBehavior<ActorPoolCoordinator.Command> {

    public interface Command {}
    public static class StartProcessing implements Command {}

    private final List<ActorRef<FilterVertexActor.Command>> vertexRouters = new ArrayList<>();
    private final List<ActorRef<FilterEdgeActor.Command>> edgeRouters = new ArrayList<>();
    private final List<ActorRef<FilterProjectPropertyActor.Command>> propertyRouters = new ArrayList<>();

    public static Behavior<Command> create(Config config) {
        return Behaviors.setup(context -> new ActorPoolCoordinator(context, config));
    }

    private final ActorContext<Command> context;
    private final Config config;

//    private final List<ActorRef<FilterEdgeActor.Command>> edgeActors = new ArrayList<>();
//    private final List<ActorRef<FilterProjectPropertyActor.Command>> propertyActors = new ArrayList<>();

//    private final ServiceKey<FilterEdgeActor.Command> edgeKey =
//            ServiceKey.create(FilterEdgeActor.Command.class, "edge-actors");
//    private final ServiceKey<FilterProjectPropertyActor.Command> propertyKey =
//            ServiceKey.create(FilterProjectPropertyActor.Command.class, "property-actors");
//    private final ServiceKey<FilterVertexActor.Command> vertexKey =
//            ServiceKey.create(FilterVertexActor.Command.class, "vertex-actors");

    private ActorPoolCoordinator(ActorContext<Command> context, Config config) {
        super(context);
        this.context = context;
        this.config = config;
        createActorPools();
//        spawnActorPools();
        setupActorCommunication();
    }
    private void createActorPools() {
        List<? extends Config> pools = config.getConfigList("pekko.toy-system.actor-pools");

        for (Config poolConfig : pools) {
            String type = poolConfig.getString("type");
            int instances = poolConfig.getInt("instances");
            int poolCount = poolConfig.hasPath("pools") ? poolConfig.getInt("pools") : 1;

            // this logic can be reversed
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
                                FilterProjectPropertyActor.create(poolIndex, 0),
                                instances,
                                "property-pool-" + poolIndex
                        ));
                        break;
                }
            }

            context.getLog().info("Created {} pools of {} {} actors ({} instances each)",
                    poolCount, instances, type, instances);
        }
    }

    private <T> ActorRef<T> createRouterPool(Behavior<T> behavior, int instances, String name) {
        PoolRouter<T> pool = Routers.pool(instances, behavior)
                .withRandomRouting();
        return context.spawn(pool, name);
    }

    private void setupActorCommunication() {
        context.scheduleOnce(
                Duration.ofSeconds(3),
                context.getSelf(),
                new StartProcessing()
        );
    }


//    private void spawnActorPools() {
//        List<? extends Config> pools = config.getConfigList("pekko.toy-system.actor-pools");
//
//        for (Config poolConfig : pools) {
//            String type = poolConfig.getString("type");
//            int instances = poolConfig.getInt("instances");
//            int poolCount = poolConfig.hasPath("pools") ? poolConfig.getInt("pools") : 1;
//
//            for (int p = 1; p < poolCount + 1; p++) {
//                for (int i = 1; i < instances + 1; i++) {
//                    switch (type) {
//                        case "vertex":
//                            ActorRef<FilterVertexActor.Command> vertexActor =
//                                    context.spawn(FilterVertexActor.create(p, i),
//                                            "vertex-actor-" + p + "-" + i);
//                            context.getSystem().receptionist().tell(Receptionist.register(vertexKey, vertexActor));
//                            break;
//                        case "edge":
//                            ActorRef<FilterEdgeActor.Command> edgeActor =
//                                    context.spawn(FilterEdgeActor.create(p, i),
//                                            "edge-actor-" + p + "-" + i);
//
//                            context.getSystem().receptionist().tell(Receptionist.register(edgeKey, edgeActor));
//                            edgeActors.add(edgeActor);
//
//                            break;
//                        case "property":
//                            ActorRef<FilterProjectPropertyActor.Command> propertyActor =
//                                    context.spawn(FilterProjectPropertyActor.create(p, i),
//                                            "property-actor-" + p + "-" + i);
//                            context.getSystem().receptionist().tell(Receptionist.register(propertyKey, propertyActor));
//                            propertyActors.add(propertyActor);
//
//                            break;
//                    }
//                }
//            }
//
//            context.getLog().info("Spawned {} pools of {} {} actors ({} instances each)",
//                    poolCount, instances, type, instances);
//        }
//        context.getSelf().tell(new StartProcessing());
//
////        setupActorCommunication();
//
//    }


    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartProcessing.class, this::onStartProcessing)
                .build();
    }

    private Behavior<Command> onStartProcessing(StartProcessing command)  {
        // Randomly select a router from each pool list
        ActorRef<FilterVertexActor.Command> randomVertexRouter = getRandomRouter(vertexRouters);
        ActorRef<FilterEdgeActor.Command> randomEdgeRouter = getRandomRouter(edgeRouters);
        ActorRef<FilterProjectPropertyActor.Command> randomPropertyRouter = getRandomRouter(propertyRouters);

        if (randomVertexRouter != null && randomEdgeRouter != null && randomPropertyRouter != null) {
            randomVertexRouter.tell(new FilterVertexActor.ProduceVertices(
                    randomEdgeRouter,
                    randomPropertyRouter
            ));
            context.getLog().info("=== PROCESSING STARTED (Random Pool Selection) ===");
        } else {
            context.getLog().warn("One or more router pools are empty, cannot start processing.");
        }
        return this;
    }


    // Helper method for random router selection
    private <T> ActorRef<T> getRandomRouter(List<ActorRef<T>> routers) {
        if (routers.isEmpty()) return null;
        return routers.get(ThreadLocalRandom.current().nextInt(routers.size()));
    }
}
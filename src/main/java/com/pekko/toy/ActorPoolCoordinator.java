package com.pekko.toy;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.actors.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import java.util.List;

public class ActorPoolCoordinator extends AbstractBehavior<ActorPoolCoordinator.Command> {

    public interface Command {}

    public static Behavior<Command> create(Config config) {
        return Behaviors.setup(context -> new ActorPoolCoordinator(context, config));
    }

    private final ActorContext<Command> context;
    private final Config config;

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

            for (int p = 1; p < poolCount+1; p++) {
                for (int i = 1; i < instances+1; i++) {
                    switch (type) {
                        case "vertex":
                            context.spawn(FilterVertexActor.create(p, i),
                                    "vertex-actor-" + p + "-" + i);
                            break;
                        case "edge":
                            context.spawn(FilterEdgeActor.create(p, i),
                                    "edge-actor-" + p + "-" + i);
                            break;
                        case "property":
                            context.spawn(FilterProjectPropertyActor.create(p, i),
                                    "property-actor-" + p + "-" + i);
                            break;
                    }
                }
            }

            context.getLog().info("Spawned {} pools of {} {} actors ({} instances each)",
                    poolCount, instances, type, instances);
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().build();
    }
}

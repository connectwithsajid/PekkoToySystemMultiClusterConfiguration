package com.pekko.toy;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class MultiClusterPekkoToySystemMain {

    public static void main(String[] args) {
        String clusterId = (args.length > 0) ? args[0] : "1";
        Config config = ConfigFactory.load("cluster" + clusterId + ".conf");

        ActorSystem.create(RootBehavior.create(), "PekkoToySystem", config);
    }

    public static class RootBehavior {
        public static Behavior<Void> create() {
            return Behaviors.setup(context -> {
                // Start actor pool coordinator
                Config config = context.getSystem().settings().config();
                context.spawn(
                        ActorPoolCoordinator.create(config),
                        "actor-pool-coordinator"
                );

                context.getLog().info("Cluster node started with roles: {}",
                        config.getStringList("pekko.cluster.roles"));

                return Behaviors.empty();
            });
        }
    }
}

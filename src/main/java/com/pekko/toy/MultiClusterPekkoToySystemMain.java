package com.pekko.toy;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class MultiClusterPekkoToySystemMain {

    public static void main(String[] args) {
        // You can pass config file or cluster name as args, or use defaults
        String configFile = (args.length > 0) ? args[0] : "application.conf";
        String clusterName = (args.length > 1) ? args[1] : "cluster1";

        // Load the application config file
        Config baseConfig = ConfigFactory.load(configFile);

        // Extract the cluster-specific config if needed; else just use baseConfig
        Config clusterConfig = baseConfig.hasPath("clusters." + clusterName)
                ? baseConfig.getConfig("clusters." + clusterName).withFallback(baseConfig)
                : baseConfig;

        // Start the ActorSystem with the merged config
        ActorSystem.create(RootBehavior.create(), "PekkoToySystem", clusterConfig);
    }

    public static class RootBehavior {
        public static Behavior<Void> create() {
            return Behaviors.setup(context -> {
                Config config = context.getSystem().settings().config();
                context.spawn(
                        ActorPoolCoordinator.create(config),
                        "actor-pool-coordinator"
                );
                context.getLog().info("Cluster node started with roles: {}",
                        config.hasPath("pekko.cluster.roles")
                                ? config.getStringList("pekko.cluster.roles")
                                : "[none]");
                return Behaviors.empty();
            });
        }
    }
}

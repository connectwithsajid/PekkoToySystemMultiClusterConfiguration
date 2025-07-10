package com.pekko.toy;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class MultiClusterPekkoToySystemMain {

//    public static void main(String[] args) {
//        String clusterId = (args.length > 0) ? args[0] : "1";
//        Config config = ConfigFactory.load("cluster" + clusterId + ".conf");
//
//        ActorSystem.create(RootBehavior.create(), "PekkoToySystem", config);
//    }

    public static void main(String[] args) {
        String configFile = (args.length > 0) ? args[0] : "application.conf";
        String clusterName = (args.length > 1) ? args[1] : "cluster1";

        // Load the application config file
        Config baseConfig = ConfigFactory.load(configFile);

        // Extract the cluster-specific config
        Config clusterConfig = baseConfig.getConfig("clusters." + clusterName);

        // Merge with base config so global settings are available
        Config mergedConfig = clusterConfig.withFallback(baseConfig);

        // Start the ActorSystem with the merged config
        ActorSystem.create(RootBehavior.create(), "PekkoToySystem", mergedConfig);
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

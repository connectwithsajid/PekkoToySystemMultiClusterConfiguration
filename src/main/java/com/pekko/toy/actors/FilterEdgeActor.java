package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import java.util.ArrayList;
import java.util.List;


public class FilterEdgeActor extends AbstractBehavior<FilterEdgeActor.Command> {

    public interface Command {}

    public static class ProduceEdges implements Command {
        public final String vertexId;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProduceEdges(String vertexId) {
            this.vertexId = vertexId;
            this.propertyRouter = null;
        }
        public ProduceEdges(String vertexId, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.vertexId = vertexId;
            this.propertyRouter = propertyRouter;
        }
    }


    public static Behavior<Command> create(int poolIndex, int instanceIndex) {
        return Behaviors.setup(context -> new FilterEdgeActor(context, poolIndex, instanceIndex));
    }
    private final int poolIndex;
    private final int instanceIndex;


    private FilterEdgeActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterEdgeActor from pool {} instance {} is created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceEdges.class, this::onProduceEdges)
                .build();
    }

    private Behavior<Command> onProduceEdges(ProduceEdges command) {
        if (command.propertyRouter == null) {
            getContext().getLog().error("Property router not set for edge processing");
            return this;
        }

        List<String> edges = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            edges.add(command.vertexId + "_edge_" + i);
        }

        // Send each edge to property actors
        for (String edge : edges) {
            command.propertyRouter.tell(
                    new FilterProjectPropertyActor.ProduceProperties(command.vertexId, edge)
            );
        }

        getContext().getLog().info("Produced 1000 edges for vertex {}", command.vertexId);
        return this;
    }
}

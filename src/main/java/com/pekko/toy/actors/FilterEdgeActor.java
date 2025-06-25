package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;

public class FilterEdgeActor extends AbstractBehavior<FilterEdgeActor.Command> {

    public interface Command {}

    public static class ProduceEdges implements Command {
        public final String vertexId;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

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
        context.getLog().info("FilterEdgeActor from pool {} instance {} created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceEdges.class, this::onProduceEdges)
                .build();
    }

    private Behavior<Command> onProduceEdges(ProduceEdges command) {
        for (int i = 1; i <= 1000; i++) {
            String edgeId = command.vertexId + "_edge_" + i;
            command.propertyRouter.tell(new FilterProjectPropertyActor.ProduceProperties(command.vertexId, edgeId));
        }
        getContext().getLog().info("Produced 1000 edges for vertex {}", command.vertexId);
        return this;
    }
}

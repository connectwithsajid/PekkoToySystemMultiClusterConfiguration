package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;
import java.util.ArrayList;
import java.util.List;

public class FilterVertexActor extends AbstractBehavior<FilterVertexActor.Command> {

    public interface Command {}

    public static class ProduceVertices implements Command {
        public final ActorRef<FilterEdgeActor.Command> edgeRouter;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProduceVertices(ActorRef<FilterEdgeActor.Command> edgeRouter,
                               ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.edgeRouter = edgeRouter;
            this.propertyRouter = propertyRouter;
        }
    }

    public static Behavior<Command> create(int poolIndex, int instanceIndex) {
        return Behaviors.setup(context -> new FilterVertexActor(context, poolIndex, instanceIndex));
    }

    private final int poolIndex;
    private final int instanceIndex;

    private FilterVertexActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterVertexActor from pool {} instance {} created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceVertices.class, this::onProduceVertices)
                .build();
    }

    private Behavior<Command> onProduceVertices(ProduceVertices command) {
        List<String> vertices = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            vertices.add("vertex_" + i);
        }

        // Send each vertex to edge actors
        for (String vertex : vertices) {
            command.edgeRouter.tell(new FilterEdgeActor.ProduceEdges(vertex, command.propertyRouter));
        }

        getContext().getLog().info("Produced 100 vertices");
        return this;
    }
}

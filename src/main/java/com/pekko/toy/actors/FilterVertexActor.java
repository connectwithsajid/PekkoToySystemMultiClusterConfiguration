package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
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
        context.getLog().info("FilterVertexActor from pool {} instance {} is created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceVertices.class, this::onProduceVertices)
                .build();    }

    private Behavior<Command> onProduceVertices(ProduceVertices command) {
        for (int i = 1; i <= 100; i++) {
            String vertex = "vertex_" + i;
            // Fixed: Use vertex variable and correct propertyRouter reference
            command.edgeRouter.tell(new FilterEdgeActor.ProduceEdges(vertex, command.propertyRouter));
        }
        getContext().getLog().info("Produced 100 vertices");
        return this;
    }

}

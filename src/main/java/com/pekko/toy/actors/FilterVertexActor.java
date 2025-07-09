package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;
import com.pekko.toy.splitlib.Split;
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

    // Batch message for EdgeActor
    public static class ProcessVertexBatch implements FilterEdgeActor.Command {
        public final List<String> vertices;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProcessVertexBatch(List<String> vertices, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.vertices = vertices;
            this.propertyRouter = propertyRouter;
        }
    }

    private final int poolIndex;
    private final int instanceIndex;
    private final int vertexChunkSize = 10; // Or make configurable

    public static Behavior<Command> create(int poolIndex, int instanceIndex) {
        return Behaviors.setup(context -> new FilterVertexActor(context, poolIndex, instanceIndex));
    }

    private FilterVertexActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterVertexActor created at path {}", context.getSelf().path());

//        context.getLog().info("FilterVertexActor from pool {} instance {} created", poolIndex, instanceIndex);

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

        Split<String> split = new Split<>(vertexChunkSize, batch -> {
            command.edgeRouter.tell(new FilterEdgeActor.ProcessVertexBatch(batch, command.propertyRouter));
        });

        for (String vertex : vertices) {
            split.send(vertex);
        }
        split.close();

        getContext().getLog().info("Produced {} vertices in batches of {}", vertices.size(), vertexChunkSize);
        return this;
    }
}

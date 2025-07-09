package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;
import com.pekko.toy.splitlib.Split;
import java.util.List;

public class FilterEdgeActor extends AbstractBehavior<FilterEdgeActor.Command> {

    public interface Command {}

    // Batch message from VertexActor
    public static class ProcessVertexBatch implements Command {
        public final List<String> vertices;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProcessVertexBatch(List<String> vertices, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.vertices = vertices;
            this.propertyRouter = propertyRouter;
        }
    }

    // Batch message for PropertyActor
    public static class ProcessEdgeBatch implements FilterProjectPropertyActor.Command {
        public final List<String> edges;

        public ProcessEdgeBatch(List<String> edges) {
            this.edges = edges;
        }
    }

    private final int poolIndex;
    private final int instanceIndex;
    private final int edgeChunkSize = 10; // Or make configurable

    public static Behavior<Command> create(int poolIndex, int instanceIndex) {
        return Behaviors.setup(context -> new FilterEdgeActor(context, poolIndex, instanceIndex));
    }

    private FilterEdgeActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterEdgeActor created at path {}", context.getSelf().path());

//        context.getLog().info("FilterEdgeActor from pool {} instance {} created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessVertexBatch.class, this::onProcessVertexBatch)
                .build();
    }

    private Behavior<Command> onProcessVertexBatch(ProcessVertexBatch command) {
        Split<String> split = new Split<>(edgeChunkSize, batch -> {
            command.propertyRouter.tell(new FilterProjectPropertyActor.ProcessEdgeBatch(batch));
        });

        for (String vertex : command.vertices) {
            for (int i = 1; i <= 1000; i++) {
                String edgeId = vertex + "_edge_" + i;
                split.send(edgeId);
            }
        }
        split.close();

        getContext().getLog().info("Processed vertex batch into edge batches of {}", edgeChunkSize);
        return this;
    }
}

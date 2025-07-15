package com.pekko.toy.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.pekko.toy.splitlib.Split;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.*;

public class FilterVertexActor extends AbstractBehavior<FilterVertexActor.Command> {

    public interface Command {}

    public static class ProduceVertices implements Command {
        public final ObjectNode networkPacket;
        public final ActorRef<FilterEdgeActor.Command> edgeRouter;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProduceVertices(ObjectNode packet, ActorRef<FilterEdgeActor.Command> edgeRouter,
                               ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.networkPacket = packet;
            this.edgeRouter = edgeRouter;
            this.propertyRouter = propertyRouter;
        }
    }

    public static Behavior<Command> create(int chunkSize) {
        return Behaviors.setup(ctx -> new FilterVertexActor(ctx, chunkSize));
    }

    private final int vertexChunkSize;

    public FilterVertexActor(ActorContext<Command> ctx, int chunkSize) {
        super(ctx);
        this.vertexChunkSize = chunkSize;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceVertices.class, this::onProduceVertices)
                .build();
    }

    private Behavior<Command> onProduceVertices(ProduceVertices cmd) {
        ObjectMapper mapper = new ObjectMapper();

        Split split = new Split();
        split.initialize(
                cmd.networkPacket,
                vertexChunkSize,
                "EdgeActor",
                batch -> cmd.edgeRouter.tell(new FilterEdgeActor.ProcessVertexBatch(batch, cmd.propertyRouter))
        );

        for (int i = 1; i <= 17; i++) {
            ObjectNode vertex = mapper.createObjectNode()
                    .put("Vid", i)
                    .put("Count", i * 100)
                    .put("Name", "Vertex_" + i);
            split.send(vertex);
        }

        split.close();
        return this;
    }
}

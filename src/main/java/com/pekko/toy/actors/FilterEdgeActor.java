package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;
import com.pekko.toy.splitlib.Split;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class FilterEdgeActor extends AbstractBehavior<FilterEdgeActor.Command> {

    public interface Command {}

    public static class ProcessVertexBatch implements Command {
        public final ObjectNode packet;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProcessVertexBatch(ObjectNode packet, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.packet = packet;
            this.propertyRouter = propertyRouter;
        }
    }

    public static class ProcessEdgeBatch implements FilterProjectPropertyActor.Command {
        public final ObjectNode packet;

        public ProcessEdgeBatch(ObjectNode packet) {
            this.packet = packet;
        }
    }

    private final int edgeChunkSize;

    public static Behavior<Command> create(int edgeChunkSize) {
        return Behaviors.setup(context -> new FilterEdgeActor(context, edgeChunkSize));
    }

    private FilterEdgeActor(ActorContext<Command> context, int edgeChunkSize) {
        super(context);
        this.edgeChunkSize = edgeChunkSize;
        context.getLog().info("FilterEdgeActor created at path {}", context.getSelf().path());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessVertexBatch.class, this::onProcessVertexBatch)
                .build();
    }

    private Behavior<Command> onProcessVertexBatch(ProcessVertexBatch command) {
        ObjectNode packet = command.packet;
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode vertices = (ArrayNode) packet.get("data");
        ObjectNode metadata = packet.get("metadata").deepCopy();
        metadata.put("packetType", "edge");

        ObjectNode networkPacket = mapper.createObjectNode();
        networkPacket.set("metadata", metadata);
        networkPacket.set("data", mapper.createArrayNode());

        Split split = new Split();
        split.initialize(networkPacket, edgeChunkSize, batchPacket -> {
            command.propertyRouter.tell(new FilterProjectPropertyActor.ProcessEdgeBatch(batchPacket));
        });

        for (JsonNode vertex : vertices) {
            long vid = vertex.get("Vid").asLong();
            for (int i = 1; i <= 6; i++) {
                ObjectNode edgeObj = mapper.createObjectNode()
                        .put("EdgeId", vid + "_edge_" + i);
                split.send(edgeObj);
            }
        }
        split.close();

        getContext().getLog().info("Processed vertex batch into edge batches of {}", edgeChunkSize);
        return this;
    }
}

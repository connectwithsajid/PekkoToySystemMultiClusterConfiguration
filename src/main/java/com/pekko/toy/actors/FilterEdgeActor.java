package com.pekko.toy.actors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.pekko.toy.splitlib.Split;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.*;

public class FilterEdgeActor extends AbstractBehavior<FilterEdgeActor.Command> {
    private int count_edges =  5;
    public interface Command {}
    private String[] next_operators =   {"PropertyActor0","PropertyActor1","PropertyActor2"};
    private String next_operators_policy = "RoundRobinRoutingPolicy";

    public static class ProcessVertexBatch implements Command {
        public final ObjectNode packet;

        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProcessVertexBatch(ObjectNode packet, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.packet = packet;
            this.propertyRouter = propertyRouter;
        }
    }

    public static Behavior<Command> create(int chunkSize) {
        return Behaviors.setup(ctx -> new FilterEdgeActor(ctx, chunkSize));
    }

    private final int edgeChunkSize;

    public FilterEdgeActor(ActorContext<Command> ctx, int chunkSize) {
        super(ctx);
        this.edgeChunkSize = chunkSize;
        ctx.getLog().info("FilterVertexActor created at path {}", ctx.getSelf().path());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessVertexBatch.class, this::onVertexBatch)
                .build();
    }

    private Behavior<Command> onVertexBatch(ProcessVertexBatch msg) {
        ObjectNode networkPacket = msg.packet;
        ObjectMapper mapper = new ObjectMapper();

        ArrayNode vertices = (ArrayNode) networkPacket.get("data");

        Split split = new Split();
        split.initialize(
                networkPacket,
                edgeChunkSize,
                next_operators,
                next_operators_policy,
                batch -> {
            msg.propertyRouter.tell(new FilterProjectPropertyActor.ProcessEdgeBatch(batch));
        });

        for (JsonNode vertex : vertices) {
            long vid = vertex.get("Vid").asLong();
            for (int i = 1; i <= count_edges; i++) {
                ObjectNode edge = mapper.createObjectNode().put("EdgeId", "E_" + vid + "_" + i);
                split.send(edge);
            }
        }

        split.close();
        return this;
    }
}

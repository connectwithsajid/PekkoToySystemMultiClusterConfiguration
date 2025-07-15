package com.pekko.toy.actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pekko.toy.splitlib.Split;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.util.concurrent.atomic.AtomicInteger;

public class FilterProjectPropertyActor extends AbstractBehavior<FilterProjectPropertyActor.Command> {

    public interface Command {}

    public static class ProcessEdgeBatch implements Command {
        public final ObjectNode packet;
        public ProcessEdgeBatch(ObjectNode packet) {
            this.packet = packet;
        }
    }

    private final int chunkSize;
    private final AtomicInteger totalCount;
    private final String[] names = {"john", "mary"};

    public static Behavior<Command> create(int chunkSize, AtomicInteger totalCount) {
        return Behaviors.setup(ctx -> new FilterProjectPropertyActor(ctx, chunkSize, totalCount));
    }

    private FilterProjectPropertyActor(ActorContext<Command> ctx, int chunkSize, AtomicInteger totalCount) {
        super(ctx);
        this.chunkSize = chunkSize;
        this.totalCount = totalCount;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessEdgeBatch.class, this::onEdgeBatch)
                .build();
    }

    private Behavior<Command> onEdgeBatch(ProcessEdgeBatch msg) {
        ObjectNode packet = msg.packet;
        ArrayNode edges = (ArrayNode) packet.get("data");
        ObjectMapper mapper = new ObjectMapper();

        Split split = new Split();
        split.initialize(packet, chunkSize, "Terminal", batch -> {
            ArrayNode data = (ArrayNode) batch.get("data");
            int count = data.size();
            totalCount.addAndGet(count);
            getContext().getLog().info("⛳ Final batch sent with {} properties. Total now: {}", count, totalCount.get());
        });

        for (JsonNode edge : edges) {
            String edgeId = edge.get("EdgeId").asText();
            for (String name : names) {
                ObjectNode prop = mapper.createObjectNode().put("PropId", edgeId + "_" + name);
                split.send(prop);
            }
        }

        split.close();
        return this;
    }
}

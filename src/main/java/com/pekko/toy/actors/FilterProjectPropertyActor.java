package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.splitlib.Split;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicInteger;

public class FilterProjectPropertyActor extends AbstractBehavior<FilterProjectPropertyActor.Command> {

    public interface Command {}

    public static class ProcessEdgeBatch implements Command {
        public final ObjectNode packet;

        public ProcessEdgeBatch(ObjectNode packet) {
            this.packet = packet;
        }
    }

    private int instanceCount = 0;
    private final AtomicInteger totalCount;
    private static final String[] names = {"john", "mary"};
//    private static final String[] names = {"john", "mary", "alice", "bob", "charlie",
//            "diana", "edward", "fiona", "george", "kathy"};
    private final int propertyChunkSize;

    public static Behavior<Command> create(int propertyChunkSize, AtomicInteger totalCount) {
        return Behaviors.setup(context -> new FilterProjectPropertyActor(context, propertyChunkSize, totalCount));
    }

    private FilterProjectPropertyActor(ActorContext<Command> context, int propertyChunkSize, AtomicInteger totalCount) {
        super(context);
        this.totalCount = totalCount;
        this.propertyChunkSize = propertyChunkSize;
        context.getLog().info("FilterProjectPropertyActor created at path {}", context.getSelf().path());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessEdgeBatch.class, this::onProcessEdgeBatch)
                .build();
    }

    private Behavior<Command> onProcessEdgeBatch(ProcessEdgeBatch command) {
        ObjectNode packet = command.packet;
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode edges = (ArrayNode) packet.get("data");
        ObjectNode metadata = packet.get("metadata").deepCopy();
        metadata.put("packetType", "property");

        ObjectNode networkPacket = mapper.createObjectNode();
        networkPacket.set("metadata", metadata);
        networkPacket.set("data", mapper.createArrayNode());

        Split split = new Split();
        split.initialize(networkPacket, propertyChunkSize, batchPacket -> {
            ArrayNode batchData = (ArrayNode) batchPacket.get("data");
            int batchSize = batchData.size();
            instanceCount += batchSize;
            int currentTotal = totalCount.addAndGet(batchSize);

            getContext().getLog().info(
                    "{} created {} properties (batch size). Total system output: {}",
                    getContext().getSelf().path(), batchSize, currentTotal
            );
        });

        for (JsonNode edge : edges) {
            String edgeId = edge.get("EdgeId").asText();
            for (String name : names) {
                ObjectNode propertyObj = mapper.createObjectNode().put("PropertyId", edgeId + "_" + name);
                split.send(propertyObj);
            }
        }
        split.close();

        getContext().getLog().info(
                "{} finished processing edge batch. Instance has produced {} properties so far.",
                getContext().getSelf().path(), instanceCount
        );
        return this;
    }
}

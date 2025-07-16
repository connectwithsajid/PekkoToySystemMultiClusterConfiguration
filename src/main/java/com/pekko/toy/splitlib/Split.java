package com.pekko.toy.splitlib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;

public class Split {
    private int batchSize;
    private int numEltsInPkt;
    private String[] nextOperators;
    private String next_operators_policy;
    private ObjectNode templateMetadata;
    private ObjectNode outgoingPkt;
    private ArrayNode dataArray;
    private final ObjectMapper mapper = new ObjectMapper();
    private java.util.function.Consumer<ObjectNode> batchConsumer;

    public void initialize(
            ObjectNode networkPacket,
            int batchSize,
            String[]  nextOperators,
            String next_operators_policy,
            java.util.function.Consumer<ObjectNode> batchConsumer
    ) {
        this.batchSize = batchSize;
        this.nextOperators = nextOperators;
        this.next_operators_policy = next_operators_policy;
        this.numEltsInPkt = 0;
        this.batchConsumer = batchConsumer;

        JsonNode metadataNode = networkPacket.get("metadata");

        if (metadataNode == null || !metadataNode.isObject()) {
            throw new IllegalArgumentException("networkPacket must have a 'metadata' ObjectNode");
        }

        this.templateMetadata = (ObjectNode) metadataNode.deepCopy();

        // Create first outgoing packet
        this.dataArray = mapper.createArrayNode();
        this.outgoingPkt = createOutgoingPacket();
    }

    public void send(JsonNode streamElt) {
        dataArray.add(streamElt);
        numEltsInPkt++;

        if (numEltsInPkt == batchSize) {
            submitBatch();
            prepareNextPacket();
        }
    }

    public void close() {
        if (numEltsInPkt > 0) {
            submitBatch();
        }
    }

    private void submitBatch() {


        ObjectNode batchToSend = outgoingPkt;
                //.deepCopy();

        // Set or update next operator ID
        ObjectNode metadataToUse = (ObjectNode) batchToSend.get("metadata");
//        metadataToUse.put("nextOperator", Arrays.toString(this.nextOperators));
        ArrayNode opsArray = metadataToUse.arrayNode();
        for (String op : nextOperators) {
            opsArray.add(op);
        }

        metadataToUse.set("nextOperator", opsArray);
        metadataToUse.put("nextOperatorPolicy", next_operators_policy);

        try {
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(batchToSend);
            System.out.println("[Split.submitBatch] Sending batch:\n" + prettyJson);
        } catch (Exception e) {
            System.err.println("Failed to convert batch to JSON string: " + e.getMessage());
        }

        batchConsumer.accept(batchToSend);

    }

    private void prepareNextPacket() {
        this.dataArray = mapper.createArrayNode();
        this.outgoingPkt = createOutgoingPacket();
        this.numEltsInPkt = 0;
    }

    private ObjectNode createOutgoingPacket() {
        ObjectNode newPacket = mapper.createObjectNode();

        newPacket.set("metadata", templateMetadata);
        //.deepCopy());
        newPacket.set("data", dataArray);
        return newPacket;
    }
}

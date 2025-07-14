package com.pekko.toy.splitlib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Split {
    private int batchSize;
    private int numEltsInPkt;
    private ObjectNode templateMetadata;
    private ObjectNode outgoingPkt;
    private ArrayNode dataArray;
    private final ObjectMapper mapper = new ObjectMapper();
    private java.util.function.Consumer<ObjectNode> batchConsumer;

    public void initialize(ObjectNode networkPacket, int batchSize, java.util.function.Consumer<ObjectNode> batchConsumer) {
        this.batchSize = batchSize;
        this.numEltsInPkt = 0;
        this.batchConsumer = batchConsumer;

        JsonNode metadataNode = networkPacket.get("metadata");
        System.out.println("Metadata: " + metadataNode);

        if (metadataNode == null || !metadataNode.isObject()) {
            throw new IllegalArgumentException("networkPacket must have a 'metadata' ObjectNode");
        }

        this.templateMetadata = (ObjectNode) metadataNode.deepCopy();

        this.dataArray = mapper.createArrayNode();
        this.outgoingPkt = mapper.createObjectNode();
        this.outgoingPkt.set("metadata", templateMetadata.deepCopy());
        this.outgoingPkt.set("data", dataArray);



    }

    public void send(JsonNode streamElt) {
        dataArray.add(streamElt);
        numEltsInPkt++;
        if (numEltsInPkt == batchSize) {
//            batchConsumer.accept(outgoingPkt);
            batchConsumer.accept(outgoingPkt.deepCopy());
            System.out.println("outgoingPkt" + outgoingPkt.deepCopy());
            this.dataArray = mapper.createArrayNode();
            this.outgoingPkt = mapper.createObjectNode();
            this.outgoingPkt.set("metadata", templateMetadata.deepCopy());
            this.outgoingPkt.set("data", dataArray);
            numEltsInPkt = 0;
        }
    }

    public void close() {
        if (numEltsInPkt > 0) {
            batchConsumer.accept(outgoingPkt.deepCopy());
            System.out.println("outgoingPkt" + outgoingPkt.deepCopy());
        }
    }
}

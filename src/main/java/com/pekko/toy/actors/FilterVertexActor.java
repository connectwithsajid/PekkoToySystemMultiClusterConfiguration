package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.ActorRef;
import com.pekko.toy.splitlib.Split;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FilterVertexActor extends AbstractBehavior<FilterVertexActor.Command> {

    public interface Command {}

    public static class ProduceVertices implements Command {
        public final ObjectNode packet;
        public final ActorRef<FilterEdgeActor.Command> edgeRouter;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProduceVertices(ObjectNode packet,
                               ActorRef<FilterEdgeActor.Command> edgeRouter,
                               ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.packet = packet;
            this.edgeRouter = edgeRouter;
            this.propertyRouter = propertyRouter;
        }
    }

    public static class ProcessVertexBatch implements FilterEdgeActor.Command {
        public final ObjectNode packet;
        public final ActorRef<FilterProjectPropertyActor.Command> propertyRouter;

        public ProcessVertexBatch(ObjectNode packet, ActorRef<FilterProjectPropertyActor.Command> propertyRouter) {
            this.packet = packet;
            this.propertyRouter = propertyRouter;
        }
    }

    private final int vertexChunkSize;

    public static Behavior<Command> create(int vertexChunkSize) {
        return Behaviors.setup(context -> new FilterVertexActor(context, vertexChunkSize));
    }

    private FilterVertexActor(ActorContext<Command> context, int vertexChunkSize) {
        super(context);
        this.vertexChunkSize = vertexChunkSize;
        context.getLog().info("FilterVertexActor created at path {}", context.getSelf().path());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceVertices.class, this::onProduceVertices)
                .build();
    }

    private Behavior<Command> onProduceVertices(ProduceVertices command) {
        ObjectNode inputPacket = command.packet;
        ObjectMapper mapper = new ObjectMapper();

        Split split = new Split();
        split.initialize(inputPacket, vertexChunkSize, batchPacket -> {
            command.edgeRouter.tell(new FilterEdgeActor.ProcessVertexBatch(batchPacket, command.propertyRouter));
        });

        for (int i = 1; i <= 7; i++) {
            ObjectNode vertexObj = mapper.createObjectNode()
                    .put("Vid", i);
//                    .put("Count", i * 2)
//                    .put("Name", "Name_" + i);
            split.send(vertexObj);
        }
        split.close();

        getContext().getLog().info("Produced 100 vertices in batches of {}", vertexChunkSize);
        return this;
    }
}

package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import java.util.*;

public class FilterProjectPropertyActor extends AbstractBehavior<FilterProjectPropertyActor.Command> {

    public interface Command {}

    public static class ProduceProperties implements Command {
        public final String vertexId;
        public final String edgeId;

        public ProduceProperties(String vertexId, String edgeId) {
            this.vertexId = vertexId;
            this.edgeId = edgeId;
        }
    }

    public static Behavior<Command> create(int poolIndex, int instanceIndex) {
        return Behaviors.setup(context -> new FilterProjectPropertyActor(context, poolIndex, instanceIndex));
    }

    private final int poolIndex;
    private final int instanceIndex;

    private FilterProjectPropertyActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterProjectPropertyActor from pool {} instance {} created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProduceProperties.class, this::onProduceProperties)
                .build();
    }

    private Behavior<Command> onProduceProperties(ProduceProperties command) {
        List<String> properties = new ArrayList<>();
        String[] names = {"john", "mary", "alice", "bob", "charlie",
                "diana", "edward", "fiona", "george", "kathy"};
        for (String name : names) {
            properties.add(command.vertexId + "_" + command.edgeId + "_" + name);
        }
        getContext().getLog().info(
                "ProjectPropertyActor from pool {} instance {} produced 10 projected properties for vertex {} edge {}: {}",
                poolIndex, instanceIndex, command.vertexId, command.edgeId, properties
        );
        return this;
    }
}

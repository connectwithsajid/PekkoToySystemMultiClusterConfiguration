package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import java.util.ArrayList;
import java.util.List;


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
        return Behaviors.setup(context -> new FilterProjectPropertyActor(context, poolIndex, instanceIndex));    }

    private final int poolIndex;
    private final int instanceIndex;
    private int producedCount = 0;

    private FilterProjectPropertyActor(ActorContext<Command> context, int poolIndex, int instanceIndex) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        context.getLog().info("FilterProjectPropertyActor from pool {} instance {} is created", poolIndex, instanceIndex);    }

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

        producedCount += properties.size(); // <--- Increment this instance's count


        getContext().getLog().info(
                "ProjectPropertyActor from pool {} instance {} produced 10 projected properties for vertex {} edge {}: {}",
                poolIndex, instanceIndex, command.vertexId, command.edgeId, properties
        );
// Log running total for this instance
        getContext().getLog().info(
                "PROGRESS: ProjectPropertyActor from pool {} instance {} has produced {} properties so far.",
                poolIndex, instanceIndex, producedCount
        );



        return this;
    }
}

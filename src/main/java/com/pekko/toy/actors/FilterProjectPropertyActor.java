package com.pekko.toy.actors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import com.pekko.toy.splitlib.Split;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class FilterProjectPropertyActor extends AbstractBehavior<FilterProjectPropertyActor.Command> {

    public interface Command {}

    // Batch message from EdgeActor
    public static class ProcessEdgeBatch implements Command {
        public final List<String> edges;

        public ProcessEdgeBatch(List<String> edges) {
            this.edges = edges;
        }
    }

    private final int poolIndex;
    private final int instanceIndex;
    private int instanceCount = 0;
    private final AtomicInteger totalCount;
    private static final String[] names = {"john", "mary", "alice", "bob", "charlie",
            "diana", "edward", "fiona", "george", "kathy"};

    private final int propertyChunkSize = 10000; // You can make this configurable

    public static Behavior<Command> create(int poolIndex, int instanceIndex, AtomicInteger totalCount) {
        return Behaviors.setup(context -> new FilterProjectPropertyActor(context, poolIndex, instanceIndex, totalCount));
    }

    private FilterProjectPropertyActor(ActorContext<Command> context, int poolIndex, int instanceIndex, AtomicInteger totalCount) {
        super(context);
        this.poolIndex = poolIndex;
        this.instanceIndex = instanceIndex;
        this.totalCount = totalCount;
        context.getLog().info("FilterProjectPropertyActor created at path {}", context.getSelf().path());
//        context.getLog().info("FilterProjectPropertyActor from pool {} instance {} created", poolIndex, instanceIndex);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessEdgeBatch.class, this::onProcessEdgeBatch)
                .build();
    }

    private Behavior<Command> onProcessEdgeBatch(ProcessEdgeBatch command) {
        Split<String> split = new Split<>(propertyChunkSize, batch -> {
            instanceCount += batch.size();
            int currentTotal = totalCount.addAndGet(batch.size());

            getContext().getLog().info(
                    "{} created {} properties (batch size). Total system output: {}",
                    getContext().getSelf().path(), batch.size(), currentTotal
            );

        });

        for (String edge : command.edges) {
            for (String name : names) {
                String property = edge + "_" + name;
                split.send(property);
            }
        }
        split.close();
        getContext().getLog().info(
                "{} finished processing edge batch. Instance has produced {} properties so far.",
                getContext().getSelf().path(), instanceCount
        );
        return this;
    }


//    private Behavior<Command> onProcessEdgeBatch(ProcessEdgeBatch command) {
//        for (String edge : command.edges) {
//            for (String name : names) {
//                String property = edge + "_" + name;
//                propertyBuffer.add(property);
//                bufferedCount++;
//
//                if (propertyBuffer.size() == propertyChunkSize) {
//                    flushPropertyBuffer();
//                }
//            }
//        }
//        // Optionally: flush remaining properties at shutdown or after all work is done
//        return this;
//    }

}

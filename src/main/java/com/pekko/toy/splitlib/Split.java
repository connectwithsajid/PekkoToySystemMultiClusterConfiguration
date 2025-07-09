package com.pekko.toy.splitlib;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Split<T> {
    private final int chunkSize;
    private final Consumer<List<T>> batchConsumer;
    private final List<T> buffer;

    public Split(int chunkSize, Consumer<List<T>> batchConsumer) {
        this.chunkSize = chunkSize;
        this.batchConsumer = batchConsumer;
        this.buffer = new ArrayList<>(chunkSize);
    }

    public void send(T item) {
        buffer.add(item);
        if (buffer.size() == chunkSize) {
            flush();
        }
    }

    public void close() {
        if (!buffer.isEmpty()) {
            flush();
        }
    }

    private void flush() {
        batchConsumer.accept(new ArrayList<>(buffer));
        buffer.clear();
    }
}

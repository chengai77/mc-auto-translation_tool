package org.universaltranslator.core;

import java.util.ArrayDeque;

/** 异步结果顺序展示队列 */
public final class OrderedDisplayQueue<T> {
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<Entry<T>>();
    private final int maximumEntries;
    private final int holdTicks;
    private int remainingTicks;

    public OrderedDisplayQueue(int maximumEntries, int holdTicks) {
        if (maximumEntries < 1 || holdTicks < 0) {
            throw new IllegalArgumentException("Invalid display queue limits");
        }
        this.maximumEntries = maximumEntries;
        this.holdTicks = holdTicks;
    }

    public synchronized Ticket<T> offer() {
        if (entries.size() >= maximumEntries) {
            return null;
        }
        Entry<T> entry = new Entry<T>();
        entries.addLast(entry);
        return new Ticket<T>(this, entry);
    }

    public synchronized T tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
            return null;
        }
        Entry<T> entry = entries.peekFirst();
        if (entry == null || !entry.ready) {
            return null;
        }
        entries.removeFirst();
        if (entry.value == null) {
            return null;
        }
        remainingTicks = holdTicks;
        return entry.value;
    }

    public synchronized void clear() {
        entries.clear();
        remainingTicks = 0;
    }

    public synchronized int size() {
        return entries.size();
    }

    private synchronized void complete(Entry<T> entry, T value) {
        if (entries.contains(entry)) {
            entry.value = value;
            entry.ready = true;
        }
    }

    public static final class Ticket<T> {
        private final OrderedDisplayQueue<T> owner;
        private final Entry<T> entry;

        private Ticket(OrderedDisplayQueue<T> owner, Entry<T> entry) {
            this.owner = owner;
            this.entry = entry;
        }

        public void complete(T value) {
            owner.complete(entry, value);
        }
    }

    private static final class Entry<T> {
        private T value;
        private boolean ready;
    }
}

package org.universaltranslator.core;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** 重复显示去重 */
public final class RepeatingDisplayCache<T> {
    private final ConcurrentHashMap<String, Boolean> pending =
            new ConcurrentHashMap<String, Boolean>();
    private final ConcurrentHashMap<String, Entry<T>> completed =
            new ConcurrentHashMap<String, Entry<T>>();
    private final ArrayDeque<String> completedOrder = new ArrayDeque<String>();
    private final int maximumEntries;

    public RepeatingDisplayCache(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("Invalid repeat cache limit");
        }
        this.maximumEntries = maximumEntries;
    }

    public synchronized Claim<T> acquire(String key) {
        Entry<T> entry = completed.get(key);
        if (entry != null) {
            return entry.displayed
                    ? Claim.displayed(entry.value) : Claim.suppressed();
        }
        if (pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return Claim.suppressed();
        }
        return Claim.claimed();
    }

    public synchronized void complete(String key, T value) {
        pending.remove(key);
        if (value == null) {
            return;
        }
        Entry<T> previous = completed.put(key, new Entry<T>(value));
        if (previous == null) {
            completedOrder.addLast(key);
        }
        trim();
    }

    public synchronized void fail(String key) {
        pending.remove(key);
    }

    public synchronized void markDisplayed(String key) {
        Entry<T> entry = completed.get(key);
        if (entry != null) {
            entry.displayed = true;
        }
    }

    public synchronized void clear() {
        pending.clear();
        completed.clear();
        completedOrder.clear();
    }

    private void trim() {
        while (completed.size() > maximumEntries) {
            String eldest = completedOrder.pollFirst();
            if (eldest == null) {
                return;
            }
            completed.remove(eldest);
        }
    }

    public static final class Claim<T> {
        private final boolean claimed;
        private final T displayed;

        private Claim(boolean claimed, T displayed) {
            this.claimed = claimed;
            this.displayed = displayed;
        }

        public boolean isClaimed() {
            return claimed;
        }

        public T displayedValue() {
            return displayed;
        }

        private static <T> Claim<T> claimed() {
            return new Claim<T>(true, null);
        }

        private static <T> Claim<T> suppressed() {
            return new Claim<T>(false, null);
        }

        private static <T> Claim<T> displayed(T value) {
            return new Claim<T>(false, value);
        }
    }

    private static final class Entry<T> {
        private final T value;
        private volatile boolean displayed;

        private Entry(T value) {
            this.value = value;
        }
    }
}

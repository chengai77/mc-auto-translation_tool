package org.universaltranslator.core;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 合并主线程刷新 */
public final class CoalescingUpdateQueue {
    private final Executor executor;
    private final Runnable update;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public CoalescingUpdateQueue(Executor executor, Runnable update) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.update = Objects.requireNonNull(update, "update");
    }

    public void request() {
        revision.incrementAndGet();
        schedule();
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    runUpdate();
                }
            });
        } catch (RuntimeException failure) {
            scheduled.set(false);
            throw failure;
        }
    }

    private void runUpdate() {
        long observed = revision.get();
        try {
            update.run();
        } finally {
            scheduled.set(false);
        }
        if (revision.get() != observed) {
            schedule();
        }
    }
}

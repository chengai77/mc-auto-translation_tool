package org.universaltranslator.core;

import java.util.concurrent.atomic.AtomicInteger;

/** 前台渲染额度 */
final class PendingTranslationBudget {
    private final int maximumBackground;
    private final int maximumForeground;
    private final int maximumChat;
    private final int maximumSystemMessage;
    private final AtomicInteger background = new AtomicInteger();
    private final AtomicInteger foreground = new AtomicInteger();
    private final AtomicInteger chat = new AtomicInteger();
    private final AtomicInteger systemMessage = new AtomicInteger();

    PendingTranslationBudget(int maximumBackground, int maximumForeground) {
        this(maximumBackground, maximumForeground, maximumForeground, maximumForeground);
    }

    PendingTranslationBudget(int maximumBackground, int maximumForeground, int maximumChat) {
        this(maximumBackground, maximumForeground, maximumChat, maximumForeground);
    }

    PendingTranslationBudget(
            int maximumBackground,
            int maximumForeground,
            int maximumChat,
            int maximumSystemMessage
    ) {
        if (maximumBackground < 1 || maximumForeground < 1
                || maximumChat < 1 || maximumSystemMessage < 1) {
            throw new IllegalArgumentException("Pending limits must be positive");
        }
        this.maximumBackground = maximumBackground;
        this.maximumForeground = maximumForeground;
        this.maximumChat = maximumChat;
        this.maximumSystemMessage = maximumSystemMessage;
    }

    boolean tryAcquire(boolean foregroundWork) {
        AtomicInteger counter = foregroundWork ? foreground : background;
        int maximum = foregroundWork ? maximumForeground : maximumBackground;
        return tryAcquire(counter, maximum);
    }

    boolean tryAcquireChat() {
        return tryAcquire(chat, maximumChat);
    }

    boolean tryAcquireSystemMessage() {
        return tryAcquire(systemMessage, maximumSystemMessage);
    }

    private static boolean tryAcquire(AtomicInteger counter, int maximum) {
        while (true) {
            int current = counter.get();
            if (current >= maximum) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    void release(boolean foregroundWork) {
        AtomicInteger counter = foregroundWork ? foreground : background;
        release(counter);
    }

    void releaseChat() {
        release(chat);
    }

    void releaseSystemMessage() {
        release(systemMessage);
    }

    private static void release(AtomicInteger counter) {
        while (true) {
            int current = counter.get();
            if (current <= 0 || counter.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    int backgroundCount() {
        return background.get();
    }

    int foregroundCount() {
        return foreground.get();
    }

    int chatCount() {
        return chat.get();
    }

    int systemMessageCount() {
        return systemMessage.get();
    }

    void reset() {
        background.set(0);
        foreground.set(0);
        chat.set(0);
        systemMessage.set(0);
    }
}

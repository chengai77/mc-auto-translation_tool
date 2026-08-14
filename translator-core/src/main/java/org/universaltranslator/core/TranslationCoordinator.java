package org.universaltranslator.core;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;

/**
 * Coordinates protection, caching, in-flight de-duplication and background work.
 * It never blocks a Minecraft render thread.
 */
public final class TranslationCoordinator implements AutoCloseable {
    private static final int MAX_QUEUED_TRANSLATIONS = 128;
    private static final int MAX_QUEUED_URGENT_TRANSLATIONS = 64;
    private static final String CACHE_FORMAT_VERSION = "translation-v6-" + GameTranslationHints.VERSION;

    private final TranslationProvider provider;
    private final TranslationStore cache;
    private final ThreadPoolExecutor executor;
    private final ThreadPoolExecutor urgentExecutor;
    private final boolean dedicatedUrgentExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<TranslationResult>> inFlight =
            new ConcurrentHashMap<String, CompletableFuture<TranslationResult>>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger submissionSequence = new AtomicInteger();
    private final RecentTranslationContext recentContext = new RecentTranslationContext();

    public TranslationCoordinator(TranslationProvider provider, TranslationStore cache, int workerCount) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.cache = Objects.requireNonNull(cache, "cache");
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<Runnable>(),
                new TranslationThreadFactory("universal-translator-"),
                new ThreadPoolExecutor.AbortPolicy());
        if (workerCount > 1) {
            int urgentWorkers = Math.min(2, workerCount);
            this.urgentExecutor = new ThreadPoolExecutor(
                    urgentWorkers,
                    urgentWorkers,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<Runnable>(),
                    new TranslationThreadFactory("universal-translator-urgent-"),
                    new ThreadPoolExecutor.AbortPolicy());
            this.dedicatedUrgentExecutor = true;
        } else {
            this.urgentExecutor = this.executor;
            this.dedicatedUrgentExecutor = false;
        }
    }

    public CompletableFuture<TranslationResult> translate(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind
    ) {
        return translate(text, sourceLanguage, targetLanguage, kind,
                Collections.<String>emptyList(), true);
    }

    public CompletableFuture<TranslationResult> translate(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind,
            final Iterable<String> protectedLiterals
    ) {
        return translate(text, sourceLanguage, targetLanguage, kind, protectedLiterals, true);
    }

    public CompletableFuture<TranslationResult> translate(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind,
            final Iterable<String> protectedLiterals,
            final boolean preserveHanText
    ) {
        Objects.requireNonNull(text, "text");
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("targetLanguage is required");
        }
        if (closed.get()) {
            return CompletableFuture.completedFuture(
                    TranslationResult.failure(text, "Translation session is closed"));
        }
        if (!LanguageHeuristics.shouldTranslate(text, targetLanguage)) {
            return CompletableFuture.completedFuture(TranslationResult.unchanged(text));
        }

        // Keep all regex construction, cache I/O and provider work off the render thread.
        // The raw request key is deliberately cheap to construct and still de-duplicates
        // the same text while a translation is in progress.
        final String requestKey = CACHE_FORMAT_VERSION + "\n" + provider.id() + "\n" + sourceLanguage + "\n"
                + targetLanguage + "\n" + kind + "\n" + preserveHanText + "\n" + text;
        CompletableFuture<TranslationResult> existing = inFlight.get(requestKey);
        if (existing == null) {
            final CompletableFuture<TranslationResult> created =
                    new CompletableFuture<TranslationResult>();
            existing = inFlight.putIfAbsent(requestKey, created);
            if (existing == null) {
                existing = created;
                try {
                    ThreadPoolExecutor targetExecutor = executorFor(kind);
                    int maximumQueueSize = targetExecutor == urgentExecutor && dedicatedUrgentExecutor
                            ? MAX_QUEUED_URGENT_TRANSLATIONS : MAX_QUEUED_TRANSLATIONS;
                    if (targetExecutor.getQueue().size() >= maximumQueueSize) {
                        throw new RejectedExecutionException("Translation queue is busy");
                    }
                    targetExecutor.execute(new PrioritizedTranslationTask(
                            priorityOf(kind),
                            submissionSequence.incrementAndGet(),
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        ProtectedText protectedText = ProtectedText.parse(
                                                text, protectedLiterals, preserveHanText);
                                        if (!LanguageHeuristics.shouldTranslate(
                                                protectedText.getUnprotectedTemplateText(), targetLanguage)) {
                                            created.complete(TranslationResult.unchanged(text));
                                            return;
                                        }
                                        String restored = TranslationOutputValidator.requireDisplaySafe(
                                                text, translateSegments(protectedText, sourceLanguage, targetLanguage, kind));
                                        created.complete(TranslationResult.success(
                                                text, restored));
                                    } catch (Exception exception) {
                                        created.complete(TranslationResult.failure(text, exception.getMessage()));
                                    } finally {
                                        inFlight.remove(requestKey, created);
                                    }
                                }
                            }));
                } catch (RejectedExecutionException busy) {
                    inFlight.remove(requestKey, created);
                    created.complete(TranslationResult.failure(
                            text, "Translation queue is busy; retrying later"));
                }
            }
        }
        return existing;
    }

    public TranslationResult cachedTranslation(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind,
            final Iterable<String> protectedLiterals,
            final boolean preserveHanText
    ) {
        Objects.requireNonNull(text, "text");
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("targetLanguage is required");
        }
        if (!LanguageHeuristics.shouldTranslate(text, targetLanguage)) {
            return TranslationResult.unchanged(text);
        }
        try {
            ProtectedText protectedText = ProtectedText.parse(text, protectedLiterals, preserveHanText);
            if (!LanguageHeuristics.shouldTranslate(protectedText.getUnprotectedTemplateText(), targetLanguage)) {
                return TranslationResult.unchanged(text);
            }
            String restored = TranslationOutputValidator.requireDisplaySafe(
                    text, translateCachedSegments(protectedText, sourceLanguage, targetLanguage, kind));
            return TranslationResult.success(text, restored);
        } catch (RuntimeException invalidOrMissingCachedValue) {
            return null;
        }
    }

    private String translateSegments(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) throws Exception {
        StringBuilder output = new StringBuilder(protectedText.getOriginal().length() + 16);
        for (ProtectedText.Segment segment : protectedText.getSegments()) {
            if (segment.isProtectedValue()) {
                output.append(segment.text());
            } else {
                output.append(translateSegment(
                        segment.text(), sourceLanguage, targetLanguage, kind));
            }
        }
        return output.toString();
    }

    private String translateCachedSegments(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) {
        StringBuilder output = new StringBuilder(protectedText.getOriginal().length() + 16);
        for (ProtectedText.Segment segment : protectedText.getSegments()) {
            if (segment.isProtectedValue()) {
                output.append(segment.text());
            } else {
                output.append(translateCachedSegment(segment.text(), sourceLanguage, targetLanguage, kind));
            }
        }
        return output.toString();
    }

    private String translateSegment(
            String segment,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) throws Exception {
        int start = 0;
        int end = segment.length();
        while (start < end && Character.isWhitespace(segment.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(segment.charAt(end - 1))) {
            end--;
        }
        String core = segment.substring(start, end);
        if (!LanguageHeuristics.shouldTranslate(core, targetLanguage)) {
            return segment;
        }
        String exact = GameTranslationHints.exactTranslation(core, targetLanguage);
        if (exact != null) {
            recentContext.remember(core, exact, kind);
            return segment.substring(0, start) + exact + segment.substring(end);
        }
        String cacheKey = CACHE_FORMAT_VERSION + "\n" + provider.id()
                + "\n" + sourceLanguage + "\n" + targetLanguage + "\n" + core;
        String translated = cache.get(cacheKey);
        if (translated != null) {
            try {
                translated = TranslationOutputValidator.requireValid(core, translated);
            } catch (IllegalArgumentException invalidCachedValue) {
                translated = null;
            }
        }
        if (translated == null) {
            translated = requestValidatedTranslation(core, core, sourceLanguage, targetLanguage, kind);
            if (translated == null) {
                String normalized = normalizeAllCapsCore(core);
                if (normalized != null) {
                    translated = requestValidatedTranslation(normalized, core, sourceLanguage, targetLanguage, kind);
                }
            }
            if (translated == null) {
                throw new IllegalStateException("Provider returned an empty translation");
            }
            cache.put(cacheKey, translated);
        }
        recentContext.remember(core, translated, kind);
        return segment.substring(0, start) + translated + segment.substring(end);
    }

    private String requestValidatedTranslation(
            String requestText,
            String validationText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) throws Exception {
        String contextHint = recentContext.snapshot(kind);
        String translated = provider.translate(new TranslationRequest(
                requestText, sourceLanguage, targetLanguage, kind, contextHint));
        if (translated == null || translated.trim().isEmpty()) {
            return null;
        }
        try {
            String valid = TranslationOutputValidator.requireValid(validationText, translated);
            recentContext.remember(validationText, valid, kind);
            return valid;
        } catch (IllegalArgumentException invalidOutput) {
            return null;
        }
    }

    private static String normalizeAllCapsCore(String core) {
        boolean hasLetter = false;
        boolean hasLowerCase = false;
        for (int i = 0; i < core.length(); i++) {
            char ch = core.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
                if (Character.isLowerCase(ch)) {
                    hasLowerCase = true;
                    break;
                }
            }
        }
        if (!hasLetter || hasLowerCase) {
            return null;
        }
        String lower = core.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String translateCachedSegment(
            String segment,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) {
        int start = 0;
        int end = segment.length();
        while (start < end && Character.isWhitespace(segment.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(segment.charAt(end - 1))) {
            end--;
        }
        String core = segment.substring(start, end);
        if (!LanguageHeuristics.shouldTranslate(core, targetLanguage)) {
            return segment;
        }
        String exact = GameTranslationHints.exactTranslation(core, targetLanguage);
        if (exact != null) {
            recentContext.remember(core, exact, kind);
            return segment.substring(0, start) + exact + segment.substring(end);
        }
        String cacheKey = CACHE_FORMAT_VERSION + "\n" + provider.id()
                + "\n" + sourceLanguage + "\n" + targetLanguage + "\n" + core;
        String translated = cache.get(cacheKey);
        if (translated == null) {
            throw new IllegalStateException("Cached translation is missing");
        }
        translated = TranslationOutputValidator.requireValid(core, translated);
        recentContext.remember(core, translated, kind);
        return segment.substring(0, start) + translated + segment.substring(end);
    }

    public void clearCache() {
        cache.clear();
        for (CompletableFuture<TranslationResult> future : inFlight.values()) {
            future.cancel(false);
        }
        inFlight.clear();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Complete callers before interrupting workers. Otherwise an interrupted
        // provider can win the race and publish an ordinary failure result even
        // though the whole translation session is being cancelled.
        for (CompletableFuture<TranslationResult> future : inFlight.values()) {
            future.completeExceptionally(
                    new CancellationException("Translation session was closed"));
        }
        inFlight.clear();
        executor.shutdownNow();
        if (dedicatedUrgentExecutor) {
            urgentExecutor.shutdownNow();
        }
        if (provider instanceof AutoCloseable) {
            try {
                ((AutoCloseable) provider).close();
            } catch (Exception ignored) {
                // Minecraft is shutting down or applying a replacement configuration.
            }
        }
    }

    private ThreadPoolExecutor executorFor(TextKind kind) {
        return priorityOf(kind) == 0 ? urgentExecutor : executor;
    }

    private static int priorityOf(TextKind kind) {
        if (kind == null) {
            return 80;
        }
        switch (kind) {
            case TITLE:
            case SUBTITLE:
            case ACTION_BAR:
            case BOSS_BAR:
            case TOAST:
                return 0;
            case CHAT:
            case SYSTEM_MESSAGE:
            case DISCONNECT_REASON:
                return 10;
            case CONTAINER_TITLE:
            case ITEM_NAME:
            case ITEM_LORE:
            case TOOLTIP:
            case SIGN:
            case BOOK:
                return 20;
            case SCOREBOARD_TITLE:
            case SCOREBOARD_LINE:
            case PLAYER_LIST_HEADER:
            case PLAYER_LIST_FOOTER:
                return 40;
            default:
                return 80;
        }
    }

    private static final class PrioritizedTranslationTask implements Runnable, Comparable<PrioritizedTranslationTask> {
        private final int priority;
        private final int sequence;
        private final Runnable delegate;

        private PrioritizedTranslationTask(int priority, int sequence, Runnable delegate) {
            this.priority = priority;
            this.sequence = sequence;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedTranslationTask other) {
            if (priority != other.priority) {
                return priority < other.priority ? -1 : 1;
            }
            if (sequence == other.sequence) {
                return 0;
            }
            return sequence < other.sequence ? -1 : 1;
        }
    }

    private static final class TranslationThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();
        private final String prefix;

        private TranslationThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

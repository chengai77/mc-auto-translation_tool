package org.universaltranslator.core;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates protection, caching, in-flight de-duplication and background work.
 * It never blocks a Minecraft render thread.
 */
public final class TranslationCoordinator implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(
            TranslationCoordinator.class.getName());
    private static final int MAX_QUEUED_TRANSLATIONS = 128;
    private static final int MAX_QUEUED_URGENT_TRANSLATIONS = 64;
    private static final int MAX_QUEUED_SYSTEM_TRANSLATIONS = 64;
    private static final int MAX_SHARED_FOREGROUND_RESERVE = 32;
    private static final String CACHE_FORMAT_VERSION = "translation-v13-" + GameTranslationHints.VERSION;

    private final TranslationProvider provider;
    private final TranslationStore cache;
    private final ThreadPoolExecutor executor;
    private final ThreadPoolExecutor urgentExecutor;
    private final ThreadPoolExecutor chatExecutor;
    private final ThreadPoolExecutor systemExecutor;
    private final boolean dedicatedUrgentExecutor;
    private final boolean dedicatedChatExecutor;
    private final boolean dedicatedSystemExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<TranslationResult>> inFlight =
            new ConcurrentHashMap<String, CompletableFuture<TranslationResult>>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean providerClosed = new AtomicBoolean();
    private final AtomicInteger submissionSequence = new AtomicInteger();
    private final RecentTranslationContext recentContext = new RecentTranslationContext();

    public TranslationCoordinator(TranslationProvider provider, TranslationStore cache, int workerCount) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.cache = Objects.requireNonNull(cache, "cache");
        TranslationCacheIdentity.registerProvider(cache, provider.id());
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
        if (workerCount > 1) {
            int chatWorkers = Math.min(2, workerCount);
            this.chatExecutor = new ThreadPoolExecutor(
                    chatWorkers,
                    chatWorkers,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<Runnable>(),
                    new TranslationThreadFactory("universal-translator-chat-"),
                    new ThreadPoolExecutor.AbortPolicy());
            this.dedicatedChatExecutor = true;
        } else {
            this.chatExecutor = this.urgentExecutor;
            this.dedicatedChatExecutor = false;
        }
        if (workerCount > 1) {
            int systemWorkers = Math.min(2, workerCount);
            this.systemExecutor = new ThreadPoolExecutor(
                    systemWorkers,
                    systemWorkers,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new PriorityBlockingQueue<Runnable>(),
                    new TranslationThreadFactory("universal-translator-system-"),
                    new ThreadPoolExecutor.AbortPolicy());
            this.dedicatedSystemExecutor = true;
        } else {
            this.systemExecutor = this.urgentExecutor;
            this.dedicatedSystemExecutor = false;
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
        return translate(text, sourceLanguage, targetLanguage, kind,
                protectedLiterals, preserveHanText, "");
    }

    public CompletableFuture<TranslationResult> translate(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind,
            final Iterable<String> protectedLiterals,
            final boolean preserveHanText,
            final String protectedContextKey
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

        // 脱离渲染线程
        // 廉价去重键
        // 进行中亦去重
        final String contextKey = protectedContextKey == null
                ? "" : protectedContextKey.trim();
        final String requestKey = CACHE_FORMAT_VERSION + "\n" + provider.id() + "\n" + sourceLanguage + "\n"
                + targetLanguage + "\n" + kind + "\n" + preserveHanText
                + "\n" + contextKey + "\n" + text;
        CompletableFuture<TranslationResult> existing = inFlight.get(requestKey);
        if (existing == null) {
            final CompletableFuture<TranslationResult> created =
                    new CompletableFuture<TranslationResult>();
            existing = inFlight.putIfAbsent(requestKey, created);
            if (existing == null) {
                existing = created;
                try {
                    ThreadPoolExecutor targetExecutor = executorFor(kind);
                    int maximumQueueSize = maximumQueueSize(targetExecutor, kind);
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
                                        InlineTextureCode.TranslationPlan texturePlan =
                                                InlineTextureCode.prepareForTranslation(text);
                                        ProtectedText protectedText = ProtectedText.parse(
                                                texturePlan.request(), protectedLiterals, preserveHanText);
                                        if (!LanguageHeuristics.shouldTranslate(
                                                protectedText.getUnprotectedTemplateText(), targetLanguage)) {
                                            created.complete(TranslationResult.unchanged(text));
                                            return;
                                        }
                                        String cached = cachedRestoredTranslation(
                                                protectedText, sourceLanguage, targetLanguage,
                                                kind, texturePlan, text);
                                        if (cached != null) {
                                            if (!contextKey.isEmpty()) {
                                                putCached(renderedCacheIdentity(
                                                        sourceLanguage, targetLanguage, kind,
                                                        preserveHanText, contextKey, text), cached);
                                            }
                                            created.complete(TranslationResult.success(text, cached));
                                            return;
                                        }
                                        String translated = translateSegments(
                                                protectedText, sourceLanguage, targetLanguage, kind);
                                        String restored = restoreDisplayText(
                                                text, translated, targetLanguage, texturePlan);
                                        if (!contextKey.isEmpty()) {
                                            putCached(renderedCacheIdentity(
                                                    sourceLanguage, targetLanguage, kind,
                                                    preserveHanText, contextKey, text), restored);
                                        }
                                        created.complete(TranslationResult.success(
                                                text, restored));
                                    } catch (Exception exception) {
                                        created.complete(TranslationOutputValidator
                                                .isOutputValidationFailure(exception)
                                                ? TranslationResult.recoverableFailure(
                                                        text, exception.getMessage())
                                                : TranslationResult.failure(
                                                        text, exception.getMessage()));
                                    } finally {
                                        inFlight.remove(requestKey, created);
                                    }
                                }
                            }));
                } catch (RejectedExecutionException busy) {
                    inFlight.remove(requestKey, created);
                    created.complete(TranslationResult.recoverableFailure(
                            text, "Translation queue is busy; retrying later"));
                }
            }
        }
        return existing;
    }

    public TranslationResult cachedRenderedTranslation(
            final String text,
            final String sourceLanguage,
            final String targetLanguage,
            final TextKind kind,
            final boolean preserveHanText,
            final String protectedContextKey
    ) {
        Objects.requireNonNull(text, "text");
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("targetLanguage is required");
        }
        String contextKey = protectedContextKey == null
                ? "" : protectedContextKey.trim();
        if (contextKey.isEmpty()) {
            return null;
        }
        String translated = getCached(renderedCacheIdentity(
                sourceLanguage, targetLanguage, kind,
                preserveHanText, contextKey, text));
        if (translated == null) {
            return null;
        }
        try {
            translated = LocalizedNumericGrammar.normalize(
                    text, translated, targetLanguage);
            translated = LocalizedClauseOrder.normalize(text, translated, targetLanguage);
            return TranslationResult.success(text,
                    TranslationOutputValidator.requireRestoredDisplaySafe(text, translated));
        } catch (IllegalArgumentException invalidCachedValue) {
            return null;
        }
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
            InlineTextureCode.TranslationPlan texturePlan =
                    InlineTextureCode.prepareForTranslation(text);
            ProtectedText protectedText = ProtectedText.parse(
                    texturePlan.request(), protectedLiterals, preserveHanText);
            if (!LanguageHeuristics.shouldTranslate(protectedText.getUnprotectedTemplateText(), targetLanguage)) {
                return TranslationResult.unchanged(text);
            }
            String translated = translateCachedSegments(
                    protectedText, sourceLanguage, targetLanguage, kind);
            String restored = restoreDisplayText(
                    text, translated, targetLanguage, texturePlan);
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
        if (StructuredTemplateTranslator.requiresHologramSegmentation(protectedText, kind)) {
            return translateHologramBlocks(
                    protectedText, sourceLanguage, targetLanguage, kind);
        }
        if (StructuredTemplateTranslator.shouldUse(protectedText, kind)) {
            try {
                return StructuredTemplateTranslator.translate(
                        protectedText, sourceLanguage, targetLanguage, kind,
                        provider, cache, recentContext, CACHE_FORMAT_VERSION);
            } catch (StructuredTemplateTranslator.TemplateTranslationException invalidTemplate) {
                // 全大写原文常被模型原样返回，回退分段后重试
                boolean allCaps = LanguageHeuristics.normalizeAllCaps(
                        protectedText.getUnprotectedTemplateText()) != null;
                if (!invalidTemplate.canFallbackToSegments() && !allCaps) {
                    throw invalidTemplate;
                }
                logStructuredFallback(protectedText, kind);
                return translateLocalSegments(
                        protectedText, sourceLanguage, targetLanguage, kind, false);
            }
        }
        boolean preserveLines = StructuredTemplateTranslator.requiresLocalChatSegmentation(
                protectedText, kind);
        return translateLocalSegments(
                protectedText, sourceLanguage, targetLanguage, kind, preserveLines);
    }

    private String translateLocalSegments(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            boolean preserveLines
    ) throws Exception {
        if (preserveLines) {
            return SegmentBatchTranslator.translate(
                    protectedText, sourceLanguage, targetLanguage, kind,
                    provider, cache, recentContext, CACHE_FORMAT_VERSION);
        }
        StringBuilder output = new StringBuilder(protectedText.getOriginal().length() + 16);
        for (ProtectedText.Segment segment : protectedText.getSegments()) {
            if (segment.isProtectedValue()) {
                output.append(segment.text());
            } else {
                output.append(translateSegment(
                        segment.text(), sourceLanguage, targetLanguage, kind, preserveLines));
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
        if (StructuredTemplateTranslator.requiresHologramSegmentation(protectedText, kind)) {
            return translateCachedHologramBlocks(
                    protectedText, sourceLanguage, targetLanguage, kind);
        }
        if (StructuredTemplateTranslator.shouldUse(protectedText, kind)) {
            try {
                return StructuredTemplateTranslator.translateCached(
                        protectedText, sourceLanguage, targetLanguage, kind,
                        provider, cache, recentContext, CACHE_FORMAT_VERSION);
            } catch (RuntimeException missingStructuredValue) {
                return translateCachedLocalSegments(
                        protectedText, sourceLanguage, targetLanguage, kind, false);
            }
        }
        boolean preserveLines = StructuredTemplateTranslator.requiresLocalChatSegmentation(
                protectedText, kind);
        return translateCachedLocalSegments(
                protectedText, sourceLanguage, targetLanguage, kind, preserveLines);
    }

    private String cachedRestoredTranslation(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            InlineTextureCode.TranslationPlan texturePlan,
            String source
    ) {
        try {
            String translated = translateCachedSegments(
                    protectedText, sourceLanguage, targetLanguage, kind);
            return restoreDisplayText(source, translated, targetLanguage, texturePlan);
        } catch (RuntimeException missingOrInvalidCache) {
            return null;
        }
    }

    private String translateCachedLocalSegments(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            boolean preserveLines
    ) {
        if (preserveLines) {
            return SegmentBatchTranslator.translateCached(
                    protectedText, sourceLanguage, targetLanguage, kind,
                    provider, cache, recentContext, CACHE_FORMAT_VERSION);
        }
        StringBuilder output = new StringBuilder(protectedText.getOriginal().length() + 16);
        for (ProtectedText.Segment segment : protectedText.getSegments()) {
            if (segment.isProtectedValue()) {
                output.append(segment.text());
            } else {
                output.append(translateCachedSegment(
                        segment.text(), sourceLanguage, targetLanguage, kind, preserveLines));
            }
        }
        return output.toString();
    }

    private static void logStructuredFallback(ProtectedText protectedText, TextKind kind) {
        LOGGER.log(Level.WARNING,
                "Structured translation used local fallback: kind={0}, protectedValues={1}, "
                        + "multiline={2}, sourceHash={3}",
                new Object[]{
                        kind == null ? TextKind.OTHER : kind,
                        Integer.valueOf(protectedText.getValues().size()),
                        Boolean.valueOf(VisualTextLayout.containsLineBreak(
                                protectedText.getOriginal())),
                        Integer.toHexString(protectedText.getOriginal().hashCode())
                });
    }

    private String translateHologramBlocks(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) throws Exception {
        HologramTextLayout.TranslationBatch batch =
                HologramTextLayout.translationBatch(protectedText.getOriginal());
        if (batch == null) {
            throw new IllegalStateException("Invalid hologram translation template");
        }
        List<String> translatedContents = new ArrayList<String>();
        for (String content : batch.contents()) {
            ProtectedText block = parseHologramBlock(content, protectedText.getValues());
            if (!LanguageHeuristics.shouldTranslate(
                    block.getUnprotectedTemplateText(), targetLanguage)) {
                translatedContents.add(content);
                continue;
            }
            try {
                translatedContents.add(StructuredTemplateTranslator.translate(
                        block, sourceLanguage, targetLanguage, kind,
                        provider, cache, recentContext, CACHE_FORMAT_VERSION));
            } catch (StructuredTemplateTranslator.TemplateTranslationException invalidTemplate) {
                // 全大写原文常被模型原样返回，回退分段后重试
                boolean allCaps = LanguageHeuristics.normalizeAllCaps(
                        block.getUnprotectedTemplateText()) != null;
                if (!invalidTemplate.canFallbackToSegments() && !allCaps) {
                    throw invalidTemplate;
                }
                logStructuredFallback(block, kind);
                translatedContents.add(translateLocalSegments(
                        block, sourceLanguage, targetLanguage, kind, false));
            }
        }
        String restored = batch.restore(translatedContents);
        if (restored == null) {
            throw new IllegalStateException("Could not restore hologram translation blocks");
        }
        restored = LocalizedNumericGrammar.normalize(
                protectedText.getOriginal(), restored, targetLanguage);
        putCached(hologramBatchCacheIdentity(
                sourceLanguage, targetLanguage, protectedText.getOriginal()), restored);
        recentContext.remember(protectedText.getOriginal(), restored, kind);
        return restored;
    }

    private String translateCachedHologramBlocks(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) {
        String aggregate = getCached(hologramBatchCacheIdentity(
                sourceLanguage, targetLanguage, protectedText.getOriginal()));
        if (aggregate != null) {
            try {
                aggregate = LocalizedNumericGrammar.normalize(
                        protectedText.getOriginal(), aggregate, targetLanguage);
                return TranslationOutputValidator.requireRestoredDisplaySafe(
                        protectedText.getOriginal(), aggregate);
            } catch (IllegalArgumentException invalidAggregate) {
                // 续用缓存块
            }
        }
        HologramTextLayout.TranslationBatch batch =
                HologramTextLayout.translationBatch(protectedText.getOriginal());
        if (batch == null) {
            throw new IllegalStateException("Invalid hologram translation template");
        }
        List<String> translatedContents = new ArrayList<String>();
        for (String content : batch.contents()) {
            ProtectedText block = parseHologramBlock(content, protectedText.getValues());
            if (!LanguageHeuristics.shouldTranslate(
                    block.getUnprotectedTemplateText(), targetLanguage)) {
                translatedContents.add(content);
                continue;
            }
            try {
                translatedContents.add(StructuredTemplateTranslator.translateCached(
                        block, sourceLanguage, targetLanguage, kind,
                        provider, cache, recentContext, CACHE_FORMAT_VERSION));
            } catch (RuntimeException missingStructuredBlock) {
                translatedContents.add(translateCachedLocalSegments(
                        block, sourceLanguage, targetLanguage, kind, false));
            }
        }
        String restored = batch.restore(translatedContents);
        if (restored == null) {
            throw new IllegalStateException("Could not restore cached hologram blocks");
        }
        restored = LocalizedNumericGrammar.normalize(
                protectedText.getOriginal(), restored, targetLanguage);
        putCached(hologramBatchCacheIdentity(
                sourceLanguage, targetLanguage, protectedText.getOriginal()), restored);
        recentContext.remember(protectedText.getOriginal(), restored, kind);
        return restored;
    }

    private static ProtectedText parseHologramBlock(
            String content, List<String> protectedValues) {
        List<String> matchingValues = new ArrayList<String>();
        for (String value : protectedValues) {
            if (value != null && !value.isEmpty() && content.contains(value)
                    && !matchingValues.contains(value)) {
                matchingValues.add(value);
            }
        }
        return ProtectedText.parse(content, matchingValues, false);
    }

    private static String hologramBatchCacheIdentity(
            String sourceLanguage, String targetLanguage, String source) {
        return sourceLanguage + "\n" + targetLanguage
                + "\nhologram-batch\n" + source;
    }

    private static String renderedCacheIdentity(
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            boolean preserveHanText,
            String protectedContextKey,
            String source
    ) {
        return sourceLanguage + "\n" + targetLanguage
                + "\n" + kind + "\n" + preserveHanText
                + "\nrendered\n" + protectedContextKey + "\n" + source;
    }

    private String translateSegment(
            String segment,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            boolean preserveLines
    ) throws Exception {
        if (preserveLines && VisualTextLayout.containsLineBreak(segment)) {
            StringBuilder output = new StringBuilder(segment.length() + 16);
            java.util.List<String> lines = VisualTextBoundaries.splitLines(segment);
            for (int index = 0; index < lines.size(); index++) {
                if (index > 0) {
                    output.append('\n');
                }
                output.append(translateSegment(
                        lines.get(index), sourceLanguage, targetLanguage, kind, false));
            }
            return output.toString();
        }
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
        String exact = GameTranslationHints.localTranslation(core, targetLanguage);
        if (exact != null) {
            recentContext.remember(core, exact, kind);
            return segment.substring(0, start) + exact + segment.substring(end);
        }
        String cacheIdentity =
                sourceLanguage + "\n" + targetLanguage + "\n" + core;
        String translated = getCached(cacheIdentity);
        if (translated != null) {
            try {
                translated = TranslationOutputValidator.requireValid(
                        core, translated, targetLanguage);
            } catch (IllegalArgumentException invalidCachedValue) {
                translated = null;
            }
        }
        if (translated == null) {
            translated = requestValidatedTranslation(core, core, sourceLanguage, targetLanguage, kind);
            if (translated == null) {
                String normalized = LanguageHeuristics.normalizeAllCaps(core);
                if (normalized != null) {
                    translated = requestValidatedTranslation(normalized, core, sourceLanguage, targetLanguage, kind);
                }
            }
            if (translated == null) {
                throw TranslationOutputValidator.invalidOutput(
                        "Provider returned no valid translation");
            }
            putCached(cacheIdentity, translated);
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
        String contextHint = recentContext.snapshot(kind, requestText);
        for (int attempt = 0; attempt < 2; attempt++) {
            String translated;
            try {
                translated = provider.translate(new TranslationRequest(
                        requestText, sourceLanguage, targetLanguage, kind, contextHint));
            } catch (Exception providerFailure) {
                if (TranslationOutputValidator.isOutputValidationFailure(providerFailure)) {
                    continue;
                }
                throw providerFailure;
            }
            if (translated == null || translated.trim().isEmpty()) {
                continue;
            }
            try {
                String valid = TranslationOutputValidator.requireValid(
                        validationText, translated, targetLanguage);
                recentContext.remember(validationText, valid, kind);
                return valid;
            } catch (IllegalArgumentException invalidOutput) {
                // 异常译文不入缓存
            }
        }
        return null;
    }

    private String translateCachedSegment(
            String segment,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            boolean preserveLines
    ) {
        if (preserveLines && VisualTextLayout.containsLineBreak(segment)) {
            StringBuilder output = new StringBuilder(segment.length() + 16);
            java.util.List<String> lines = VisualTextBoundaries.splitLines(segment);
            for (int index = 0; index < lines.size(); index++) {
                if (index > 0) {
                    output.append('\n');
                }
                output.append(translateCachedSegment(
                        lines.get(index), sourceLanguage, targetLanguage, kind, false));
            }
            return output.toString();
        }
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
        String exact = GameTranslationHints.localTranslation(core, targetLanguage);
        if (exact != null) {
            recentContext.remember(core, exact, kind);
            return segment.substring(0, start) + exact + segment.substring(end);
        }
        String translated = getCached(
                sourceLanguage + "\n" + targetLanguage + "\n" + core);
        if (translated == null) {
            throw new IllegalStateException("Cached translation is missing");
        }
        translated = TranslationOutputValidator.requireValid(
                core, translated, targetLanguage);
        recentContext.remember(core, translated, kind);
        return segment.substring(0, start) + translated + segment.substring(end);
    }

    private String getCached(String identity) {
        return TranslationCacheIdentity.get(
                cache, provider.id(), CACHE_FORMAT_VERSION, identity);
    }

    private void putCached(String identity, String value) {
        TranslationCacheIdentity.put(
                cache, CACHE_FORMAT_VERSION, identity, value);
    }

    private static String normalizeLocalizedOutput(String translated, String targetLanguage) {
        return LocalizedDateOrder.normalize(translated, targetLanguage);
    }

    private static String restoreDisplayText(
            String source,
            String translated,
            String targetLanguage,
            InlineTextureCode.TranslationPlan texturePlan
    ) {
        String restored = texturePlan.restore(
                normalizeLocalizedOutput(translated, targetLanguage));
        if (restored == null) {
            throw new IllegalArgumentException("Translation changed inline texture line boundaries");
        }
        restored = LocalizedNumericGrammar.normalize(
                source, restored, targetLanguage);
        restored = LocalizedClauseOrder.normalize(source, restored, targetLanguage);
        return TranslationOutputValidator.requireRestoredDisplaySafe(source, restored);
    }

    public void clearCache() {
        cache.clear();
        for (CompletableFuture<TranslationResult> future : inFlight.values()) {
            future.cancel(false);
        }
        inFlight.clear();
    }

    public TranslationStore cacheStore() {
        return cache;
    }

    @Override
    public void close() {
        stopWork();
        if (!providerClosed.compareAndSet(false, true)) {
            return;
        }
        closeProvider();
    }

    /** 只停止翻译任务，不在调用线程关闭网络或本地模型 provider。 */
    public void deactivate() {
        stopWork();
    }

    private void stopWork() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // 先完成调用
        // 避免竞争失败
        // 取消时不覆盖
        for (CompletableFuture<TranslationResult> future : inFlight.values()) {
            future.completeExceptionally(
                    new CancellationException("Translation session was closed"));
        }
        inFlight.clear();
        executor.shutdownNow();
        if (dedicatedUrgentExecutor) {
            urgentExecutor.shutdownNow();
        }
        if (dedicatedChatExecutor) {
            chatExecutor.shutdownNow();
        }
        if (dedicatedSystemExecutor) {
            systemExecutor.shutdownNow();
        }
    }

    private void closeProvider() {
        if (provider instanceof AutoCloseable) {
            try {
                ((AutoCloseable) provider).close();
            } catch (Exception ignored) {
                // 关闭或换配置
            }
        }
    }

    private ThreadPoolExecutor executorFor(TextKind kind) {
        if (kind == TextKind.CHAT && dedicatedChatExecutor) {
            return chatExecutor;
        }
        if (kind == TextKind.SYSTEM_MESSAGE && dedicatedSystemExecutor) {
            return systemExecutor;
        }
        return priorityOf(kind) < 80 ? urgentExecutor : executor;
    }

    private int maximumQueueSize(ThreadPoolExecutor targetExecutor, TextKind kind) {
        if (targetExecutor == chatExecutor && dedicatedChatExecutor) {
            return MAX_QUEUED_URGENT_TRANSLATIONS;
        }
        if (targetExecutor == systemExecutor && dedicatedSystemExecutor) {
            return MAX_QUEUED_SYSTEM_TRANSLATIONS;
        }
        if (targetExecutor == urgentExecutor && dedicatedUrgentExecutor) {
            return MAX_QUEUED_URGENT_TRANSLATIONS;
        }
        return priorityOf(kind) < 80
                ? MAX_QUEUED_TRANSLATIONS + MAX_SHARED_FOREGROUND_RESERVE
                : MAX_QUEUED_TRANSLATIONS;
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

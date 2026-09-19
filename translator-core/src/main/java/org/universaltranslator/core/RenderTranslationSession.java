package org.universaltranslator.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

import static org.universaltranslator.core.VisualTextLayout.containsLineBreak;
import static org.universaltranslator.core.VisualTextLayout.distributeHologramBlocks;
import static org.universaltranslator.core.VisualTextLayout.distributeLineBounded;
import static org.universaltranslator.core.VisualTextLayout.distributeVisualLines;
import static org.universaltranslator.core.VisualTextLayout.joinVisualLines;
import static org.universaltranslator.core.VisualTextLayout.joinWithNewlines;

/**
 * Non-blocking lookup facade for render hooks. The first frame returns the original;
 * completed translations are substituted on later frames.
 */
public final class RenderTranslationSession implements AutoCloseable {
    private static final long FAILURE_RETRY_MILLIS = 30_000L;
    private static final int MAX_BACKGROUND_PENDING_TRANSLATIONS = 128;
    private static final int MAX_FOREGROUND_PENDING_TRANSLATIONS = 32;
    private static final int MAX_CHAT_PENDING_TRANSLATIONS = 16;
    private static final int MAX_SYSTEM_MESSAGE_PENDING_TRANSLATIONS = 64;
    private static final int MAX_RENDERED_TRANSLATIONS = 4_096;
    private static final int MAX_FAILED_TRANSLATIONS = 1_024;
    private static final int MAX_BACKGROUND_SUBMISSIONS_PER_SECOND = 4;
    private static final int MAX_PRIORITY_SUBMISSIONS_PER_SECOND = 16;
    private static final int MAX_CHAT_SUBMISSIONS_PER_SECOND = 16;
    private static final int MAX_SYSTEM_MESSAGE_SUBMISSIONS_PER_SECOND = 32;
    private static final int MAX_URGENT_SUBMISSIONS_PER_SECOND = 30;

    private final TranslationCoordinator coordinator;
    private final String providerId;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final TranslationDisplayMode displayMode;
    private final boolean preserveHanText;
    private final ConcurrentHashMap<RenderKey, String> translated = new ConcurrentHashMap<RenderKey, String>();
    private final ConcurrentHashMap<String, Boolean> translatedOutputs =
            new ConcurrentHashMap<String, Boolean>();
    private final ConcurrentHashMap<RenderKey, PendingKind> pending =
            new ConcurrentHashMap<RenderKey, PendingKind>();
    private final PendingTranslationBudget pendingBudget = new PendingTranslationBudget(
            MAX_BACKGROUND_PENDING_TRANSLATIONS, MAX_FOREGROUND_PENDING_TRANSLATIONS,
            MAX_CHAT_PENDING_TRANSLATIONS, MAX_SYSTEM_MESSAGE_PENDING_TRANSLATIONS);
    private final ConcurrentHashMap<RenderKey, Long> retryAfter = new ConcurrentHashMap<RenderKey, Long>();
    private final SubmissionWindow backgroundSubmissions =
            new SubmissionWindow(MAX_BACKGROUND_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow prioritySubmissions =
            new SubmissionWindow(MAX_PRIORITY_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow chatSubmissions =
            new SubmissionWindow(MAX_CHAT_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow systemMessageSubmissions =
            new SubmissionWindow(MAX_SYSTEM_MESSAGE_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow urgentSubmissions =
            new SubmissionWindow(MAX_URGENT_SUBMISSIONS_PER_SECOND);
    private final VisualLineGrouper visualLineGrouper = new VisualLineGrouper();
    private volatile boolean closed;
    private boolean resourcesClosed;
    private volatile String lastFailureStatus = "";
    private volatile BiConsumer<TextKind, String> urgentCompletionListener;
    private volatile BiConsumer<TextKind, String> renderCompletionListener;
    private volatile boolean dynamicProtectedLiterals;
    private volatile Supplier<? extends Iterable<String>> protectedLiterals =
            new Supplier<Iterable<String>>() {
                @Override
                public Iterable<String> get() {
                    return Collections.emptyList();
                }
            };

    public RenderTranslationSession(
            TranslationProvider provider,
            String sourceLanguage,
            String targetLanguage,
            int maximumCacheEntries,
            int workerCount
    ) {
        this(provider, sourceLanguage, targetLanguage, new TranslationCache(maximumCacheEntries), workerCount,
                TranslationDisplayMode.TRANSLATED_ONLY, true);
    }

    public RenderTranslationSession(
            TranslationProvider provider,
            String sourceLanguage,
            String targetLanguage,
            TranslationStore store,
            int workerCount
    ) {
        this(provider, sourceLanguage, targetLanguage, store, workerCount,
                TranslationDisplayMode.TRANSLATED_ONLY, true);
    }

    public RenderTranslationSession(
            TranslationProvider provider,
            String sourceLanguage,
            String targetLanguage,
            TranslationStore store,
            int workerCount,
            TranslationDisplayMode displayMode
    ) {
        this(provider, sourceLanguage, targetLanguage, store, workerCount, displayMode, true);
    }

    public RenderTranslationSession(
            TranslationProvider provider,
            String sourceLanguage,
            String targetLanguage,
            TranslationStore store,
            int workerCount,
            TranslationDisplayMode displayMode,
            boolean preserveHanText
    ) {
        this.providerId = provider.id();
        this.coordinator = new TranslationCoordinator(provider, store, workerCount);
        this.sourceLanguage = sourceLanguage == null ? "auto" : sourceLanguage;
        if (targetLanguage == null || targetLanguage.trim().isEmpty()) {
            this.coordinator.close();
            throw new IllegalArgumentException("targetLanguage is required");
        }
        this.targetLanguage = targetLanguage.trim();
        this.displayMode = displayMode == null
                ? TranslationDisplayMode.TRANSLATED_ONLY : displayMode;
        this.preserveHanText = preserveHanText;
    }

    public String lookup(String original, TextKind kind) {
        return lookup(original, kind, true);
    }

    public String lookupComplete(String original, TextKind kind) {
        return lookup(original, kind, false);
    }

    /** 不使用全息结构占位符的完整单行翻译。 */
    public String lookupPlainHologram(String original) {
        if (closed || original == null || original.isEmpty()
                || isCompletedOutput(original)
                || !LanguageHeuristics.shouldTranslate(original, targetLanguage)) {
            return original;
        }
        return lookupDirect(original, TextKind.HOLOGRAM);
    }

    private String lookup(String original, TextKind kind, boolean allowVisualGrouping) {
        if (closed || original == null || original.isEmpty()) {
            return original;
        }
        // 双路径文本
        // 避免重复翻译
        if (isCompletedOutput(original)) {
            return original;
        }
        // 跳过已译文本
        // 无词可译跳过
        if (!LanguageHeuristics.shouldTranslate(original, targetLanguage)) {
            return original;
        }
        TextKind effectiveKind = kind == null ? TextKind.OTHER : kind;
        if (!allowVisualGrouping && effectiveKind == TextKind.HOLOGRAM) {
            return lookupCompleteHologram(original);
        }
        if ((effectiveKind == TextKind.BOOK || effectiveKind == TextKind.HOLOGRAM)
                && containsLineBreak(original)) {
            return lookupLineAwareText(original, effectiveKind);
        }
        if (allowVisualGrouping && allowsVisualGrouping(effectiveKind)) {
            VisualLineGrouper.Group group = visualLineGrouper.group(original, effectiveKind);
            if (group != null) {
                if (group.collecting()) {
                    return original;
                }
                return group.lineResult(
                        lookupStableVisualLines(group.lines, effectiveKind));
            }
        }
        return lookupDirect(original, effectiveKind);
    }

    private static boolean allowsVisualGrouping(TextKind kind) {
        return kind == TextKind.BOSS_BAR
                || kind == TextKind.HOLOGRAM
                || kind == TextKind.BOOK;
    }

    private String lookupDirect(String original, TextKind effectiveKind) {
        String raw = lookupDirectRaw(original, effectiveKind);
        return original.equals(raw) ? original : formatOutput(original, raw, effectiveKind);
    }

    private String lookupDirectRaw(String original, TextKind effectiveKind) {
        RenderKey key = new RenderKey(original, effectiveKind);
        String ready = translated.get(key);
        if (ready != null) {
            return ready;
        }

        Long retryAt = retryAfter.get(key);
        long now = System.currentTimeMillis();
        if (retryAt != null && retryAt.longValue() > now) {
            return original;
        }
        if (pending.containsKey(key)) {
            return original;
        }
        Iterable<String> currentProtectedLiterals = dynamicProtectedLiterals
                ? snapshotProtectedLiterals() : Collections.<String>emptyList();
        String protectedContextKey = protectedLiteralsCacheKey(currentProtectedLiterals);
        TranslationResult cached = cachedTranslation(
                original, effectiveKind, targetLanguage, preserveHanText,
                currentProtectedLiterals, protectedContextKey);
        if (cached != null) {
            completeLookup(key, original, cached, null);
            return cached.isTranslated() ? cached.getTranslatedText() : original;
        }
        // 大厅标签密集
        // 限制模型负载
        // 独立额度分配
        // 世界文本优先
        if (!isUrgent(effectiveKind) && !submissionWindow(effectiveKind).tryAcquire()) {
            return original;
        }
        if (!tryMarkPending(key, effectiveKind)) {
            return original;
        }
        try {
            CompletableFuture<TranslationResult> future = coordinator.translate(
                    original, sourceLanguage, targetLanguage, effectiveKind,
                    currentProtectedLiterals, preserveHanText, protectedContextKey);
            future.whenComplete((result, error) -> completeLookup(
                    key, original, result, error));
        } catch (RuntimeException submissionFailure) {
            completeLookup(key, original, null, submissionFailure);
        }
        return original;
    }

    private synchronized boolean tryMarkPending(RenderKey key, TextKind kind) {
        if (pending.containsKey(key)) {
            return false;
        }
        PendingKind pendingKind = pendingKind(kind);
        if (!tryAcquirePending(pendingKind)) {
            return false;
        }
        if (pending.putIfAbsent(key, pendingKind) != null) {
            releasePending(pendingKind);
            return false;
        }
        return true;
    }

    private synchronized void removePending(RenderKey key) {
        PendingKind pendingKind = pending.remove(key);
        if (pendingKind != null) {
            releasePending(pendingKind);
        }
    }

    private boolean tryAcquirePending(PendingKind kind) {
        if (kind == PendingKind.CHAT) {
            return pendingBudget.tryAcquireChat();
        }
        if (kind == PendingKind.SYSTEM_MESSAGE) {
            return pendingBudget.tryAcquireSystemMessage();
        }
        return pendingBudget.tryAcquire(kind == PendingKind.FOREGROUND);
    }

    private void releasePending(PendingKind kind) {
        if (kind == PendingKind.CHAT) {
            pendingBudget.releaseChat();
        } else if (kind == PendingKind.SYSTEM_MESSAGE) {
            pendingBudget.releaseSystemMessage();
        } else {
            pendingBudget.release(kind == PendingKind.FOREGROUND);
        }
    }

    private static PendingKind pendingKind(TextKind kind) {
        if (kind == TextKind.CHAT) {
            return PendingKind.CHAT;
        }
        if (kind == TextKind.SYSTEM_MESSAGE) {
            return PendingKind.SYSTEM_MESSAGE;
        }
        return isForeground(kind) ? PendingKind.FOREGROUND : PendingKind.BACKGROUND;
    }

    private static boolean isForeground(TextKind kind) {
        return kind != TextKind.HOLOGRAM
                && kind != TextKind.ENTITY_NAME
                && kind != TextKind.OTHER;
    }

    private String lookupCompleteHologram(String original) {
        if (providerId.contains("offline-llama:" + OfflineModel.LITE.modelId())
                && !containsLineBreak(original)
                && !StyledTranslationTemplate.contains(original)
                && !InlineTextureCode.matcher(original).find()) {
            return lookupDirect(original, TextKind.HOLOGRAM);
        }
        HologramTextLayout.Plan plan = HologramTextLayout.prepare(original);
        if (plan == null) {
            return containsLineBreak(original)
                    ? lookupLineAwareText(original, TextKind.HOLOGRAM)
                    : lookupDirect(original, TextKind.HOLOGRAM);
        }
        String translatedTemplate = lookupDirectRaw(plan.request(), TextKind.HOLOGRAM);
        if (plan.request().equals(translatedTemplate)) {
            return original;
        }
        String restored = plan.restore(translatedTemplate);
        if (restored == null || restored.equals(original)) {
            return original;
        }
        String output = formatOutput(original, restored, TextKind.HOLOGRAM);
        rememberCompletedOutput(output);
        return output;
    }

    /**
     * Translates related visual lines as one semantic text. Minecraft often wraps one
     * sentence into multiple render lines (chat, signs, books); those wraps must not
     * become translation boundaries.
     */
    public List<String> lookupLines(List<String> originals, TextKind kind) {
        if (originals == null || originals.isEmpty()) {
            return originals;
        }
        if (usesIndependentLineTranslation(kind)) {
            return lookupIndependentLines(originals, kind);
        }
        if (kind == TextKind.SIGN
                && VisualTextBoundaries.hasDecorativeLayout(originals)) {
            return lookupIndependentLines(originals, kind);
        }
        if (preservesSeparatorBoundaries(kind)) {
            List<String> separated = lookupWithLayoutBoundaries(originals, kind);
            if (separated != null) {
                return separated;
            }
        }
        return lookupJoinedLines(originals, kind);
    }

    private List<String> lookupJoinedLines(List<String> originals, TextKind kind) {
        return lookupJoinedLines(originals, kind, false);
    }

    private List<String> lookupJoinedLines(List<String> originals, TextKind kind, boolean direct) {
        String originalText = joinVisualLines(originals);
        if (originalText.isEmpty()) {
            return originals;
        }
        String translatedText = direct ? lookupDirect(originalText, kind) : lookup(originalText, kind);
        if (originalText.equals(translatedText)) {
            return originals;
        }
        if (kind == TextKind.HOLOGRAM) {
            return distributeHologramBlocks(originals, translatedText);
        }
        if (preservesLineBoundaries(kind)) {
            return distributeLineBounded(originals, translatedText);
        }
        return distributeVisualLines(originals, translatedText);
    }

    private String lookupLineAwareText(String original, TextKind kind) {
        List<String> lines = VisualTextBoundaries.splitLines(original);
        List<String> translatedLines = lookupStableVisualLines(lines, kind);
        if (translatedLines.equals(lines)) {
            return original;
        }
        return joinWithNewlines(translatedLines);
    }

    private List<String> lookupStableVisualLines(List<String> originals, TextKind kind) {
        if (kind == TextKind.BOOK && BookMenuLayout.hasMenuRows(originals)) {
            return BookMenuLayout.translate(
                    originals,
                    new BookMenuLayout.LineTranslator() {
                        @Override
                        public String translate(String text) {
                            return lookupDirect(text, kind);
                        }
                    },
                    new BookMenuLayout.BlockTranslator() {
                        @Override
                        public List<String> translate(List<String> lines) {
                            return lookupJoinedLines(lines, kind, true);
                        }
                    });
        }
        if (preservesSeparatorBoundaries(kind)) {
            List<String> separated = lookupWithLayoutBoundaries(originals, kind);
            if (separated != null) {
                return separated;
            }
        }
        return lookupJoinedLines(originals, kind, true);
    }

    private List<String> lookupWithLayoutBoundaries(List<String> originals, TextKind kind) {
        if (!VisualTextBoundaries.hasSeparatorLine(originals)
                && !(kind == TextKind.SIGN
                && VisualTextBoundaries.hasGraphicLayout(originals))) {
            return null;
        }
        List<String> result = new ArrayList<String>(originals.size());
        int index = 0;
        while (index < originals.size()) {
            String line = originals.get(index);
            if (isFixedLayoutLine(kind, originals, line)) {
                result.add(line);
                index++;
                continue;
            }
            int start = index;
            while (index < originals.size()
                    && !isFixedLayoutLine(kind, originals, originals.get(index))) {
                index++;
            }
            result.addAll(lookupJoinedLines(
                    new ArrayList<String>(originals.subList(start, index)), kind, true));
        }
        return result.equals(originals) ? originals : result;
    }

    private static boolean isFixedLayoutLine(
            TextKind kind, List<String> lines, String line) {
        return VisualTextBoundaries.isSeparatorLine(line)
                || (kind == TextKind.SIGN && VisualTextBoundaries.hasGraphicLayout(lines)
                && VisualTextBoundaries.isGraphicLine(line));
    }

    public List<String> lookupIndependentLines(List<String> originals, TextKind kind) {
        if (originals == null || originals.isEmpty()) {
            return originals;
        }
        List<String> translatedLines = null;
        for (int index = 0; index < originals.size(); index++) {
            String original = originals.get(index);
            String translatedLine = lookup(original, kind);
            if (original == null ? translatedLine != null : !original.equals(translatedLine)) {
                if (translatedLines == null) {
                    translatedLines = new ArrayList<String>(originals);
                }
                translatedLines.set(index, translatedLine);
            }
        }
        return translatedLines == null ? originals : translatedLines;
    }

    /**
     * 将视觉换行行合并为一个翻译请求，再按原行数分配译文。
     */
    public List<String> lookupWrappedLines(List<String> originals, TextKind kind) {
        if (originals == null || originals.isEmpty()) {
            return originals;
        }
        return lookupJoinedLines(originals, kind == null ? TextKind.OTHER : kind);
    }

    private static boolean preservesLineBoundaries(TextKind kind) {
        return false;
    }

    private static boolean preservesSeparatorBoundaries(TextKind kind) {
        return kind == TextKind.SIGN || kind == TextKind.BOOK || kind == TextKind.HOLOGRAM;
    }

    private static boolean usesIndependentLineTranslation(TextKind kind) {
        return kind == TextKind.TOOLTIP
                || kind == TextKind.ITEM_LORE;
    }

    public void setUrgentCompletionListener(
            BiConsumer<TextKind, String> urgentCompletionListener
    ) {
        this.urgentCompletionListener = urgentCompletionListener;
    }

    public void setRenderCompletionListener(
            BiConsumer<TextKind, String> renderCompletionListener
    ) {
        this.renderCompletionListener = renderCompletionListener;
    }

    /**
     * Submits an interactive translation and exposes its completion to a platform adapter.
     * This is used for outgoing chat: the caller can cancel the original send, then send the
     * completed result on Minecraft's main thread without ever blocking that thread.
     */
    public CompletableFuture<TranslationResult> translateInteractive(
            String original,
            TextKind kind,
            String requestedTargetLanguage,
            boolean preserveHanText
    ) {
        if (original == null) {
            throw new IllegalArgumentException("original cannot be null");
        }
        String target = requestedTargetLanguage == null ? "" : requestedTargetLanguage.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("targetLanguage is required");
        }
        Iterable<String> literals;
        try {
            literals = protectedLiterals.get();
        } catch (RuntimeException ignored) {
            literals = Collections.emptyList();
        }
        String protectedContextKey = protectedLiteralsCacheKey(literals);
        TranslationResult cached = cachedTranslation(
                original,
                kind == null ? TextKind.CHAT : kind,
                target,
                preserveHanText,
                literals,
                protectedContextKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return coordinator.translate(
                original,
                sourceLanguage,
                target,
                kind == null ? TextKind.CHAT : kind,
                literals,
                preserveHanText,
                protectedContextKey);
    }

    private TranslationResult cachedTranslation(
            String original,
            TextKind kind,
            String requestedTargetLanguage,
            boolean requestedPreserveHanText,
            Iterable<String> literals,
            String protectedContextKey
    ) {
        TranslationResult cached = null;
        if (dynamicProtectedLiterals) {
            cached = coordinator.cachedRenderedTranslation(
                    original, sourceLanguage, requestedTargetLanguage, kind,
                    requestedPreserveHanText, protectedContextKey);
        }
        if (cached != null) {
            return cached;
        }
        if (dynamicProtectedLiterals
                && !(literals instanceof ProtectedLiteralsSnapshot)) {
            return null;
        }
        return coordinator.cachedTranslation(
                original, sourceLanguage, requestedTargetLanguage, kind,
                literals, requestedPreserveHanText);
    }

    private Iterable<String> snapshotProtectedLiterals() {
        try {
            return protectedLiterals.get();
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private static String protectedLiteralsCacheKey(Iterable<String> literals) {
        return literals instanceof ProtectedLiteralsSnapshot
                ? ((ProtectedLiteralsSnapshot) literals).cacheKey() : "";
    }

    private static boolean isUrgent(TextKind kind) {
        if (kind == null) {
            return false;
        }
        switch (kind) {
            case TITLE:
            case SUBTITLE:
            case ACTION_BAR:
            case BOSS_BAR:
            case TOAST:
                return true;
            default:
                return false;
        }
    }

    private synchronized void completeLookup(
            RenderKey key,
            String original,
            TranslationResult result,
            Throwable error
    ) {
        removePending(key);
        if (closed) {
            return;
        }
        if (error != null || result == null || result.isFailure()) {
            if (retryAfter.size() >= MAX_FAILED_TRANSLATIONS) {
                retryAfter.clear();
            }
            long now = System.currentTimeMillis();
            retryAfter.put(key, now + FAILURE_RETRY_MILLIS);
            if (error == null && result != null && result.isRecoverableFailure()) {
                return;
            }
            lastFailureStatus = safeFailureStatus(error, result);
            return;
        }
        lastFailureStatus = "";
        retryAfter.remove(key);
        if (result.isTranslated()) {
            if (translated.size() >= MAX_RENDERED_TRANSLATIONS) {
                translated.clear();
                translatedOutputs.clear();
            }
            String rawOutput = result.getTranslatedText();
            String output = formatOutput(original, rawOutput, key.kind);
            translated.put(key, rawOutput);
            rememberCompletedOutput(rawOutput);
            rememberCompletedOutput(output);
            notifyRenderCompletion(key.kind, output);
            notifyUrgentCompletion(key.kind, output);
        }
    }

    private void rememberCompletedOutput(String output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        translatedOutputs.put(output, Boolean.TRUE);
        String visibleOutput = StyledTranslationTemplate.strip(output);
        translatedOutputs.put(visibleOutput, Boolean.TRUE);
        translatedOutputs.put(
                TranslationTextStyling.stripLegacyFormatting(output), Boolean.TRUE);
        translatedOutputs.put(
                TranslationTextStyling.stripLegacyFormatting(visibleOutput), Boolean.TRUE);
    }

    private void notifyRenderCompletion(TextKind kind, String output) {
        BiConsumer<TextKind, String> listener = renderCompletionListener;
        if (listener == null || output == null || output.isEmpty()) {
            return;
        }
        try {
            listener.accept(kind, output);
        } catch (RuntimeException ignored) {
            // 回调尽力而为
        }
    }

    private void notifyUrgentCompletion(TextKind kind, String output) {
        if (!isUrgent(kind) || output == null || output.isEmpty()) {
            return;
        }
        BiConsumer<TextKind, String> listener = urgentCompletionListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(kind, output);
        } catch (RuntimeException ignored) {
            // 回调不破缓存
        }
    }

    private boolean isCompletedOutput(String text) {
        if (translatedOutputs.containsKey(text)) {
            return true;
        }
        String unformatted = TranslationTextStyling.stripLegacyFormatting(text);
        return unformatted != text && translatedOutputs.containsKey(unformatted);
    }

    private String renderResult(String original, TranslationResult result, TextKind kind) {
        if (result == null || !result.isTranslated()) {
            return original;
        }
        return formatOutput(original, result.getTranslatedText(), kind);
    }

    private String formatOutput(String original, String translatedText, TextKind kind) {
        if (kind == TextKind.HOLOGRAM) {
            translatedText = formatHologramBoundaries(translatedText);
        }
        if (displayMode != TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                || original.equals(translatedText)) {
            return translatedText;
        }
        String bilingualOriginal = StyledTranslationTemplate.strip(original);
        String bilingualTranslation = InlineTextureCode.matcher(original).find()
                ? InlineTextureCode.strip(translatedText) : translatedText;
        return TranslationDisplayText.bilingual(bilingualOriginal, bilingualTranslation);
    }

    private static String formatHologramBoundaries(String text) {
        List<String> output = new ArrayList<String>();
        for (String line : VisualTextBoundaries.splitLines(text)) {
            List<String> segments = VisualTextBoundaries.splitBracketSegments(line);
            if (segments.size() > 1) {
                output.addAll(segments);
            } else {
                output.add(line);
            }
        }
        return joinWithNewlines(output);
    }

    public void setProtectedLiteralsSupplier(Supplier<? extends Iterable<String>> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier cannot be null");
        }
        this.protectedLiterals = supplier;
        this.dynamicProtectedLiterals = true;
    }

    /** 最近失败记录 */
    public String lastFailureStatus() {
        return lastFailureStatus;
    }

    private static String safeFailureStatus(Throwable error, TranslationResult result) {
        String message = result == null ? null : result.getErrorMessage();
        if ((message == null || message.trim().isEmpty()) && error != null) {
            message = error.getMessage();
        }
        if (message == null || message.trim().isEmpty()) {
            message = "未知错误";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() > 120) {
            singleLine = singleLine.substring(0, 117) + "...";
        }
        return "翻译失败：" + singleLine;
    }

    public synchronized void clearRenderedTranslations() {
        clearRenderedState();
        coordinator.clearCache();
    }

    private void clearRenderedState() {
        translated.clear();
        translatedOutputs.clear();
        pending.clear();
        pendingBudget.reset();
        retryAfter.clear();
        visualLineGrouper.clear();
        lastFailureStatus = "";
        backgroundSubmissions.reset();
        prioritySubmissions.reset();
        chatSubmissions.reset();
        systemMessageSubmissions.reset();
        urgentSubmissions.reset();
    }

    public synchronized int importCache(java.nio.file.Path source) throws java.io.IOException {
        int imported = TranslationCacheOperations.importInto(coordinator.cacheStore(), source);
        clearRenderedState();
        return imported;
    }

    public synchronized java.nio.file.Path exportCache(java.nio.file.Path target) throws java.io.IOException {
        return TranslationCacheOperations.exportFrom(coordinator.cacheStore(), target);
    }

    public synchronized void clearCacheFile() throws java.io.IOException {
        TranslationCacheOperations.clear(coordinator.cacheStore());
        clearRenderedState();
    }

    private synchronized void clearMemoryOnClose() {
        translated.clear();
        translatedOutputs.clear();
        pending.clear();
        pendingBudget.reset();
        retryAfter.clear();
        visualLineGrouper.clear();
        lastFailureStatus = "";
        backgroundSubmissions.reset();
        prioritySubmissions.reset();
        chatSubmissions.reset();
        systemMessageSubmissions.reset();
        urgentSubmissions.reset();
    }

    private SubmissionWindow submissionWindow(TextKind kind) {
        switch (kind) {
            case CHAT:
                return chatSubmissions;
            case SYSTEM_MESSAGE:
                return systemMessageSubmissions;
            case TITLE:
            case SUBTITLE:
            case ACTION_BAR:
            case BOSS_BAR:
            case TOAST:
                return urgentSubmissions;
            case SCOREBOARD_TITLE:
            case SCOREBOARD_LINE:
            case PLAYER_LIST_HEADER:
            case PLAYER_LIST_FOOTER:
            case CONTAINER_TITLE:
            case ITEM_NAME:
            case ITEM_LORE:
            case TOOLTIP:
            case SIGN:
            case BOOK:
            case DISCONNECT_REASON:
                return prioritySubmissions;
            default:
                return backgroundSubmissions;
        }
    }

    private static final class SubmissionWindow {
        private final int maximum;
        private long windowStartedAt;
        private int used;

        private SubmissionWindow(int maximum) {
            this.maximum = maximum;
        }

        private synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            if (windowStartedAt == 0L || now - windowStartedAt >= TimeUnit.SECONDS.toNanos(1L)) {
                windowStartedAt = now;
                used = 0;
            }
            if (used >= maximum) {
                return false;
            }
            used++;
            return true;
        }

        private synchronized void reset() {
            windowStartedAt = 0L;
            used = 0;
        }
    }

    private enum PendingKind {
        BACKGROUND,
        FOREGROUND,
        CHAT,
        SYSTEM_MESSAGE
    }

    @Override
    public synchronized void close() {
        if (resourcesClosed) {
            return;
        }
        closed = true;
        resourcesClosed = true;
        coordinator.close();
        clearMemoryOnClose();
    }

    /** 立即停用，资源由后台释放。 */
    public void deactivate() {
        closed = true;
    }

    private static final class RenderKey {
        private final String text;
        private final TextKind kind;

        private RenderKey(String text, TextKind kind) {
            this.text = text;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RenderKey)) {
                return false;
            }
            RenderKey key = (RenderKey) other;
            return text.equals(key.text) && kind == key.kind;
        }

        @Override
        public int hashCode() {
            return 31 * text.hashCode() + kind.hashCode();
        }
    }
}

package org.universaltranslator.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/**
 * Non-blocking lookup facade for render hooks. The first frame returns the original;
 * completed translations are substituted on later frames.
 */
public final class RenderTranslationSession implements AutoCloseable {
    private static final long FAILURE_RETRY_MILLIS = 30_000L;
    private static final int MAX_PENDING_TRANSLATIONS = 128;
    private static final int MAX_RENDERED_TRANSLATIONS = 4_096;
    private static final int MAX_FAILED_TRANSLATIONS = 1_024;
    private static final int MAX_BACKGROUND_SUBMISSIONS_PER_SECOND = 4;
    private static final int MAX_PRIORITY_SUBMISSIONS_PER_SECOND = 16;
    private static final int MAX_URGENT_SUBMISSIONS_PER_SECOND = 30;
    private static final long VISUAL_GROUP_WINDOW_MILLIS = 80L;
    private static final int MAX_VISUAL_GROUP_LINES = 8;
    private static final int MAX_VISUAL_GROUP_TEXT_LENGTH = 420;

    private final TranslationCoordinator coordinator;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final TranslationDisplayMode displayMode;
    private final boolean preserveHanText;
    private final ConcurrentHashMap<RenderKey, String> translated = new ConcurrentHashMap<RenderKey, String>();
    private final ConcurrentHashMap<String, Boolean> translatedOutputs =
            new ConcurrentHashMap<String, Boolean>();
    private final ConcurrentHashMap<RenderKey, Boolean> pending = new ConcurrentHashMap<RenderKey, Boolean>();
    private final ConcurrentHashMap<RenderKey, Long> retryAfter = new ConcurrentHashMap<RenderKey, Long>();
    private final SubmissionWindow backgroundSubmissions =
            new SubmissionWindow(MAX_BACKGROUND_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow prioritySubmissions =
            new SubmissionWindow(MAX_PRIORITY_SUBMISSIONS_PER_SECOND);
    private final SubmissionWindow urgentSubmissions =
            new SubmissionWindow(MAX_URGENT_SUBMISSIONS_PER_SECOND);
    private final VisualLineGrouper visualLineGrouper = new VisualLineGrouper();
    private volatile boolean closed;
    private volatile String lastFailureStatus = "";
    private volatile BiConsumer<TextKind, String> urgentCompletionListener;
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
        if (closed || original == null || original.isEmpty()) {
            return original;
        }
        // A GUI text can pass through both a high-level draw hook and TextRenderer.
        // Never submit our own completed output for translation a second time.
        if (isCompletedOutput(original)) {
            return original;
        }
        // Avoid building player-name snapshots for text that is already in the
        // target language or contains no words worth translating.
        if (!LanguageHeuristics.shouldTranslate(original, targetLanguage)) {
            return original;
        }
        TextKind effectiveKind = kind == null ? TextKind.OTHER : kind;
        VisualLineGroup group = visualLineGrouper.group(original, effectiveKind);
        if (group != null) {
            String grouped = lookupDirect(group.text, effectiveKind);
            if (!group.text.equals(grouped)) {
                return group.lineResult(grouped);
            }
        }
        return lookupDirect(original, effectiveKind);
    }

    private String lookupDirect(String original, TextKind effectiveKind) {
        RenderKey key = new RenderKey(original, effectiveKind);
        String ready = translated.get(key);
        if (ready != null) {
            return ready;
        }

        TranslationResult cached = coordinator.cachedTranslation(
                original, sourceLanguage, targetLanguage, effectiveKind,
                Collections.<String>emptyList(), preserveHanText);
        if (cached != null) {
            completeLookup(key, original, cached, null);
            return renderResult(original, cached);
        }

        Long retryAt = retryAfter.get(key);
        long now = System.currentTimeMillis();
        if (retryAt != null && retryAt.longValue() > now) {
            return original;
        }
        // A busy multiplayer lobby can expose thousands of rapidly changing
        // strings in a few frames. Drop excess render-time work and try again on
        // a later frame instead of growing an unbounded queue and freezing MC.
        if (pending.size() >= MAX_PENDING_TRANSLATIONS) {
            return original;
        }
        if (pending.containsKey(key)) {
            return original;
        }
        // A global font hook can see hundreds of unique labels per second in a lobby.
        // Keep the local model from running at 100% continuously. Interactive and HUD
        // surfaces use a separate allowance so tooltips and chat are not starved by
        // world-space labels.
        if (!submissionWindow(effectiveKind).tryAcquire()) {
            return original;
        }
        if (pending.putIfAbsent(key, Boolean.TRUE) == null) {
            CompletableFuture<TranslationResult> future = coordinator.translate(
                    original, sourceLanguage, targetLanguage, effectiveKind,
                    snapshotProtectedLiterals(), preserveHanText);
            future.whenComplete((result, error) -> completeLookup(
                    key, original, result, error));
        }
        return original;
    }

    /**
     * Translates related visual lines as one semantic text. Minecraft often wraps one
     * sentence into multiple render lines (chat, signs, books, tooltips); those wraps
     * must not become translation boundaries.
     */
    public List<String> lookupLines(List<String> originals, TextKind kind) {
        if (originals == null || originals.isEmpty()) {
            return originals;
        }
        String originalText = joinVisualLines(originals);
        if (originalText.isEmpty()) {
            return originals;
        }
        String translatedText = lookup(originalText, kind);
        if (originalText.equals(translatedText)) {
            return originals;
        }
        return distributeVisualLines(originals, translatedText);
    }

    private static String joinVisualLines(List<String> lines) {
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String normalized = line.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (joined.length() > 0 && needsSpace(joined.charAt(joined.length() - 1), normalized.charAt(0))) {
                joined.append(' ');
            }
            joined.append(normalized);
        }
        return joined.toString();
    }

    private static boolean needsSpace(char before, char after) {
        if (Character.isWhitespace(before) || Character.isWhitespace(after)) {
            return false;
        }
        return isAsciiWord(before) && isAsciiWord(after);
    }

    private static boolean isAsciiWord(char value) {
        return value < 128 && Character.isLetterOrDigit(value);
    }

    private static List<String> distributeVisualLines(List<String> originals, String translatedText) {
        List<String> normalizedTranslated = normalizeTranslatedLines(translatedText);
        if (normalizedTranslated.size() == originals.size()) {
            return normalizedTranslated;
        }
        List<String> replacement = new ArrayList<String>(originals.size());
        boolean placed = false;
        String compactTranslated = translatedText == null ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
        for (String original : originals) {
            if (original == null || original.trim().isEmpty()) {
                replacement.add(original);
                continue;
            }
            if (!placed) {
                replacement.add(compactTranslated);
                placed = true;
            } else {
                replacement.add("");
            }
        }
        return replacement;
    }

    private static List<String> normalizeTranslatedLines(String translatedText) {
        List<String> lines = new ArrayList<String>();
        if (translatedText == null) {
            lines.add("");
            return lines;
        }
        String[] split = translatedText.split("\\R", -1);
        for (String line : split) {
            lines.add(line.trim());
        }
        return lines;
    }

    public void setUrgentCompletionListener(
            BiConsumer<TextKind, String> urgentCompletionListener
    ) {
        this.urgentCompletionListener = urgentCompletionListener;
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
        return coordinator.translate(
                original,
                sourceLanguage,
                target,
                kind == null ? TextKind.CHAT : kind,
                literals,
                preserveHanText);
    }

    private Iterable<String> snapshotProtectedLiterals() {
        try {
            return protectedLiterals.get();
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
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
        pending.remove(key);
        if (closed) {
            return;
        }
        if (error != null || result == null || result.isFailure()) {
            if (retryAfter.size() >= MAX_FAILED_TRANSLATIONS) {
                retryAfter.clear();
            }
            retryAfter.put(key, System.currentTimeMillis() + FAILURE_RETRY_MILLIS);
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
            String output = formatOutput(original, result.getTranslatedText());
            translated.put(key, output);
            translatedOutputs.put(output, Boolean.TRUE);
            translatedOutputs.put(
                    TranslationTextStyling.stripLegacyFormatting(output), Boolean.TRUE);
            notifyUrgentCompletion(key.kind, output);
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
            // Platform UI callbacks are best-effort; never break render caching.
        }
    }

    private boolean isCompletedOutput(String text) {
        if (translatedOutputs.containsKey(text)) {
            return true;
        }
        String unformatted = TranslationTextStyling.stripLegacyFormatting(text);
        return unformatted != text && translatedOutputs.containsKey(unformatted);
    }

    private String renderResult(String original, TranslationResult result) {
        if (result == null || !result.isTranslated()) {
            return original;
        }
        return formatOutput(original, result.getTranslatedText());
    }

    private String formatOutput(String original, String translatedText) {
        if (displayMode != TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                || original.equals(translatedText)) {
            return translatedText;
        }
        return original + " \u00a78| \u00a7f" + translatedText;
    }

    public void setProtectedLiteralsSupplier(Supplier<? extends Iterable<String>> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier cannot be null");
        }
        this.protectedLiterals = supplier;
    }

    /** Latest render-time failure, cleared after the next successful request. */
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
        translated.clear();
        translatedOutputs.clear();
        pending.clear();
        retryAfter.clear();
        visualLineGrouper.clear();
        coordinator.clearCache();
        lastFailureStatus = "";
        backgroundSubmissions.reset();
        prioritySubmissions.reset();
        urgentSubmissions.reset();
    }

    private SubmissionWindow submissionWindow(TextKind kind) {
        switch (kind) {
            case TITLE:
            case SUBTITLE:
            case ACTION_BAR:
            case BOSS_BAR:
            case TOAST:
                return urgentSubmissions;
            case CHAT:
            case SYSTEM_MESSAGE:
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

    private static final class VisualLineGrouper {
        private final LinkedHashMap<TextKind, VisualLineBucket> buckets =
                new LinkedHashMap<TextKind, VisualLineBucket>();

        private synchronized VisualLineGroup group(String original, TextKind kind) {
            if (!shouldGroup(kind, original)) {
                buckets.remove(kind);
                return null;
            }
            long now = System.currentTimeMillis();
            VisualLineBucket bucket = buckets.get(kind);
            if (bucket == null || now - bucket.updatedAt > VISUAL_GROUP_WINDOW_MILLIS
                    || bucket.lines.size() >= MAX_VISUAL_GROUP_LINES) {
                bucket = new VisualLineBucket(kind);
                buckets.put(kind, bucket);
            }
            bucket.add(original, now);
            if (bucket.lines.size() < 2) {
                return null;
            }
            String joined = joinVisualLines(bucket.lines);
            if (joined.length() > MAX_VISUAL_GROUP_TEXT_LENGTH) {
                bucket.reset(original, now);
                return null;
            }
            return new VisualLineGroup(joined, original, bucket.lineIndex(original));
        }

        private synchronized void clear() {
            buckets.clear();
        }

        private static boolean shouldGroup(TextKind kind, String original) {
            if (original == null || original.indexOf('\n') >= 0 || original.indexOf('\r') >= 0) {
                return false;
            }
            String trimmed = original.trim();
            if (trimmed.length() < 2 || trimmed.length() > 140) {
                return false;
            }
            switch (kind) {
                case TITLE:
                case SUBTITLE:
                case ACTION_BAR:
                case CHAT:
                case SYSTEM_MESSAGE:
                case SCOREBOARD_TITLE:
                case SCOREBOARD_LINE:
                case PLAYER_LIST_HEADER:
                case PLAYER_LIST_FOOTER:
                case BOSS_BAR:
                case SIGN:
                case BOOK:
                case HOLOGRAM:
                case OTHER:
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class VisualLineBucket {
        private final TextKind kind;
        private final List<String> lines = new ArrayList<String>();
        private long updatedAt;

        private VisualLineBucket(TextKind kind) {
            this.kind = kind;
        }

        private void add(String original, long now) {
            if (!lines.isEmpty() && lines.get(lines.size() - 1).equals(original)) {
                updatedAt = now;
                return;
            }
            if (lines.contains(original)) {
                reset(original, now);
                return;
            }
            lines.add(original);
            updatedAt = now;
        }

        private void reset(String original, long now) {
            lines.clear();
            lines.add(original);
            updatedAt = now;
        }

        private int lineIndex(String original) {
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index).equals(original)) {
                    return index;
                }
            }
            return 0;
        }
    }

    private static final class VisualLineGroup {
        private final String text;
        private final String currentLine;
        private final int currentIndex;

        private VisualLineGroup(String text, String currentLine, int currentIndex) {
            this.text = text;
            this.currentLine = currentLine;
            this.currentIndex = currentIndex;
        }

        private String lineResult(String translatedText) {
            List<String> translatedLines = normalizeTranslatedLines(translatedText);
            if (translatedLines.size() > currentIndex) {
                String line = translatedLines.get(currentIndex);
                return line == null || line.isEmpty() ? currentLine : line;
            }
            if (currentIndex == 0) {
                String compact = translatedText == null
                        ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
                return compact.isEmpty() ? currentLine : compact;
            }
            return "";
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        coordinator.close();
        clearRenderedTranslations();
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

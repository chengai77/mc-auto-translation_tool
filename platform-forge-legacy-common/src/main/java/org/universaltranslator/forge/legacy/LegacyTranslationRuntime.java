package org.universaltranslator.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.universaltranslator.core.CoalescingUpdateQueue;
import org.universaltranslator.core.LanguageHeuristics;
import org.universaltranslator.core.OrderedDisplayQueue;
import org.universaltranslator.core.RenderTranslationSession;
import org.universaltranslator.core.PersistentTranslationCache;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationCacheOperations;
import org.universaltranslator.core.TranslationStore;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationProviderStatus;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TranslationTextStyling;
import org.universaltranslator.core.RecentUserText;
import org.universaltranslator.core.RepeatingDisplayCache;
import org.universaltranslator.core.TranslationResult;
import org.universaltranslator.core.provider.LlamaCppOfflineProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

final class LegacyTranslationRuntime {
    private static final long PLAYER_NAME_SNAPSHOT_MILLIS = 5_000L;
    // 对齐保护上限
    // 避免截断名单
    private static final int MAX_PROTECTED_PLAYER_NAMES = 1_000;

    private static volatile RenderTranslationSession session;
    private static volatile TranslationStore cacheStore;
    private static volatile boolean cacheStoreDisk;
    private static volatile Path cacheStoreFile;
    private static volatile LegacyConfig activeConfig;
    private static volatile TranslationProvider activeProvider;
    private static volatile List<String> protectedPlayerNames = Collections.emptyList();
    private static volatile long protectedPlayerNamesExpireAt;
    private static final RecentUserText RECENT_USER_TEXT = new RecentUserText();
    private static final RecentUserText LOCAL_HUD_TEXT = new RecentUserText();
    private static CompletableFuture<Void> outgoingTail = CompletableFuture.completedFuture(null);
    private static volatile boolean replayingUrgentHudText;
    private static final ConcurrentHashMap<TextKind, OrderedDisplayQueue<LegacyHudOutput>>
            URGENT_HUD_QUEUES =
            new ConcurrentHashMap<TextKind, OrderedDisplayQueue<LegacyHudOutput>>();
    private static final RepeatingDisplayCache<LegacyHudOutput> URGENT_HUD_REPEATS =
            new RepeatingDisplayCache<LegacyHudOutput>(64);
    private static final CoalescingUpdateQueue CHAT_REFRESH_QUEUE =
            new CoalescingUpdateQueue(
                    new Executor() {
                        @Override
                        public void execute(Runnable command) {
                            Minecraft.getMinecraft().addScheduledTask(command);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            LegacyVersionAccess.refreshChatNow(Minecraft.getMinecraft());
                        }
                    });

    private LegacyTranslationRuntime() {
    }

    static synchronized void initialize(LegacyConfig config) throws IOException {
        shutdown();
        activeConfig = config;
        if (config.enabled) {
            TranslationStore store = prepareCacheStore(config);
            TranslationProvider provider = config.createProvider();
            activeProvider = provider;
            int workers = provider.id().contains("offline-llama:") ? 1 : 4;
            RenderTranslationSession created = new RenderTranslationSession(
                    provider, "auto", config.targetLanguage, store, workers, config.displayMode,
                    config.translateEnglishOnly);
            created.setProtectedLiteralsSupplier(LegacyTranslationRuntime::playerNameSnapshot);
            created.setRenderCompletionListener((kind, output) -> {
                if ((kind == TextKind.CHAT || kind == TextKind.SYSTEM_MESSAGE)
                        && session == created) {
                    CHAT_REFRESH_QUEUE.request();
                }
            });
            session = created;
            CHAT_REFRESH_QUEUE.request();
        }
    }

    static String translate(String original, TextKind kind) {
        RenderTranslationSession active = session;
        LegacyConfig config = activeConfig;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (active == null || config == null || !config.allows(kind)
                || minecraft.currentScreen instanceof LegacyConfigScreen
                || minecraft.currentScreen instanceof LegacyDiagnosticsScreen
                || minecraft.currentScreen instanceof LegacyCacheScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogSourceScreen
                || LegacyLocalTextGuard.isLocalChatInput(minecraft.currentScreen, original)
                || LOCAL_HUD_TEXT.shouldPreserve(original)
                || RECENT_USER_TEXT.shouldPreserve(original)
                || LegacyVersionAccess.connection(minecraft) == null) {
            return original;
        }
        String translated = active.lookup(original, kind);
        if (shouldRecordInLog(kind)) {
            LegacyTranslationLog.add(original, displayTranslatedOnly(translated));
        }
        return translated;
    }

    static synchronized int importCache(Path source) throws IOException {
        RenderTranslationSession active = session;
        return active == null
                ? TranslationCacheOperations.importInto(requireCacheStore(), source)
                : active.importCache(source);
    }

    static LegacyConfig currentConfig() {
        return activeConfig;
    }

    static synchronized void updateConfig(LegacyConfig config) throws IOException {
        initialize(config);
    }

    static void clearTranslationHistory() {
        LegacyTranslationLog.clear();
        RenderTranslationSession active = session;
        if (active != null) {
            active.clearRenderedTranslations();
        }
    }

    static synchronized Path exportCache(Path target) throws IOException {
        RenderTranslationSession active = session;
        return active == null
                ? TranslationCacheOperations.exportFrom(requireCacheStore(), target)
                : active.exportCache(target);
    }

    static synchronized void clearCacheFile() throws IOException {
        RenderTranslationSession active = session;
        if (active == null) {
            TranslationCacheOperations.clear(requireCacheStore());
        } else {
            active.clearCacheFile();
        }
    }

    private static TranslationStore requireCacheStore() throws IOException {
        LegacyConfig config = activeConfig;
        if (config == null) {
            throw new IOException("Translation settings are not initialized");
        }
        return prepareCacheStore(config);
    }

    private static TranslationStore prepareCacheStore(LegacyConfig config) throws IOException {
        Path file = config.cacheFile.toPath().toAbsolutePath().normalize();
        if (cacheStore == null || cacheStoreDisk != config.diskCache || !file.equals(cacheStoreFile)) {
            cacheStore = config.diskCache
                    ? new PersistentTranslationCache(file, 10_000)
                    : new TranslationCache(10_000);
            cacheStoreDisk = config.diskCache;
            cacheStoreFile = file;
        }
        return cacheStore;
    }

    static synchronized void shutdown() {
        RenderTranslationSession active = session;
        session = null;
        activeProvider = null;
        protectedPlayerNames = Collections.emptyList();
        protectedPlayerNamesExpireAt = 0L;
        RECENT_USER_TEXT.clear();
        LOCAL_HUD_TEXT.clear();
        outgoingTail = CompletableFuture.completedFuture(null);
        replayingUrgentHudText = false;
        for (OrderedDisplayQueue<LegacyHudOutput> queue : URGENT_HUD_QUEUES.values()) {
            queue.clear();
        }
        URGENT_HUD_QUEUES.clear();
        URGENT_HUD_REPEATS.clear();
        if (active != null) {
            active.deactivate();
            Thread closer = new Thread(active::close, "universal-translator-shutdown");
            closer.setDaemon(true);
            closer.start();
        }
    }

    private static synchronized List<String> playerNameSnapshot() {
        long now = System.currentTimeMillis();
        if (now < protectedPlayerNamesExpireAt) {
            return protectedPlayerNames;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = LegacyVersionAccess.connection(minecraft);
        if (connection == null) {
            protectedPlayerNames = Collections.emptyList();
        } else {
            List<String> names = new ArrayList<String>();
            addProtectedLiteral(names, LegacyVersionAccess.localPlayerName(minecraft));
            addProtectedLiteral(names, LegacyVersionAccess.serverAddress(minecraft));
            for (NetworkPlayerInfo player : connection.getPlayerInfoMap()) {
                if (names.size() >= MAX_PROTECTED_PLAYER_NAMES) {
                    break;
                }
                if (player.getGameProfile() != null && player.getGameProfile().getName() != null) {
                    addProtectedLiteral(names, player.getGameProfile().getName());
                }
            }
            protectedPlayerNames = Collections.unmodifiableList(names);
        }
        protectedPlayerNamesExpireAt = now + PLAYER_NAME_SNAPSHOT_MILLIS;
        return protectedPlayerNames;
    }

    private static void addProtectedLiteral(List<String> values, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty() && normalized.length() <= 255
                && values.size() < MAX_PROTECTED_PLAYER_NAMES && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    static String status() {
        RenderTranslationSession active = session;
        TranslationProvider provider = activeProvider;
        String providerStatus = provider instanceof TranslationProviderStatus
                ? ((TranslationProviderStatus) provider).status() : "";
        // 保留离线错误
        // 避免错误跳变
        if (providerStatus.startsWith("离线翻译失败")) {
            return providerStatus;
        }
        if (active != null && !active.lastFailureStatus().isEmpty()) {
            return active.lastFailureStatus();
        }
        return providerStatus;
    }

    static TranslationDiagnosticsSnapshot diagnostics() {
        LegacyConfig config = activeConfig;
        TranslationProvider provider = activeProvider;
        if (config == null) {
            return new TranslationDiagnosticsSnapshot(
                    false, "", "", "", null, false, false, -1L, -1L, "尚未载入设置");
        }
        Path modelFile = LlamaCppOfflineProvider.modelPath(
                config.offlineDirectory.toPath(), config.offlineModel);
        return new TranslationDiagnosticsSnapshot(
                config.enabled,
                config.provider,
                provider == null ? "" : provider.id(),
                config.targetLanguage,
                config.offlineModel,
                config.offlineAutoDownload,
                config.diskCache,
                fileSize(modelFile),
                fileSize(config.cacheFile.toPath()),
                status());
    }

    private static long fileSize(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1L;
        } catch (IOException ignored) {
            return -1L;
        }
    }

    static List<String> translateLines(List<String> originals, TextKind kind) {
        RenderTranslationSession active = session;
        LegacyConfig config = activeConfig;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (active == null || config == null || !config.allows(kind)
                || minecraft.currentScreen instanceof LegacyConfigScreen
                || minecraft.currentScreen instanceof LegacyDiagnosticsScreen
                || minecraft.currentScreen instanceof LegacyCacheScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogSourceScreen
                || LegacyRenderContext.isTextInput()
                || LegacyVersionAccess.connection(minecraft) == null) {
            return originals;
        }
        List<String> translated = active.lookupLines(originals, kind);
        if (shouldRecordInLog(kind)) {
            LegacyTranslationLog.add(joinLogLines(originals), displayTranslatedOnly(joinLogLines(translated)));
        }
        return translated;
    }

    static List<String> translateIndependentLines(List<String> originals, TextKind kind) {
        RenderTranslationSession active = session;
        LegacyConfig config = activeConfig;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (active == null || config == null || !config.allows(kind)
                || minecraft.currentScreen instanceof LegacyConfigScreen
                || minecraft.currentScreen instanceof LegacyDiagnosticsScreen
                || minecraft.currentScreen instanceof LegacyCacheScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogScreen
                || minecraft.currentScreen instanceof LegacyTranslationLogSourceScreen
                || LegacyRenderContext.isTextInput()
                || LegacyVersionAccess.connection(minecraft) == null) {
            return originals;
        }
        return active.lookupIndependentLines(originals, kind);
    }

    static TranslationTextColor translatedTextColor() {
        LegacyConfig config = activeConfig;
        return config == null ? TranslationTextColor.ORIGINAL : config.translatedTextColor;
    }

    private static boolean shouldRecordInLog(TextKind kind) {
        LegacyConfig config = activeConfig;
        return config != null
                && kind != TextKind.TOOLTIP
                && kind != TextKind.ITEM_NAME
                && kind != TextKind.ITEM_LORE
                && config.logAllowedKinds.contains(kind);
    }

    private static String joinLogLines(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
            if (!normalized.isEmpty()) {
                if (joined.length() > 0) {
                    joined.append(' ');
                }
                joined.append(normalized);
            }
        }
        return joined.toString();
    }

    private static String displayTranslatedOnly(String value) {
        return value == null ? "" : value;
    }

    static boolean preloadTitle(
            String title,
            String subtitle,
            int fadeIn,
            int stay,
            int fadeOut
    ) {
        if (replayingUrgentHudText) {
            return false;
        }
        boolean titleQueued = title != null && preloadUrgentHudText(
                title, TextKind.TITLE, false, fadeIn, stay, fadeOut);
        boolean subtitleQueued = subtitle != null && preloadUrgentHudText(
                subtitle, TextKind.SUBTITLE, false, fadeIn, stay, fadeOut);
        if (subtitleQueued && title != null && !titleQueued) {
            titleQueued = queueUrgentHudFallback(
                    title, TextKind.TITLE, false, fadeIn, stay, fadeOut);
        }
        if (titleQueued && subtitle != null && !subtitleQueued) {
            subtitleQueued = queueUrgentHudFallback(
                    subtitle, TextKind.SUBTITLE, false, fadeIn, stay, fadeOut);
        }
        return titleQueued || subtitleQueued;
    }

    static boolean preloadOverlay(String text, boolean tinted) {
        return !replayingUrgentHudText && preloadUrgentHudText(
                text, TextKind.ACTION_BAR, tinted, -1, -1, -1);
    }

    static void showLocalOverlay(Minecraft minecraft, String text, boolean tinted) {
        if (minecraft == null || text == null || text.trim().isEmpty()) {
            return;
        }
        LOCAL_HUD_TEXT.remember(text);
        boolean previous = replayingUrgentHudText;
        replayingUrgentHudText = true;
        try {
            LegacyVersionAccess.displayOverlay(minecraft, text, tinted);
        } finally {
            replayingUrgentHudText = previous;
        }
    }

    private static boolean preloadUrgentHudText(
            final String original,
            final TextKind kind,
            final boolean tinted,
            final int fadeIn,
            final int stay,
            final int fadeOut
    ) {
        final RenderTranslationSession active = session;
        final LegacyConfig config = activeConfig;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (original == null || original.trim().isEmpty()
                || LOCAL_HUD_TEXT.shouldPreserve(original)
                || active == null || config == null || !config.allows(kind)
                || LegacyVersionAccess.connection(minecraft) == null
                || !LanguageHeuristics.shouldTranslate(original, config.targetLanguage)) {
            return false;
        }
        final String repeatKey = kind.name() + '\u0000' + tinted + '\u0000' + original;
        RepeatingDisplayCache.Claim<LegacyHudOutput> claim =
                URGENT_HUD_REPEATS.acquire(repeatKey);
        if (claim.displayedValue() != null) {
            replayUrgentHudText(claim.displayedValue());
            return true;
        }
        if (!claim.isClaimed()) {
            return true;
        }
        OrderedDisplayQueue<LegacyHudOutput> queue = urgentHudQueue(kind);
        final OrderedDisplayQueue.Ticket<LegacyHudOutput> ticket = queue.offer();
        if (ticket == null) {
            URGENT_HUD_REPEATS.fail(repeatKey);
            return false;
        }
        try {
            active.translateInteractive(
                            original, kind, config.targetLanguage, config.translateEnglishOnly)
                    .whenComplete((result, failure) -> {
                        if (session != active || activeConfig != config
                                || failure != null || result == null) {
                            completeUrgentHudFallback(
                                    repeatKey, ticket, original, kind, tinted,
                                    fadeIn, stay, fadeOut);
                            return;
                        }
                        String output = original;
                        if (result.isTranslated()
                                && result.getTranslatedText() != null
                                && !original.equals(result.getTranslatedText())) {
                            output = TranslationTextStyling.applyTranslatedStyle(
                                    original, result.getTranslatedText(),
                                    config.translatedTextColor);
                        }
                        LegacyHudOutput completed = new LegacyHudOutput(
                                repeatKey, kind, output, tinted, fadeIn, stay, fadeOut);
                        URGENT_HUD_REPEATS.complete(repeatKey, completed);
                        ticket.complete(completed);
                    });
        } catch (RuntimeException failure) {
            completeUrgentHudFallback(
                    repeatKey, ticket, original, kind, tinted, fadeIn, stay, fadeOut);
        }
        return true;
    }

    private static boolean queueUrgentHudFallback(
            String original,
            TextKind kind,
            boolean tinted,
            int fadeIn,
            int stay,
            int fadeOut
    ) {
        if (original == null || original.trim().isEmpty()) {
            return false;
        }
        String repeatKey = kind.name() + '\u0000' + tinted + '\u0000' + original;
        OrderedDisplayQueue<LegacyHudOutput> queue = urgentHudQueue(kind);
        OrderedDisplayQueue.Ticket<LegacyHudOutput> ticket = queue.offer();
        if (ticket == null) {
            return false;
        }
        LegacyHudOutput fallback = new LegacyHudOutput(
                repeatKey, kind, original, tinted, fadeIn, stay, fadeOut);
        ticket.complete(fallback);
        return true;
    }

    private static void completeUrgentHudFallback(
            String repeatKey,
            OrderedDisplayQueue.Ticket<LegacyHudOutput> ticket,
            String original,
            TextKind kind,
            boolean tinted,
            int fadeIn,
            int stay,
            int fadeOut
    ) {
        URGENT_HUD_REPEATS.fail(repeatKey);
        ticket.complete(new LegacyHudOutput(
                repeatKey, kind, original, tinted, fadeIn, stay, fadeOut));
    }

    private static OrderedDisplayQueue<LegacyHudOutput> urgentHudQueue(TextKind kind) {
        OrderedDisplayQueue<LegacyHudOutput> queue = URGENT_HUD_QUEUES.get(kind);
        if (queue == null) {
            OrderedDisplayQueue<LegacyHudOutput> created =
                    new OrderedDisplayQueue<LegacyHudOutput>(
                            8, kind == TextKind.TITLE || kind == TextKind.SUBTITLE ? 60 : 10);
            queue = URGENT_HUD_QUEUES.putIfAbsent(kind, created);
            if (queue == null) {
                queue = created;
            }
        }
        return queue;
    }

    static void tickUrgentHudText() {
        for (TextKind kind : new TextKind[] {
                TextKind.TITLE, TextKind.SUBTITLE, TextKind.ACTION_BAR }) {
            LegacyHudOutput output = urgentHudQueue(kind).tick();
            if (output != null) {
                URGENT_HUD_REPEATS.markDisplayed(output.repeatKey);
                replayUrgentHudText(output);
            }
        }
    }

    private static void replayUrgentHudText(LegacyHudOutput output) {
        replayingUrgentHudText = true;
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (output.kind == TextKind.TITLE) {
                LegacyVersionAccess.displayTitle(
                        minecraft, output.text, null,
                        output.fadeIn, output.stay, output.fadeOut);
            } else if (output.kind == TextKind.SUBTITLE) {
                LegacyVersionAccess.displayTitle(
                        minecraft, null, output.text,
                        output.fadeIn, output.stay, output.fadeOut);
            } else {
                LegacyVersionAccess.displayOverlay(
                        minecraft, output.text, output.tinted);
            }
        } finally {
            replayingUrgentHudText = false;
        }
    }

    private static final class LegacyHudOutput {
        private final String repeatKey;
        private final TextKind kind;
        private final String text;
        private final boolean tinted;
        private final int fadeIn;
        private final int stay;
        private final int fadeOut;

        private LegacyHudOutput(
                String repeatKey,
                TextKind kind,
                String text,
                boolean tinted,
                int fadeIn,
                int stay,
                int fadeOut
        ) {
            this.repeatKey = repeatKey;
            this.kind = kind;
            this.text = text;
            this.tinted = tinted;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }
    }

    static void protectOutgoingMessage(String message) {
        RECENT_USER_TEXT.remember(message);
    }

    static boolean shouldTranslateOutgoing(String message) {
        LegacyConfig config = activeConfig;
        return session != null && config != null && config.enabled && config.translateOutgoing
                && message != null && !message.trim().isEmpty() && !message.startsWith("/");
    }

    static boolean shouldCompleteOutgoing() {
        LegacyConfig config = activeConfig;
        return session != null && config != null && config.enabled;
    }

    /** 发送顺序序列化 */
    static synchronized CompletableFuture<TranslationResult> translateOutgoing(String message) {
        final RenderTranslationSession active = session;
        final LegacyConfig config = activeConfig;
        if (active == null || config == null || !shouldTranslateOutgoing(message)) {
            return CompletableFuture.completedFuture(TranslationResult.unchanged(message));
        }
        // 主线程取字面
        // 工作线程不查网络
        CompletableFuture<TranslationResult> translated = active.translateInteractive(
                message, TextKind.CHAT, config.outgoingTargetLanguage, false);
        CompletableFuture<TranslationResult> next = outgoingTail
                .handle((ignored, failure) -> null)
                .thenCompose(ignored -> translated);
        outgoingTail = next.handle((ignored, failure) -> null);
        return next;
    }
}

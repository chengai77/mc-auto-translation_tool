package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.universaltranslator.core.CoalescingUpdateQueue;
import org.universaltranslator.core.RenderTranslationSession;
import org.universaltranslator.core.PersistentTranslationCache;
import org.universaltranslator.core.ProtectedLiteralsSnapshot;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationCacheOperations;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationProviderStatus;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;
import org.universaltranslator.core.TranslationStore;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TranslationDisplayText;
import org.universaltranslator.core.RecentUserText;
import org.universaltranslator.core.RepeatingDisplayCache;
import org.universaltranslator.core.StyledTranslationTemplate;
import org.universaltranslator.core.provider.LlamaCppOfflineProvider;
import org.universaltranslator.core.TranslationResult;
import org.universaltranslator.core.OrderedDisplayQueue;
import org.universaltranslator.fabric.mixin.ChatHudAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class FabricTranslationRuntime {
    private static final long PLAYER_NAME_SNAPSHOT_MILLIS = 5_000L;
    // 对齐保护上限
    // 避免截断名单
    private static final int MAX_PROTECTED_PLAYER_NAMES = 1_000;

    private static volatile RenderTranslationSession session;
    private static volatile TranslationStore cacheStore;
    private static volatile boolean cacheStoreDisk;
    private static volatile Path cacheStoreFile;
    private static volatile FabricConfig activeConfig;
    private static volatile TranslationProvider activeProvider;
    private static volatile ProtectedLiteralsSnapshot protectedPlayerNames =
            ProtectedLiteralsSnapshot.empty();
    private static volatile long protectedPlayerNamesExpireAt;
    private static final RecentUserText RECENT_USER_TEXT = new RecentUserText();
    private static CompletableFuture<Void> outgoingTail = CompletableFuture.completedFuture(null);
    private static volatile boolean replayingUrgentHudText;
    private static final ConcurrentHashMap<TextKind, OrderedDisplayQueue<UrgentHudOutput>> URGENT_HUD_QUEUES =
            new ConcurrentHashMap<TextKind, OrderedDisplayQueue<UrgentHudOutput>>();
    private static final RepeatingDisplayCache<UrgentHudOutput> URGENT_HUD_REPEATS =
            new RepeatingDisplayCache<UrgentHudOutput>(64);
    private static final CoalescingUpdateQueue CHAT_REFRESH_QUEUE =
            new CoalescingUpdateQueue(
                    command -> MinecraftClient.getInstance().execute(command),
                    FabricTranslationRuntime::refreshChat);

    private FabricTranslationRuntime() {
    }

    static synchronized void initialize(FabricConfig config) throws IOException {
        shutdown();
        activeConfig = config;
        if (!config.enabled) {
            return;
        }
        TranslationProvider provider = config.createProvider();
        activeProvider = provider;
        TranslationStore store = prepareCacheStore(config);
        int workers = provider.id().contains("offline-llama:") ? 1 : 4;
        RenderTranslationSession created = new RenderTranslationSession(
                provider, "auto", config.targetLanguage, store, workers, config.displayMode,
                config.translateEnglishOnly);
        created.setProtectedLiteralsSupplier(FabricTranslationRuntime::playerNameSnapshot);
        created.setRenderCompletionListener((kind, output) -> {
            if (kind != TextKind.CHAT && kind != TextKind.SYSTEM_MESSAGE) {
                return;
            }
            if (session == created) {
                CHAT_REFRESH_QUEUE.request();
            }
        });
        session = created;
        CHAT_REFRESH_QUEUE.request();
    }

    private static void refreshChat() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) {
            return;
        }
        net.minecraft.client.gui.hud.ChatHud chat = client.inGameHud.getChatHud();
        ChatHudAccessor state = (ChatHudAccessor) chat;
        int oldLines = state.universalTranslator$getVisibleMessages().size();
        int scrolled = state.universalTranslator$getScrolledLines();
        boolean unread = state.universalTranslator$getUnread();
        chat.reset();
        int newLines = state.universalTranslator$getVisibleMessages().size();
        int restoredScroll = scrolled <= 0 ? 0 : Math.max(0, scrolled + newLines - oldLines);
        state.universalTranslator$setScrolledLines(restoredScroll);
        state.universalTranslator$setUnread(unread);
    }

    static FabricConfig currentConfig() {
        return activeConfig;
    }

    static synchronized void updateConfig(FabricConfig config) throws IOException {
        initialize(config);
    }

    static String translateForRender(String original, TextKind kind) {
        return translateForRender(original, original, kind, false, false);
    }

    static String translateCompleteForRender(String original, TextKind kind) {
        return translateForRender(original, original, kind, true, false);
    }

    static String translateCompleteForRender(
            String request, String visibleOriginal, TextKind kind) {
        return translateForRender(request, visibleOriginal, kind, true, false);
    }

    static String translatePlainHologramForRender(
            String request, String visibleOriginal) {
        return translateForRender(
                request, visibleOriginal, TextKind.HOLOGRAM, false, true);
    }

    private static String translateForRender(
            String original,
            String visibleOriginal,
            TextKind kind,
            boolean completeText,
            boolean plainHologram) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        String guardText = visibleOriginal == null ? original : visibleOriginal;
        if (active == null || config == null || !config.allows(kind)
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorProviderScreen
                || client.currentScreen instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof UniversalTranslatorCacheScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || FabricLocalTextGuard.isLocalChatInput(client, guardText)
                || RECENT_USER_TEXT.shouldPreserve(guardText)
                || client.world == null || client.getNetworkHandler() == null) {
            return original;
        }
        String translated = plainHologram
                ? active.lookupPlainHologram(original)
                : completeText
                ? active.lookupComplete(original, kind) : active.lookup(original, kind);
        if (shouldRecordInLog(kind)) {
            TranslationLog.add(guardText,
                    StyledTranslationTemplate.strip(displayTranslatedOnly(translated)));
        }
        return translated;
    }

    static String translateChatStyleFragmentForRender(String original) {
        return translateStyleFragmentForRender(original, TextKind.CHAT);
    }

    static String translateStyleFragmentForRender(String original, TextKind kind) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextKind effectiveKind = kind == null ? TextKind.CHAT : kind;
        if (active == null || config == null || !config.allows(effectiveKind)
                || original == null || original.trim().isEmpty() || original.length() > 80
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorProviderScreen
                || client.currentScreen instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof UniversalTranslatorCacheScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || client.world == null || client.getNetworkHandler() == null) {
            return original;
        }
        return displayTranslatedOnly(active.lookup(original.trim(), effectiveKind));
    }

    static boolean preloadUrgentHudText(Text text, TextKind kind, boolean overlayTinted) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isUrgentHudKind(kind) || text == null || replayingUrgentHudText
                || active == null || config == null
                || !config.allows(kind)
                || client.world == null || client.getNetworkHandler() == null) {
            return false;
        }
        final String original = text.getString();
        if (original == null || original.trim().isEmpty()
                || RECENT_USER_TEXT.shouldPreserve(original)) {
            return false;
        }
        final String repeatKey = urgentHudKey(text, kind, overlayTinted);
        RepeatingDisplayCache.Claim<UrgentHudOutput> claim =
                URGENT_HUD_REPEATS.acquire(repeatKey);
        if (claim.displayedValue() != null) {
            UrgentHudOutput displayed = claim.displayedValue();
            replayUrgentHudText(kind, displayed.text, displayed.tinted);
            return true;
        }
        if (!claim.isClaimed()) {
            return true;
        }
        OrderedDisplayQueue<UrgentHudOutput> queue = urgentHudQueue(kind);
        final OrderedDisplayQueue.Ticket<UrgentHudOutput> ticket = queue.offer();
        if (ticket == null) {
            URGENT_HUD_REPEATS.fail(repeatKey);
            return false;
        }
        active.translateInteractive(original, kind, config.targetLanguage, config.translateEnglishOnly)
                .whenComplete((result, failure) -> {
                    if (failure != null || session != active
                            || activeConfig != config || result == null) {
                        URGENT_HUD_REPEATS.fail(repeatKey);
                        ticket.complete(null);
                        return;
                    }
                    String translated = result.getTranslatedText();
                    Text styled = result.isTranslated() && translated != null
                            && !translated.equals(original)
                            ? BookTextStyler.rebuild(text, translated, text.getStyle(), kind) : text;
                    final Text output = styled == null
                            ? (result.isTranslated() && translated != null
                            ? Text.literal(translated).setStyle(text.getStyle()) : text) : styled;
                    UrgentHudOutput completed =
                            new UrgentHudOutput(repeatKey, output, overlayTinted);
                    URGENT_HUD_REPEATS.complete(repeatKey, completed);
                    ticket.complete(completed);
                });
        return true;
    }

    private static String urgentHudKey(Text text, TextKind kind, boolean tinted) {
        return kind.name() + '\u0000' + tinted + '\u0000' + text;
    }

    private static OrderedDisplayQueue<UrgentHudOutput> urgentHudQueue(TextKind kind) {
        OrderedDisplayQueue<UrgentHudOutput> queue = URGENT_HUD_QUEUES.get(kind);
        if (queue == null) {
            OrderedDisplayQueue<UrgentHudOutput> created = new OrderedDisplayQueue<UrgentHudOutput>(8,
                    kind == TextKind.TITLE || kind == TextKind.SUBTITLE ? 60 : 10);
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
            UrgentHudOutput output = urgentHudQueue(kind).tick();
            if (output != null) {
                URGENT_HUD_REPEATS.markDisplayed(output.repeatKey);
                replayUrgentHudText(kind, output.text, output.tinted);
            }
        }
    }

    private static final class UrgentHudOutput {
        private final String repeatKey;
        private final Text text;
        private final boolean tinted;

        private UrgentHudOutput(String repeatKey, Text text, boolean tinted) {
            this.repeatKey = repeatKey;
            this.text = text;
            this.tinted = tinted;
        }
    }

    private static void replayUrgentHudText(TextKind kind, Text translated, boolean overlayTinted) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.inGameHud == null) {
            return;
        }
        replayingUrgentHudText = true;
        try {
            if (kind == TextKind.ACTION_BAR || kind == TextKind.ITEM_NAME) {
                client.inGameHud.setOverlayMessage(translated, overlayTinted);
            } else if (kind == TextKind.SUBTITLE) {
                client.inGameHud.setSubtitle(translated);
                client.inGameHud.setTitleTicks(0, 60, 10);
            } else if (kind == TextKind.TITLE) {
                client.inGameHud.setTitle(translated);
                client.inGameHud.setTitleTicks(0, 60, 10);
            }
        } finally {
            replayingUrgentHudText = false;
        }
    }

    private static boolean isUrgentHudKind(TextKind kind) {
        return kind == TextKind.ACTION_BAR
                || kind == TextKind.ITEM_NAME
                || kind == TextKind.SUBTITLE
                || kind == TextKind.TITLE;
    }

    static void clearTranslationHistory() {
        TranslationLog.clear();
        HologramTextDisplayGroups.clear();
        RenderTranslationSession active = session;
        if (active != null) {
            active.clearRenderedTranslations();
        }
    }

    static synchronized int importCache(Path source) throws IOException {
        RenderTranslationSession active = session;
        return active == null
                ? TranslationCacheOperations.importInto(requireCacheStore(), source)
                : active.importCache(source);
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
        FabricConfig config = activeConfig;
        if (config == null) {
            throw new IOException("Translation settings are not initialized");
        }
        return prepareCacheStore(config);
    }

    private static TranslationStore prepareCacheStore(FabricConfig config) throws IOException {
        Path file = config.cacheFile.toAbsolutePath().normalize();
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
        URGENT_HUD_QUEUES.clear();
        URGENT_HUD_REPEATS.clear();
        HologramTextDisplayGroups.clear();
        activeProvider = null;
        protectedPlayerNames = ProtectedLiteralsSnapshot.empty();
        protectedPlayerNamesExpireAt = 0L;
        RECENT_USER_TEXT.clear();
        outgoingTail = CompletableFuture.completedFuture(null);
        if (active != null) {
            active.deactivate();
            Thread closer = new Thread(active::close, "universal-translator-shutdown");
            closer.setDaemon(true);
            closer.start();
        }
    }

    private static synchronized ProtectedLiteralsSnapshot playerNameSnapshot() {
        long now = System.currentTimeMillis();
        if (now < protectedPlayerNamesExpireAt) {
            return protectedPlayerNames;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            protectedPlayerNames = ProtectedLiteralsSnapshot.empty();
        } else {
            List<String> names = new ArrayList<String>();
            addProtectedLiteral(names, client.getSession().getUsername());
            if (client.getCurrentServerEntry() != null) {
                addProtectedLiteral(names, client.getCurrentServerEntry().address);
            }
            client.getNetworkHandler().getPlayerList().forEach(entry -> {
                if (names.size() >= MAX_PROTECTED_PLAYER_NAMES) {
                    return;
                }
                String name = entry.getProfile().getName();
                addProtectedLiteral(names, name);
            });
            protectedPlayerNames = ProtectedLiteralsSnapshot.of(names);
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
        // 优先离线诊断
        // 避免诊断跳变
        if (providerStatus.startsWith("离线翻译失败")) {
            return providerStatus;
        }
        if (active != null && !active.lastFailureStatus().isEmpty()) {
            return active.lastFailureStatus();
        }
        return providerStatus;
    }

    static TranslationDiagnosticsSnapshot diagnostics() {
        FabricConfig config = activeConfig;
        TranslationProvider provider = activeProvider;
        if (config == null) {
            return new TranslationDiagnosticsSnapshot(
                    false, "", "", "", null, false, false, -1L, -1L, "尚未载入设置");
        }
        Path modelFile = LlamaCppOfflineProvider.modelPath(
                config.offlineDirectory, config.offlineModel);
        return new TranslationDiagnosticsSnapshot(
                config.enabled,
                config.provider,
                provider == null ? "" : provider.id(),
                config.targetLanguage,
                config.offlineModel,
                config.offlineAutoDownload,
                config.diskCache,
                fileSize(modelFile),
                fileSize(config.cacheFile),
                status());
    }

    private static long fileSize(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1L;
        } catch (IOException ignored) {
            return -1L;
        }
    }

    static List<String> translateLinesForRender(List<String> originals, TextKind kind) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorProviderScreen
                || client.currentScreen instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof UniversalTranslatorCacheScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || TranslationRenderContext.isTextInput()
                || client.world == null || client.getNetworkHandler() == null) {
            return originals;
        }
        List<String> translated = active.lookupLines(originals, kind);
        if (shouldRecordInLog(kind)) {
            TranslationLog.add(joinLogLines(originals), displayTranslatedOnly(joinLogLines(translated)));
        }
        return translated;
    }

    static List<String> translateIndependentLinesForRender(List<String> originals, TextKind kind) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorProviderScreen
                || client.currentScreen instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof UniversalTranslatorCacheScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || TranslationRenderContext.isTextInput()
                || client.world == null || client.getNetworkHandler() == null) {
            return originals;
        }
        return active.lookupWrappedLines(originals, kind);
    }

    private static boolean shouldRecordInLog(TextKind kind) {
        FabricConfig config = activeConfig;
        if (config == null
                || kind == TextKind.TOOLTIP
                || kind == TextKind.ITEM_NAME
                || kind == TextKind.ITEM_LORE) {
            return false;
        }
        return config.logAllowedKinds.contains(kind);
    }

    private static String joinLogLines(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(normalized);
        }
        return joined.toString();
    }

    static TranslationTextColor translatedTextColor() {
        FabricConfig config = activeConfig;
        return config == null ? TranslationTextColor.ORIGINAL : config.translatedTextColor;
    }

    private static String displayTranslatedOnly(String value) {
        if (value == null) {
            return "";
        }
        FabricConfig config = activeConfig;
        return TranslationDisplayText.translatedOnly(
                value, config == null ? null : config.displayMode);
    }

    static void protectOutgoingMessage(String message) {
        RECENT_USER_TEXT.remember(message);
    }

    static boolean shouldTranslateOutgoing(String message) {
        FabricConfig config = activeConfig;
        return session != null && config != null && config.enabled && config.translateOutgoing
                && message != null && !message.trim().isEmpty() && !message.startsWith("/");
    }

    /** 发送顺序序列化 */
    static synchronized CompletableFuture<TranslationResult> translateOutgoing(String message) {
        final RenderTranslationSession active = session;
        final FabricConfig config = activeConfig;
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

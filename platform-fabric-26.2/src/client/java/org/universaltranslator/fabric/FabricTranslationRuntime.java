package org.universaltranslator.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.RenderTranslationSession;
import org.universaltranslator.core.PersistentTranslationCache;
import org.universaltranslator.core.ProtectedLiteralsSnapshot;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationCacheOperations;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationProviderStatus;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;
import org.universaltranslator.core.TranslationResult;
import org.universaltranslator.core.TranslationStore;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TranslationDisplayText;
import org.universaltranslator.core.RecentUserText;
import org.universaltranslator.core.StyledTranslationTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private static boolean replayingUrgentHudText;
    private static CompletableFuture<Void> outgoingTail = CompletableFuture.completedFuture(null);

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
            Minecraft.getInstance().execute(
                    () -> Minecraft.getInstance().gui.hud.getChat().rescaleChat());
        });
        session = created;
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
        Minecraft client = Minecraft.getInstance();
        String guardText = visibleOriginal == null ? original : visibleOriginal;
        if (active == null || config == null || !config.allows(kind)
                || client.gui.screen() instanceof UniversalTranslatorConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorProviderScreen
                || client.gui.screen() instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorDiagnosticsScreen
                || client.gui.screen() instanceof UniversalTranslatorCacheScreen
                || client.gui.screen() instanceof UniversalTranslatorLlmConfigScreen
                || client.gui.screen() instanceof TranslationLogScreen
                || client.gui.screen() instanceof TranslationLogSourceScreen
                || FabricLocalTextGuard.isLocalChatInput(client, guardText)
                || RECENT_USER_TEXT.shouldPreserve(guardText)
                || client.level == null || client.getConnection() == null) {
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
        Minecraft client = Minecraft.getInstance();
        TextKind effectiveKind = kind == null ? TextKind.CHAT : kind;
        if (active == null || config == null || !config.allows(effectiveKind)
                || original == null || original.trim().isEmpty() || original.length() > 80
                || client.gui.screen() instanceof UniversalTranslatorConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorProviderScreen
                || client.gui.screen() instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorDiagnosticsScreen
                || client.gui.screen() instanceof UniversalTranslatorCacheScreen
                || client.gui.screen() instanceof UniversalTranslatorLlmConfigScreen
                || client.gui.screen() instanceof TranslationLogScreen
                || client.gui.screen() instanceof TranslationLogSourceScreen
                || client.level == null || client.getConnection() == null) {
            return original;
        }
        return displayTranslatedOnly(active.lookup(original.trim(), effectiveKind));
    }

    static void preloadUrgentHudText(Component text, TextKind kind, boolean overlayTinted) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        Minecraft client = Minecraft.getInstance();
        if (!isUrgentHudKind(kind) || text == null || replayingUrgentHudText
                || active == null || config == null
                || !config.allows(kind)
                || client.level == null || client.getConnection() == null) {
            return;
        }
        final String original = text.getString();
        if (original == null || original.trim().isEmpty()
                || RECENT_USER_TEXT.shouldPreserve(original)) {
            return;
        }
        active.translateInteractive(original, kind, config.targetLanguage, config.translateEnglishOnly)
                .thenAccept(result -> {
                    if (result == null || !result.isTranslated()) {
                        return;
                    }
                    String translated = result.getTranslatedText();
                    if (translated == null || translated.equals(original)) {
                        return;
                    }
                    Component styled = BookTextStyler.rebuild(text, translated, text.getStyle(), kind);
                    final Component output = styled == null
                            ? Component.literal(translated).setStyle(text.getStyle()) : styled;
                    client.execute(() -> replayUrgentHudText(kind, output, overlayTinted));
                });
    }

    private static void replayUrgentHudText(TextKind kind, Component translated, boolean overlayTinted) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gui == null || client.gui.hud == null) {
            return;
        }
        replayingUrgentHudText = true;
        try {
            if (kind == TextKind.ACTION_BAR || kind == TextKind.ITEM_NAME) {
                client.gui.hud.setOverlayMessage(translated, overlayTinted);
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
        HologramTextDisplayGroups.clear();
        activeProvider = null;
        protectedPlayerNames = ProtectedLiteralsSnapshot.empty();
        protectedPlayerNamesExpireAt = 0L;
        RECENT_USER_TEXT.clear();
        outgoingTail = CompletableFuture.completedFuture(null);
        if (active != null) {
            active.close();
        }
    }

    private static synchronized ProtectedLiteralsSnapshot playerNameSnapshot() {
        long now = System.currentTimeMillis();
        if (now < protectedPlayerNamesExpireAt) {
            return protectedPlayerNames;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            protectedPlayerNames = ProtectedLiteralsSnapshot.empty();
        } else {
            List<String> names = new ArrayList<String>();
            addProtectedLiteral(names, client.getUser().getName());
            if (client.getCurrentServer() != null) {
                addProtectedLiteral(names, client.getCurrentServer().ip);
            }
            client.getConnection().getOnlinePlayers().forEach(entry -> {
                if (names.size() >= MAX_PROTECTED_PLAYER_NAMES) {
                    return;
                }
                String name = entry.getProfile().name();
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
        Path modelFile = config.offlineDirectory.resolve(config.offlineModel.modelFile());
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
        Minecraft client = Minecraft.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.gui.screen() instanceof UniversalTranslatorConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorProviderScreen
                || client.gui.screen() instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorDiagnosticsScreen
                || client.gui.screen() instanceof UniversalTranslatorCacheScreen
                || client.gui.screen() instanceof UniversalTranslatorLlmConfigScreen
                || client.gui.screen() instanceof TranslationLogScreen
                || client.gui.screen() instanceof TranslationLogSourceScreen
                || TranslationRenderContext.isTextInput()
                || client.level == null || client.getConnection() == null) {
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
        Minecraft client = Minecraft.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.gui.screen() instanceof UniversalTranslatorConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorProviderScreen
                || client.gui.screen() instanceof UniversalTranslatorDeepSeekConfigScreen
                || client.gui.screen() instanceof UniversalTranslatorDiagnosticsScreen
                || client.gui.screen() instanceof UniversalTranslatorCacheScreen
                || client.gui.screen() instanceof UniversalTranslatorLlmConfigScreen
                || client.gui.screen() instanceof TranslationLogScreen
                || client.gui.screen() instanceof TranslationLogSourceScreen
                || TranslationRenderContext.isTextInput()
                || client.level == null || client.getConnection() == null) {
            return originals;
        }
        return active.lookupIndependentLines(originals, kind);
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
        CompletableFuture<TranslationResult> translated = active.translateInteractive(
                message, TextKind.CHAT, config.outgoingTargetLanguage, false);
        CompletableFuture<TranslationResult> next = outgoingTail
                .handle((ignored, failure) -> null)
                .thenCompose(ignored -> translated);
        outgoingTail = next.handle((ignored, failure) -> null);
        return next;
    }
}

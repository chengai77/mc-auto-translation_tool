package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.universaltranslator.core.RenderTranslationSession;
import org.universaltranslator.core.PersistentTranslationCache;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationProviderStatus;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;
import org.universaltranslator.core.TranslationStore;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.RecentUserText;
import org.universaltranslator.core.TranslationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class FabricTranslationRuntime {
    private static final long PLAYER_NAME_SNAPSHOT_MILLIS = 5_000L;
    // Match ProtectedText's bounded literal limit so large network lobbies do not silently
    // drop names after the first few tab-list pages.
    private static final int MAX_PROTECTED_PLAYER_NAMES = 1_000;

    private static volatile RenderTranslationSession session;
    private static volatile FabricConfig activeConfig;
    private static volatile TranslationProvider activeProvider;
    private static volatile List<String> protectedPlayerNames = Collections.emptyList();
    private static volatile long protectedPlayerNamesExpireAt;
    private static final RecentUserText RECENT_USER_TEXT = new RecentUserText();
    private static CompletableFuture<Void> outgoingTail = CompletableFuture.completedFuture(null);
    private static volatile boolean replayingUrgentHudText;

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
        TranslationStore store = config.diskCache
                ? new PersistentTranslationCache(config.cacheFile, 10_000)
                : new TranslationCache(10_000);
        int workers = provider.id().contains("offline-llama:") ? 1 : 4;
        RenderTranslationSession created = new RenderTranslationSession(
                provider, "auto", config.targetLanguage, store, workers, config.displayMode,
                config.translateEnglishOnly);
        created.setProtectedLiteralsSupplier(FabricTranslationRuntime::playerNameSnapshot);
        created.setRenderCompletionListener((kind, output) -> {
            if (kind != TextKind.CHAT && kind != TextKind.SYSTEM_MESSAGE) {
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().inGameHud != null) {
                    MinecraftClient.getInstance().inGameHud.getChatHud().reset();
                }
            });
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
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || FabricLocalTextGuard.isLocalChatInput(client, original)
                || RECENT_USER_TEXT.shouldPreserve(original)
                || client.world == null || client.getNetworkHandler() == null) {
            return original;
        }
        String translated = active.lookup(original, kind);
        if (shouldRecordInLog(kind)) {
            TranslationLog.add(original, displayTranslatedOnly(translated));
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
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || client.world == null || client.getNetworkHandler() == null) {
            return original;
        }
        return displayTranslatedOnly(active.lookup(original.trim(), effectiveKind));
    }

    static void preloadUrgentHudText(Text text, TextKind kind, boolean overlayTinted) {
        RenderTranslationSession active = session;
        FabricConfig config = activeConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isUrgentHudKind(kind) || text == null || replayingUrgentHudText
                || active == null || config == null
                || !config.allows(kind)
                || client.world == null || client.getNetworkHandler() == null) {
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
                    Text styled = BookTextStyler.rebuild(text, translated, text.getStyle());
                    final Text output = styled == null
                            ? Text.literal(translated).setStyle(text.getStyle()) : styled;
                    client.execute(() -> replayUrgentHudText(kind, output, overlayTinted));
                });
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
        RenderTranslationSession active = session;
        if (active != null) {
            active.clearRenderedTranslations();
        }
    }

    static synchronized void shutdown() {
        RenderTranslationSession active = session;
        session = null;
        activeProvider = null;
        protectedPlayerNames = Collections.emptyList();
        protectedPlayerNamesExpireAt = 0L;
        RECENT_USER_TEXT.clear();
        outgoingTail = CompletableFuture.completedFuture(null);
        if (active != null) {
            active.close();
        }
    }

    private static synchronized List<String> playerNameSnapshot() {
        long now = System.currentTimeMillis();
        if (now < protectedPlayerNamesExpireAt) {
            return protectedPlayerNames;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            protectedPlayerNames = Collections.emptyList();
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
                String name = entry.getProfile().name();
                addProtectedLiteral(names, name);
            });
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
        // The offline provider already has the specific startup diagnostic. Prefer it over the
        // session's generic wrapper so one failure cannot alternate between two chat messages.
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (active == null || config == null || !config.allows(kind)
                || client.currentScreen instanceof UniversalTranslatorConfigScreen
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
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
                || client.currentScreen instanceof UniversalTranslatorDiagnosticsScreen
                || client.currentScreen instanceof TranslationLogScreen
                || client.currentScreen instanceof TranslationLogSourceScreen
                || TranslationRenderContext.isTextInput()
                || client.world == null || client.getNetworkHandler() == null) {
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
        int separator = value.indexOf("\n");
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    static void protectOutgoingMessage(String message) {
        RECENT_USER_TEXT.remember(message);
    }

    static boolean shouldTranslateOutgoing(String message) {
        FabricConfig config = activeConfig;
        return session != null && config != null && config.enabled && config.translateOutgoing
                && message != null && !message.trim().isEmpty() && !message.startsWith("/");
    }

    /** Serializes outgoing requests so rapidly sent chat lines keep their original order. */
    static synchronized CompletableFuture<TranslationResult> translateOutgoing(String message) {
        final RenderTranslationSession active = session;
        final FabricConfig config = activeConfig;
        if (active == null || config == null || !shouldTranslateOutgoing(message)) {
            return CompletableFuture.completedFuture(TranslationResult.unchanged(message));
        }
        RECENT_USER_TEXT.remember(message);
        // Capture the tab-list/server literals on Minecraft's calling thread. The translation
        // itself may finish on a worker, but must not inspect client network state there.
        CompletableFuture<TranslationResult> translated = active.translateInteractive(
                message, TextKind.CHAT, config.outgoingTargetLanguage, false);
        CompletableFuture<TranslationResult> next = outgoingTail
                .handle((ignored, failure) -> null)
                .thenCompose(ignored -> translated);
        outgoingTail = next.handle((ignored, failure) -> null);
        return next;
    }
}

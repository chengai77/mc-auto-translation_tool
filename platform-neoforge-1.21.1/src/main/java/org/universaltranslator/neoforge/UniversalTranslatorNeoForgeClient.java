package org.universaltranslator.neoforge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.universaltranslator.core.TranslationResult;
import org.universaltranslator.core.TranslationStatusLocalizer;

/** NeoForge客户端入口 */
public final class UniversalTranslatorNeoForgeClient {
    private static final long FAILURE_NOTIFICATION_COOLDOWN_MILLIS = 60_000L;
    private static final long TOGGLE_NOTIFICATION_MILLIS = 3_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger("universal_translator");
    private static final KeyBinding OPEN_SETTINGS = new KeyBinding(
            "key.universal_translator.open_settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            KeyBinding.MISC_CATEGORY);
    private static final KeyBinding TOGGLE_TRANSLATION = new KeyBinding(
            "key.universal_translator.toggle_translation",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            KeyBinding.MISC_CATEGORY);
    private static final KeyBinding OPEN_TRANSLATION_LOG = new KeyBinding(
            "key.universal_translator.open_translation_log",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            KeyBinding.MISC_CATEGORY);

    private static boolean connectedLastTick;
    private static int joinHintTicks = -1;
    private static String lastRuntimeStatus = "";
    private static long nextFailureNotificationAt;
    private static long runtimeNotificationPausedUntil;
    private static boolean initialized;

    private UniversalTranslatorNeoForgeClient() {
    }

    public static synchronized void register(IEventBus modBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        initializeRuntime();
        modBus.addListener(UniversalTranslatorNeoForgeClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(UniversalTranslatorNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(UniversalTranslatorNeoForgeClient::onClientChat);
        NeoForge.EVENT_BUS.addListener(UniversalTranslatorNeoForgeClient::onGameShuttingDown);
    }

    private static void initializeRuntime() {
        try {
            NeoForgeConfig config = NeoForgeConfig.load(FMLPaths.CONFIGDIR.get());
            NeoForgeTranslationRuntime.initialize(config);
            LOGGER.info("MC Auto Translation Tool initialized; enabled={}", config.enabled);
        } catch (Exception exception) {
            LOGGER.error("MC Auto Translation Tool configuration failed; translation remains disabled", exception);
            NeoForgeTranslationRuntime.shutdown();
        }
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
        event.register(TOGGLE_TRANSLATION);
        event.register(OPEN_TRANSLATION_LOG);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        NeoForgeTranslationRuntime.tickUrgentHudText();
        boolean connected = client.world != null && client.getNetworkHandler() != null;
        if (connected && !connectedLastTick) {
            TranslationLog.clear();
            joinHintTicks = 60;
        } else if (!connected) {
            joinHintTicks = -1;
        }
        connectedLastTick = connected;
        if (connected && joinHintTicks > 0 && --joinHintTicks == 0) {
            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.universal_translator.join_hint"));
        }
        while (TOGGLE_TRANSLATION.wasPressed()) {
            toggleTranslation(client);
        }
        notifyRuntimeStatus(client, connected);
        while (OPEN_TRANSLATION_LOG.wasPressed()) {
            if (!(client.currentScreen instanceof TranslationLogScreen)) {
                client.setScreen(new TranslationLogScreen(client.currentScreen));
            }
        }
        while (OPEN_SETTINGS.wasPressed()) {
            if (client.currentScreen instanceof UniversalTranslatorConfigScreen) {
                continue;
            }
            try {
                NeoForgeConfig config = NeoForgeConfig.load(FMLPaths.CONFIGDIR.get());
                client.setScreen(new UniversalTranslatorConfigScreen(client.currentScreen, config));
            } catch (Exception exception) {
                LOGGER.error("Could not open MC Auto Translation Tool settings", exception);
            }
        }
    }

    private static void toggleTranslation(MinecraftClient client) {
        NeoForgeConfig previous = null;
        boolean runtimeChanged = false;
        try {
            previous = NeoForgeConfig.load(FMLPaths.CONFIGDIR.get());
            NeoForgeConfig updated = previous.withEnabled(!previous.enabled);
            if (updated.enabled) {
                updated.validateProviderConfiguration();
            }
            runtimeChanged = true;
            NeoForgeTranslationRuntime.initialize(updated);
            lastRuntimeStatus = "";
            nextFailureNotificationAt = 0L;
            updated.save();
            runtimeNotificationPausedUntil =
                    System.currentTimeMillis() + TOGGLE_NOTIFICATION_MILLIS;
            NeoForgeTranslationRuntime.showLocalOverlay(
                    Text.translatable("message.universal_translator.toggle",
                            Text.translatable(updated.enabled
                                    ? "value.universal_translator.enabled"
                                    : "value.universal_translator.disabled")),
                    false);
        } catch (Exception exception) {
            if (runtimeChanged && previous != null) {
                try {
                    NeoForgeTranslationRuntime.initialize(previous);
                } catch (Exception restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            LOGGER.error("Could not toggle MC Auto Translation Tool", exception);
            NeoForgeTranslationRuntime.showLocalOverlay(
                    Text.translatable("message.universal_translator.toggle_failed"), false);
        }
    }

    private static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!NeoForgeTranslationRuntime.shouldTranslateOutgoing(message)) {
            return;
        }
        event.setCanceled(true);
        MinecraftClient client = MinecraftClient.getInstance();
        NeoForgeTranslationRuntime.showLocalOverlay(
                Text.translatable("message.universal_translator.outgoing_translating"), false);
        NeoForgeTranslationRuntime.translateOutgoing(message).whenComplete((result, error) ->
                client.execute(() -> sendCompletedMessage(client, message, result, error)));
    }

    private static void sendCompletedMessage(
            MinecraftClient client,
            String original,
            TranslationResult result,
            Throwable error
    ) {
        if (client.getNetworkHandler() == null) {
            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.universal_translator.outgoing_disconnected"));
            return;
        }
        boolean failed = error != null || result == null || result.isFailure();
        String outgoing = failed || !result.isTranslated()
                ? original : result.getTranslatedText();
        boolean tooLong = outgoing.length() > 256;
        if (tooLong) {
            outgoing = original;
        }
        NeoForgeTranslationRuntime.protectOutgoingMessage(outgoing);
        client.getNetworkHandler().sendChatMessage(outgoing);
        if (failed) {
            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.universal_translator.outgoing_failed"));
        } else if (tooLong) {
            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.universal_translator.outgoing_too_long"));
        }
    }

    private static void notifyRuntimeStatus(MinecraftClient client, boolean connected) {
        String current = connected ? NeoForgeTranslationRuntime.status() : "";
        if (current == null) {
            current = "";
        }
        long now = System.currentTimeMillis();
        boolean downloadProgress = TranslationStatusLocalizer.isDownloadProgress(current);
        boolean changed = !current.equals(lastRuntimeStatus);
        if (downloadProgress) {
            lastRuntimeStatus = current;
            return;
        }
        if (!changed) {
            return;
        }
        boolean failure = TranslationStatusLocalizer.isFailure(current);
        if (!failure && now < runtimeNotificationPausedUntil) {
            return;
        }
        lastRuntimeStatus = current;
        if (current.isEmpty()) {
            if (!connected) {
                nextFailureNotificationAt = 0L;
            }
            return;
        }
        if (!failure && client.currentScreen != null) {
            return;
        }
        String localized = TranslationStatusLocalizer.localize(
                current, UniversalTranslatorNeoForgeClient::tr);
        if (failure) {
            if (now < nextFailureNotificationAt) {
                return;
            }
            nextFailureNotificationAt = now + FAILURE_NOTIFICATION_COOLDOWN_MILLIS;
            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.universal_translator.runtime_failed", localized));
        } else {
            nextFailureNotificationAt = 0L;
            NeoForgeTranslationRuntime.showLocalOverlay(
                    Text.translatable("message.universal_translator.runtime_status", localized), false);
        }
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event) {
        NeoForgeTranslationRuntime.shutdown();
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }
}

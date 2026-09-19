package org.universaltranslator.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.universaltranslator.core.TranslationResult;
import org.universaltranslator.core.TranslationStatusLocalizer;

public final class UniversalTranslatorForgeClient {
    private static final long FAILURE_NOTIFICATION_COOLDOWN_MILLIS = 60_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger("universal_translator");
    private static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.universal_translator.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, KeyMapping.Category.MISC);
    private static final KeyMapping TOGGLE_TRANSLATION = new KeyMapping("key.universal_translator.toggle_translation", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC);
    private static final KeyMapping OPEN_TRANSLATION_LOG = new KeyMapping("key.universal_translator.open_translation_log", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KeyMapping.Category.MISC);
    private static boolean connectedLastTick;
    private static int joinHintTicks = -1;
    private static String lastRuntimeStatus = "";
    private static long nextFailureNotificationAt;

    private UniversalTranslatorForgeClient() { }

    public static void register() {
        initializeRuntime();
        RegisterKeyMappingsEvent.BUS.addListener(UniversalTranslatorForgeClient::registerKeyMappings);
        TickEvent.ClientTickEvent.Post.BUS.addListener(UniversalTranslatorForgeClient::onClientTick);
        ClientChatEvent.BUS.addListener(UniversalTranslatorForgeClient::onClientChat);
        GameShuttingDownEvent.BUS.addListener(UniversalTranslatorForgeClient::onGameShuttingDown);
    }

    private static void initializeRuntime() {
        try {
            ForgeConfig config = ForgeConfig.load(FMLPaths.CONFIGDIR.get());
            ForgeTranslationRuntime.initialize(config);
            LOGGER.info("MC Auto Translation Tool initialized; enabled={}", config.enabled);
        } catch (Exception exception) {
            LOGGER.error("MC Auto Translation Tool configuration failed", exception);
            ForgeTranslationRuntime.shutdown();
        }
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
        event.register(TOGGLE_TRANSLATION);
        event.register(OPEN_TRANSLATION_LOG);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ForgeTranslationRuntime.tickUrgentHudText();
        boolean connected = client.level != null && client.getConnection() != null;
        if (connected && !connectedLastTick) { TranslationLog.clear(); joinHintTicks = 60; }
        else if (!connected) joinHintTicks = -1;
        connectedLastTick = connected;
        if (connected && joinHintTicks > 0 && --joinHintTicks == 0) client.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.universal_translator.join_hint"));
        while (TOGGLE_TRANSLATION.consumeClick()) toggleTranslation(client);
        notifyRuntimeStatus(client, connected);
        while (OPEN_TRANSLATION_LOG.consumeClick()) if (!(client.gui.screen() instanceof TranslationLogScreen)) client.gui.setScreen(new TranslationLogScreen(client.gui.screen()));
        while (OPEN_SETTINGS.consumeClick()) {
            if (client.gui.screen() instanceof UniversalTranslatorConfigScreen) continue;
            try { client.gui.setScreen(new UniversalTranslatorConfigScreen(client.gui.screen(), ForgeConfig.load(FMLPaths.CONFIGDIR.get()))); }
            catch (Exception exception) { LOGGER.error("Could not open settings", exception); }
        }
    }

    private static void toggleTranslation(Minecraft client) {
        ForgeConfig previous = null;
        boolean runtimeChanged = false;
        try {
            previous = ForgeConfig.load(FMLPaths.CONFIGDIR.get());
            ForgeConfig updated = previous.withEnabled(!previous.enabled);
            if (updated.enabled) updated.validateProviderConfiguration();
            runtimeChanged = true;
            ForgeTranslationRuntime.initialize(updated);
            lastRuntimeStatus = "";
            nextFailureNotificationAt = 0L;
            updated.save();
            client.gui.hud.setOverlayMessage(Component.translatable("message.universal_translator.toggle", Component.translatable(updated.enabled ? "value.universal_translator.enabled" : "value.universal_translator.disabled")), false);
        } catch (Exception exception) {
            if (runtimeChanged && previous != null) {
                try {
                    ForgeTranslationRuntime.initialize(previous);
                } catch (Exception restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            LOGGER.error("Could not toggle translation", exception);
            ForgeTranslationRuntime.showLocalOverlay(Component.translatable("message.universal_translator.toggle_failed"), false);
        }
    }

    private static boolean onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!ForgeTranslationRuntime.shouldTranslateOutgoing(message)) return false;
        Minecraft client = Minecraft.getInstance();
        ForgeTranslationRuntime.showLocalOverlay(Component.translatable("message.universal_translator.outgoing_translating"), false);
        ForgeTranslationRuntime.translateOutgoing(message).whenComplete((result, error) -> client.execute(() -> sendCompletedMessage(client, message, result, error)));
        return true;
    }

    private static void sendCompletedMessage(Minecraft client, String original, TranslationResult result, Throwable error) {
        if (client.getConnection() == null) {
            client.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.universal_translator.outgoing_disconnected"));
            return;
        }
        boolean failed = error != null || result == null || result.isFailure();
        String outgoing = failed || !result.isTranslated() ? original : result.getTranslatedText();
        boolean tooLong = outgoing.length() > 256;
        if (tooLong) outgoing = original;
        ForgeTranslationRuntime.protectOutgoingMessage(outgoing);
        client.getConnection().sendChat(outgoing);
        if (failed) {
            client.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.universal_translator.outgoing_failed"));
        } else if (tooLong) {
            client.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.universal_translator.outgoing_too_long"));
        }
    }

    private static void notifyRuntimeStatus(Minecraft client, boolean connected) {
        String current = connected ? ForgeTranslationRuntime.status() : "";
        if (current == null) current = "";
        long now = System.currentTimeMillis();
        boolean downloadProgress = TranslationStatusLocalizer.isDownloadProgress(current);
        boolean changed = !current.equals(lastRuntimeStatus);
        if (downloadProgress) {
            lastRuntimeStatus = current;
            return;
        }
        if (!changed) return;
        lastRuntimeStatus = current;
        if (current.isEmpty()) {
            if (!connected) nextFailureNotificationAt = 0L;
            return;
        }
        boolean failure = TranslationStatusLocalizer.isFailure(current);
        String localized = TranslationStatusLocalizer.localize(current,
                UniversalTranslatorForgeClient::tr);
        if (failure) {
            if (now < nextFailureNotificationAt) return;
            nextFailureNotificationAt = now + FAILURE_NOTIFICATION_COOLDOWN_MILLIS;
            client.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.universal_translator.runtime_failed", localized));
        } else if (client.gui.screen() == null) {
            nextFailureNotificationAt = 0L;
            client.gui.hud.setOverlayMessage(Component.translatable("message.universal_translator.runtime_status", localized), false);
        }
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event) { ForgeTranslationRuntime.shutdown(); }
}

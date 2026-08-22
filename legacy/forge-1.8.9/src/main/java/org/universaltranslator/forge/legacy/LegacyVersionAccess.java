package org.universaltranslator.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.List;

/** 1.8.9名适配 */
public final class LegacyVersionAccess {
    private LegacyVersionAccess() {
    }

    static NetHandlerPlayClient connection(Minecraft minecraft) {
        return minecraft.getNetHandler();
    }

    static FontRenderer fontRenderer() {
        return Minecraft.getMinecraft().fontRendererObj;
    }

    static String localPlayerName(Minecraft minecraft) {
        return minecraft.getSession() == null ? null : minecraft.getSession().getUsername();
    }

    static String serverAddress(Minecraft minecraft) {
        return minecraft.getCurrentServerData() == null
                ? null : minecraft.getCurrentServerData().serverIP;
    }

    static void showLocalChatMessage(Minecraft minecraft, String message) {
        minecraft.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
    }

    static void sendChatMessage(Minecraft minecraft, String message) {
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.sendChatMessage(message);
        }
    }

    static void rememberSentMessage(Minecraft minecraft, String message) {
        minecraft.ingameGUI.getChatGUI().addToSentMessages(message);
    }

    static int maximumChatLength() {
        return 100;
    }

    public static List<IChatComponent> splitTranslatedChat(
            IChatComponent component,
            int width,
            FontRenderer font,
            boolean keepNewLines,
            boolean keepFormatting
    ) {
        IChatComponent translated = translateChatComponent(component);
        LegacyRenderContext.pushChat();
        try {
            return GuiUtilRenderComponents.splitText(
                    translated, width, font, keepNewLines, keepFormatting);
        } finally {
            LegacyRenderContext.pop();
        }
    }

    private static IChatComponent translateChatComponent(IChatComponent component) {
        if (component == null) {
            return null;
        }
        String original = component.getUnformattedText();
        String translated = LegacyRenderedTextBridge.translateChatMessage(original);
        if (original.equals(translated)) {
            return component;
        }
        ChatComponentText replacement = new ChatComponentText(translated);
        replacement.setChatStyle(component.getChatStyle());
        return replacement;
    }

    static void refreshChatAsync(final Minecraft minecraft) {
        minecraft.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (minecraft.ingameGUI != null) {
                    minecraft.ingameGUI.getChatGUI().refreshChat();
                }
            }
        });
    }
}

package org.universaltranslator.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

/** Compile-time adapter for names changed after Minecraft 1.8.9. */
public final class LegacyVersionAccess {
    private LegacyVersionAccess() {
    }

    static NetHandlerPlayClient connection(Minecraft minecraft) {
        return minecraft.getConnection();
    }

    static FontRenderer fontRenderer() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    static String localPlayerName(Minecraft minecraft) {
        return minecraft.getSession() == null ? null : minecraft.getSession().getUsername();
    }

    static String serverAddress(Minecraft minecraft) {
        return minecraft.getCurrentServerData() == null
                ? null : minecraft.getCurrentServerData().serverIP;
    }

    static void showLocalChatMessage(Minecraft minecraft, String message) {
        minecraft.ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message));
    }

    static void sendChatMessage(Minecraft minecraft, String message) {
        if (minecraft.player != null) {
            minecraft.player.sendChatMessage(message);
        }
    }

    static void rememberSentMessage(Minecraft minecraft, String message) {
        minecraft.ingameGUI.getChatGUI().addToSentMessages(message);
    }

    static int maximumChatLength() {
        return 256;
    }

    public static List<ITextComponent> splitTranslatedChat(
            ITextComponent component,
            int width,
            FontRenderer font,
            boolean keepNewLines,
            boolean keepFormatting
    ) {
        ITextComponent translated = translateChatComponent(component);
        LegacyRenderContext.pushChat();
        try {
            return GuiUtilRenderComponents.splitText(
                    translated, width, font, keepNewLines, keepFormatting);
        } finally {
            LegacyRenderContext.pop();
        }
    }

    private static ITextComponent translateChatComponent(ITextComponent component) {
        if (component == null) {
            return null;
        }
        String original = component.getUnformattedText();
        String translated = LegacyRenderedTextBridge.translateChatMessage(original);
        if (original.equals(translated)) {
            return component;
        }
        TextComponentString replacement = new TextComponentString(translated);
        replacement.setStyle(component.getStyle());
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

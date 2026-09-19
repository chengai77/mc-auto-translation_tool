package org.universaltranslator.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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

    static int scaledWidth(Minecraft minecraft) {
        return new ScaledResolution(minecraft).getScaledWidth();
    }

    static int scaledHeight(Minecraft minecraft) {
        return new ScaledResolution(minecraft).getScaledHeight();
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

    static void refreshChatNow(Minecraft minecraft) {
        if (minecraft.ingameGUI != null) {
            Object chat = minecraft.ingameGUI.getChatGUI();
            Field scroll = findChatField(chat, int.class, "scrollPos");
            Field scrolled = findChatField(chat, boolean.class, "isScrolled");
            Field visible = findChatListField(chat, "drawnChatLines", "chatLines");
            int oldLines = readListSize(visible, chat);
            int scrollValue = readInt(scroll, chat, 0);
            boolean scrolledValue = readBoolean(scrolled, chat, false);
            ((net.minecraft.client.gui.GuiNewChat) chat).refreshChat();
            int newLines = readListSize(visible, chat);
            int restoredScroll = scrollValue <= 0
                    ? 0 : Math.max(0, scrollValue + newLines - oldLines);
            writeInt(scroll, chat, restoredScroll);
            writeBoolean(scrolled, chat, scrolledValue);
        }
    }

    private static Field findChatField(Object chat, Class<?> type, String name) {
        if (chat == null) {
            return null;
        }
        try {
            Field named = chat.getClass().getDeclaredField(name);
            if (named.getType() == type) {
                named.setAccessible(true);
                return named;
            }
        } catch (Throwable ignored) {
        }
        for (Field field : chat.getClass().getDeclaredFields()) {
            if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Field findChatListField(Object chat, String... names) {
        if (chat == null) {
            return null;
        }
        for (String name : names) {
            try {
                Field field = chat.getClass().getDeclaredField(name);
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int readListSize(Field field, Object target) {
        try {
            Object value = field == null ? null : field.get(target);
            return value instanceof List<?> ? ((List<?>) value).size() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int readInt(Field field, Object target, int fallback) {
        try {
            return field == null ? fallback : field.getInt(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(Field field, Object target, boolean fallback) {
        try {
            return field == null ? fallback : field.getBoolean(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void writeInt(Field field, Object target, int value) {
        try {
            if (field != null) {
                field.setInt(target, Math.max(0, value));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void writeBoolean(Field field, Object target, boolean value) {
        try {
            if (field != null) {
                field.setBoolean(target, value);
            }
        } catch (Throwable ignored) {
        }
    }

    static void displayTitle(
            Minecraft minecraft,
            String title,
            String subtitle,
            int fadeIn,
            int stay,
            int fadeOut
    ) {
        minecraft.ingameGUI.displayTitle(
                title, subtitle, fadeIn, stay, fadeOut);
    }

    static void displayOverlay(Minecraft minecraft, String text, boolean tinted) {
        minecraft.ingameGUI.setRecordPlaying(text, tinted);
    }

    static List<String> signLines(Object value) {
        List<String> lines = new ArrayList<String>(4);
        if (!(value instanceof TileEntitySign)) {
            return lines;
        }
        for (IChatComponent component : ((TileEntitySign) value).signText) {
            lines.add(component == null ? "" : component.getFormattedText());
        }
        return lines;
    }
}

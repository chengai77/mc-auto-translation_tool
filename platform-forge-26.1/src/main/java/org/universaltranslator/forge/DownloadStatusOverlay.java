package org.universaltranslator.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.TranslationStatusLocalizer;

/** 下载状态三行显示 */
public final class DownloadStatusOverlay {
    private DownloadStatusOverlay() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.options.hideGui) {
            return;
        }
        TranslationStatusLocalizer.DownloadProgressDisplay display =
                TranslationStatusLocalizer.downloadProgressDisplay(
                        ForgeTranslationRuntime.status(), DownloadStatusOverlay::tr);
        if (display == null) {
            return;
        }
        int center = client.getWindow().getGuiScaledWidth() / 2;
        int y = client.getWindow().getGuiScaledHeight() - 94;
        draw(graphics, client, tr("message.universal_translator.runtime_title"), center, y);
        draw(graphics, client, display.progress(), center, y + 11);
        draw(graphics, client, display.size(), center, y + 22);
    }

    private static void draw(
            GuiGraphicsExtractor graphics, Minecraft client, String value, int center, int y) {
        graphics.text(client.font, Component.literal(value),
                center - client.font.width(value) / 2, y, 0xFFFFFF);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}


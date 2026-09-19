package org.universaltranslator.forge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.universaltranslator.core.TranslationStatusLocalizer;

/** 下载状态三行显示 */
public final class DownloadStatusOverlay {
    private DownloadStatusOverlay() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        TranslationStatusLocalizer.DownloadProgressDisplay display =
                TranslationStatusLocalizer.downloadProgressDisplay(
                        ForgeTranslationRuntime.status(), DownloadStatusOverlay::tr);
        if (display == null) {
            return;
        }
        int center = client.getWindow().getScaledWidth() / 2;
        int y = client.getWindow().getScaledHeight() - 94;
        draw(context, client, tr("message.universal_translator.runtime_title"), center, y);
        draw(context, client, display.progress(), center, y + 11);
        draw(context, client, display.size(), center, y + 22);
    }

    private static void draw(
            DrawContext context, MinecraftClient client, String value, int center, int y) {
        context.drawTextWithShadow(client.textRenderer, Text.literal(value),
                center - client.textRenderer.getWidth(value) / 2, y, 0xFFFFFF);
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }
}


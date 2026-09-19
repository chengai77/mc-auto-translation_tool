package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.universaltranslator.core.TranslationStatusLocalizer;

/** 下载状态三行显示 */
public final class DownloadStatusOverlay {
    private DownloadStatusOverlay() {
    }

    public static void render(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        TranslationStatusLocalizer.DownloadProgressDisplay display =
                TranslationStatusLocalizer.downloadProgressDisplay(
                        FabricTranslationRuntime.status(), DownloadStatusOverlay::tr);
        if (display == null) {
            return;
        }
        int center = client.getWindow().getScaledWidth() / 2;
        int y = client.getWindow().getScaledHeight() - 94;
        draw(matrices, client, tr("message.universal_translator.runtime_title"), center, y);
        draw(matrices, client, display.progress(), center, y + 11);
        draw(matrices, client, display.size(), center, y + 22);
    }

    private static void draw(
            MatrixStack matrices, MinecraftClient client, String value, int center, int y) {
        DrawableHelper.drawTextWithShadow(matrices, client.textRenderer,
                new LiteralText(value), center - client.textRenderer.getWidth(value) / 2, y, 0xFFFFFF);
    }

    private static String tr(String key, Object... arguments) {
        return new TranslatableText(key, arguments).getString();
    }
}

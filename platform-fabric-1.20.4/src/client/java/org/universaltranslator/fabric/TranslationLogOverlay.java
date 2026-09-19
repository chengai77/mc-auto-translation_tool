package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

import java.util.List;

public final class TranslationLogOverlay {
    private TranslationLogOverlay() {
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        TranslationLog.Entry entry = TranslationLog.latest();
        if (client.options.hudHidden || client.textRenderer == null || config == null
                || !config.pinnedLogEnabled || entry == null) {
            return;
        }
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        float scale = config.pinnedLogScale / 100.0F;
        int panelWidth = Math.max(80, screenWidth * config.pinnedLogWidth / 100);
        int scaledWidth = Math.round(panelWidth / scale);
        int x = "top-left".equals(config.pinnedLogPreset)
                ? config.pinnedLogX
                : screenWidth - Math.round(panelWidth) - config.pinnedLogX;
        int y = config.pinnedLogY;
        x = Math.max(4, Math.min(screenWidth - Math.round(panelWidth) - 4, x));
        y = Math.max(4, Math.min(screenHeight - 24, y));

        String text = config.displayMode == org.universaltranslator.core.TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? entry.original + "\n" + entry.translated
                : entry.translated;
        List<OrderedText> lines = textRenderer.wrapLines(net.minecraft.text.Text.literal(text), scaledWidth - 12);
        int visibleLines = Math.min(lines.size(), 8);
        int scaledHeight = 8 + visibleLines * (textRenderer.fontHeight + 2);
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.fill(0, 0, scaledWidth, scaledHeight, 0xAA000000);
        context.fill(0, scaledHeight - 1, scaledWidth, scaledHeight, 0x66FFFFFF);
        int lineY = 5;
        for (int index = 0; index < visibleLines; index++) {
            context.drawTextWithShadow(textRenderer, lines.get(index), 6, lineY, 0xFFFFFFFF);
            lineY += textRenderer.fontHeight + 2;
        }
        context.getMatrices().pop();
    }
}

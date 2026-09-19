package org.universaltranslator.forge.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.universaltranslator.core.TranslationDisplayMode;

import java.util.List;

final class LegacyTranslationLogOverlay {
    private LegacyTranslationLogOverlay() {
    }

    static void render(Minecraft minecraft) {
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        LegacyTranslationLog.Entry entry = LegacyTranslationLog.latest();
        if (minecraft == null || minecraft.gameSettings.hideGUI || config == null
                || !config.pinnedLogEnabled || entry == null) {
            return;
        }
        FontRenderer renderer = LegacyVersionAccess.fontRenderer();
        int screenWidth = LegacyVersionAccess.scaledWidth(minecraft);
        int screenHeight = LegacyVersionAccess.scaledHeight(minecraft);
        float scale = config.pinnedLogScale / 100.0F;
        int panelWidth = Math.max(80, screenWidth * config.pinnedLogWidth / 100);
        int scaledWidth = Math.round(panelWidth / scale);
        int x = "top-left".equals(config.pinnedLogPreset)
                ? config.pinnedLogX : screenWidth - panelWidth - config.pinnedLogX;
        int y = config.pinnedLogY;
        x = Math.max(4, Math.min(screenWidth - panelWidth - 4, x));
        y = Math.max(4, Math.min(screenHeight - 24, y));
        String text = config.displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? entry.original + "\n" + entry.translated : entry.translated;
        List<String> lines = renderer.listFormattedStringToWidth(text, scaledWidth - 12);
        int visibleLines = Math.min(lines.size(), 8);
        int panelHeight = 8 + visibleLines * (renderer.FONT_HEIGHT + 2);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        Gui.drawRect(0, 0, scaledWidth, panelHeight, 0xAA000000);
        Gui.drawRect(0, panelHeight - 1, scaledWidth, panelHeight, 0x66FFFFFF);
        int lineY = 5;
        for (int index = 0; index < visibleLines; index++) {
            renderer.drawStringWithShadow(lines.get(index), 6, lineY, 0xFFFFFFFF);
            lineY += renderer.FONT_HEIGHT + 2;
        }
        GlStateManager.popMatrix();
    }
}

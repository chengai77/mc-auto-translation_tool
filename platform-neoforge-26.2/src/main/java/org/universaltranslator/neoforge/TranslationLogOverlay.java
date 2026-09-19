package org.universaltranslator.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.TranslationDisplayMode;

import java.util.List;

public final class TranslationLogOverlay {
    private TranslationLogOverlay() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        NeoForgeConfig config = NeoForgeTranslationRuntime.currentConfig();
        TranslationLog.Entry entry = TranslationLog.latest();
        if (config == null || !config.pinnedLogEnabled || entry == null) {
            return;
        }
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int panelWidth = Math.max(80, screenWidth * config.pinnedLogWidth / 100);
        int x = "top-left".equals(config.pinnedLogPreset)
                ? config.pinnedLogX
                : screenWidth - panelWidth - config.pinnedLogX;
        int y = config.pinnedLogY;
        x = Math.max(4, Math.min(screenWidth - panelWidth - 4, x));
        y = Math.max(4, y);
        String text = config.displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? entry.original + "\n" + entry.translated
                : entry.translated;
        List<String> lines = TranslationLogText.wrap(text, Math.max(12, panelWidth / 6));
        int maxLines = Math.min(lines.size(), 8);
        for (int index = 0; index < maxLines; index++) {
            graphics.text(client.font, Component.literal(lines.get(index)), x, y + index * 10, 0xFFFFFF);
        }
    }
}


package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.universaltranslator.core.TextKind;

import java.io.IOException;

final class LegacyTranslationLogSourceScreen extends GuiScreen {
    private static final int BACK = 1;
    private static final int SOURCE_BASE = 100;
    private static final int BUTTON_WIDTH = 150;
    private static final int GAP = 8;
    private static final TextKind[] LOG_SOURCE_KINDS = new TextKind[] {
            TextKind.TITLE, TextKind.SUBTITLE, TextKind.ACTION_BAR, TextKind.CHAT,
            TextKind.SYSTEM_MESSAGE, TextKind.BOSS_BAR, TextKind.SCOREBOARD_TITLE,
            TextKind.SCOREBOARD_LINE, TextKind.PLAYER_LIST_HEADER, TextKind.PLAYER_LIST_FOOTER,
            TextKind.CONTAINER_TITLE, TextKind.BOOK, TextKind.SIGN,
            TextKind.DISCONNECT_REASON, TextKind.HOLOGRAM, TextKind.OTHER
    };
    private final GuiScreen parent;
    private FontRenderer renderer;

    LegacyTranslationLogSourceScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int columns = Math.max(1, Math.min(3, (width - 24 + GAP) / (BUTTON_WIDTH + GAP)));
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int startX = Math.max(12, (width - totalWidth) / 2);
        for (int index = 0; index < LOG_SOURCE_KINDS.length; index++) {
            int x = startX + (index % columns) * (BUTTON_WIDTH + GAP);
            int y = 36 + (index / columns) * 24;
            TextKind kind = LOG_SOURCE_KINDS[index];
            buttonList.add(new GuiButton(SOURCE_BASE + index, x, y, BUTTON_WIDTH, 20, sourceText(kind)));
        }
        buttonList.add(new GuiButton(BACK, width / 2 - 50, height - 28, 100, 20,
                tr("screen.universal_translator.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BACK) {
            mc.displayGuiScreen(parent);
            return;
        }
        int index = button.id - SOURCE_BASE;
        if (index < 0 || index >= LOG_SOURCE_KINDS.length) {
            return;
        }
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        if (config != null) {
            LegacyConfig updated = config.withLogAllowedKind(LOG_SOURCE_KINDS[index],
                    !config.logAllowedKinds.contains(LOG_SOURCE_KINDS[index]));
            updated.save();
            LegacyTranslationRuntime.updateConfig(updated);
            initGui();
        }
    }

    private String sourceText(TextKind kind) {
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        boolean allowed = config != null && config.logAllowedKinds.contains(kind);
        return (allowed ? "[x] " : "[ ] ") + tr(
                "screen.universal_translator.log.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(renderer, tr("screen.universal_translator.log.source_settings.title"),
                width / 2, 12, 0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }
}

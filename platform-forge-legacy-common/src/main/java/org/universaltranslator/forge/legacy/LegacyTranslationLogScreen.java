package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;
import org.universaltranslator.core.TranslationDisplayMode;

import java.io.IOException;
import java.util.List;

final class LegacyTranslationLogScreen extends GuiScreen {
    private static final int BACK = 1;
    private static final int CLEAR = 2;
    private static final int SOURCE = 3;
    private static final int PIN = 4;
    private static final int TOP_LEFT = 10;
    private static final int TOP_RIGHT = 11;
    private static final int X_MINUS = 20;
    private static final int X_VALUE = 21;
    private static final int X_PLUS = 22;
    private static final int Y_MINUS = 23;
    private static final int Y_VALUE = 24;
    private static final int Y_PLUS = 25;
    private static final int WIDTH_MINUS = 26;
    private static final int WIDTH_VALUE = 27;
    private static final int WIDTH_PLUS = 28;
    private static final int SCALE_MINUS = 29;
    private static final int SCALE_VALUE = 30;
    private static final int SCALE_PLUS = 31;
    private final GuiScreen parent;
    private FontRenderer renderer;
    private int scroll;

    LegacyTranslationLogScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int bottom = height - 28;
        buttonList.add(new GuiButton(BACK, 12, bottom, 90, 20, tr("gui.back")));
        buttonList.add(new GuiButton(CLEAR, 108, bottom, 100, 20,
                tr("screen.universal_translator.log.clear")));
        buttonList.add(new GuiButton(SOURCE, 216, bottom, 118, 20,
                tr("screen.universal_translator.log.source_settings")));
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        buttonList.add(new GuiButton(PIN, width - 102, bottom, 90, 20,
                tr(config != null && config.pinnedLogEnabled
                        ? "screen.universal_translator.log.unpin"
                        : "screen.universal_translator.log.pin")));
        if (config == null || !config.pinnedLogEnabled) {
            return;
        }
        int y = bottom - 54;
        buttonList.add(new GuiButton(TOP_LEFT, 12, y, 74, 20,
                tr("screen.universal_translator.log.top_left")));
        buttonList.add(new GuiButton(TOP_RIGHT, 92, y, 74, 20,
                tr("screen.universal_translator.log.top_right")));
        addStepper(12, y + 26, "X", config.pinnedLogX, X_MINUS, X_VALUE, X_PLUS);
        addStepper(172, y + 26, "Y", config.pinnedLogY, Y_MINUS, Y_VALUE, Y_PLUS);
        addStepper(332, y + 26, tr("screen.universal_translator.log.width"),
                config.pinnedLogWidth, WIDTH_MINUS, WIDTH_VALUE, WIDTH_PLUS);
        addStepper(492, y + 26, tr("screen.universal_translator.log.scale"),
                config.pinnedLogScale, SCALE_MINUS, SCALE_VALUE, SCALE_PLUS);
    }

    private void addStepper(int x, int y, String label, int value, int minus, int valueId, int plus) {
        int clampedX = Math.min(x, Math.max(12, width - 150));
        buttonList.add(new GuiButton(minus, clampedX, y, 24, 20, "-"));
        buttonList.add(new GuiButton(valueId, clampedX + 26, y, 82, 20, label + " " + value));
        buttonList.add(new GuiButton(plus, clampedX + 110, y, 24, 20, "+"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        if (button.id == BACK) {
            mc.displayGuiScreen(parent);
        } else if (button.id == CLEAR) {
            LegacyTranslationRuntime.clearTranslationHistory();
            scroll = 0;
            initGui();
        } else if (button.id == SOURCE) {
            mc.displayGuiScreen(new LegacyTranslationLogSourceScreen(this));
        } else if (button.id == PIN && config != null) {
            savePinned(config, !config.pinnedLogEnabled, config.pinnedLogPreset,
                    config.pinnedLogX, config.pinnedLogY, config.pinnedLogWidth, config.pinnedLogScale);
        } else if ((button.id == TOP_LEFT || button.id == TOP_RIGHT) && config != null) {
            savePinned(config, true, button.id == TOP_LEFT ? "top-left" : "top-right",
                    config.pinnedLogX, config.pinnedLogY, config.pinnedLogWidth, config.pinnedLogScale);
        } else if (config != null) {
            int value = config.pinnedLogX;
            int minimum = 0;
            int maximum = 500;
            if (button.id == X_MINUS || button.id == X_PLUS) {
                value += button.id == X_MINUS ? -4 : 4;
            } else if (button.id == Y_MINUS || button.id == Y_PLUS) {
                value = config.pinnedLogY + (button.id == Y_MINUS ? -4 : 4);
                minimum = 0;
                maximum = 500;
            } else if (button.id == WIDTH_MINUS || button.id == WIDTH_PLUS) {
                value = config.pinnedLogWidth + (button.id == WIDTH_MINUS ? -2 : 2);
                minimum = 20;
                maximum = 90;
            } else if (button.id == SCALE_MINUS || button.id == SCALE_PLUS) {
                value = config.pinnedLogScale + (button.id == SCALE_MINUS ? -5 : 5);
                minimum = 70;
                maximum = 160;
            } else {
                return;
            }
            value = Math.max(minimum, Math.min(maximum, value));
            int x = config.pinnedLogX;
            int y = config.pinnedLogY;
            int panelWidth = config.pinnedLogWidth;
            int scale = config.pinnedLogScale;
            if (button.id == X_MINUS || button.id == X_PLUS) x = value;
            if (button.id == Y_MINUS || button.id == Y_PLUS) y = value;
            if (button.id == WIDTH_MINUS || button.id == WIDTH_PLUS) panelWidth = value;
            if (button.id == SCALE_MINUS || button.id == SCALE_PLUS) scale = value;
            savePinned(config, true, config.pinnedLogPreset, x, y, panelWidth, scale);
        }
        if (button.id != BACK && mc.currentScreen == this) {
            initGui();
        }
    }

    private void savePinned(LegacyConfig config, boolean enabled, String preset,
                            int x, int y, int width, int scale) throws IOException {
        LegacyConfig updated = config.withPinnedLogSettings(enabled, preset, x, y, width, scale);
        updated.save();
        LegacyTranslationRuntime.updateConfig(updated);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, scroll - (wheel > 0 ? 18 : -18));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(renderer, tr("screen.universal_translator.translation_log"), width / 2, 12, 0xFFFFFFFF);
        List<LegacyTranslationLog.Entry> entries = LegacyTranslationLog.entries();
        int top = 32;
        int bottom = controlsTop() - 8;
        int contentWidth = Math.max(120, width - 56);
        int y = top - scroll;
        if (entries.isEmpty()) {
            drawCenteredString(renderer, tr("screen.universal_translator.log.empty"), width / 2, top + 24, 0xFFAAAAAA);
        } else {
            for (LegacyTranslationLog.Entry entry : entries) {
                String text = entryText(entry);
                List<String> lines = renderer.listFormattedStringToWidth(text, contentWidth);
                int entryHeight = lines.size() * (renderer.FONT_HEIGHT + 2) + 14;
                if (y + entryHeight >= top && y <= bottom) {
                    int lineY = y;
                    for (String line : lines) {
                        if (lineY >= top && lineY <= bottom - renderer.FONT_HEIGHT) {
                            drawString(renderer, line, 24, lineY, 0xFFFFFFFF);
                        }
                        lineY += renderer.FONT_HEIGHT + 2;
                    }
                    drawRect(24, y + entryHeight - 7, width - 32, y + entryHeight - 6, 0x44FFFFFF);
                }
                y += entryHeight;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int controlsTop() {
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        return config != null && config.pinnedLogEnabled ? height - 92 : height - 34;
    }

    private String entryText(LegacyTranslationLog.Entry entry) {
        LegacyConfig config = LegacyTranslationRuntime.currentConfig();
        return config != null && config.displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? entry.original + "\n" + entry.translated : entry.translated;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }
}

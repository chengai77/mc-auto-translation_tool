package org.universaltranslator.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.TranslationDisplayMode;

import java.io.IOException;
import java.util.List;

final class TranslationLogScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private int scroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

    TranslationLogScreen(Screen parent) {
        super(Component.translatable("screen.universal_translator.translation_log"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshLogWidgets();
    }

    private void refreshLogWidgets() {
        clearWidgets();
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(12, bottom, 90, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.log.clear"), button -> {
            FabricTranslationRuntime.clearTranslationHistory();
            scroll = 0;
            draggingScrollbar = false;
            refreshLogWidgets();
        }).bounds(108, bottom, 100, BUTTON_HEIGHT).build());
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        addRenderableWidget(Button.builder(pinnedText(), button -> {
            FabricConfig current = FabricTranslationRuntime.currentConfig();
            if (current != null) {
                savePinned(!current.pinnedLogEnabled, current.pinnedLogPreset, current.pinnedLogX,
                        current.pinnedLogY, current.pinnedLogWidth, current.pinnedLogScale);
            }
            refreshLogWidgets();
        }).bounds(width - 102, bottom, 90, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.log.source_settings"), button ->
                Minecraft.getInstance().gui.setScreen(new TranslationLogSourceScreen(this)))
                .bounds(216, bottom, 118, BUTTON_HEIGHT).build());
        if (config == null || !config.pinnedLogEnabled) {
            return;
        }
        int y = bottom - 54;
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.log.top_left"), button -> {
            FabricConfig current = FabricTranslationRuntime.currentConfig();
            if (current != null) {
                savePinned(true, "top-left", current.pinnedLogX, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale);
            }
            refreshLogWidgets();
        }).bounds(12, y, 74, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.log.top_right"), button -> {
            FabricConfig current = FabricTranslationRuntime.currentConfig();
            if (current != null) {
                savePinned(true, "top-right", current.pinnedLogX, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale);
            }
            refreshLogWidgets();
        }).bounds(92, y, 74, BUTTON_HEIGHT).build());
        addStepper(12, y + 26, "X", config.pinnedLogX, -4, 4, 0, 500, (current, value) ->
                savePinned(true, current.pinnedLogPreset, value, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale));
        addStepper(172, y + 26, "Y", config.pinnedLogY, -4, 4, 0, 500, (current, value) ->
                savePinned(true, current.pinnedLogPreset, current.pinnedLogX, value,
                        current.pinnedLogWidth, current.pinnedLogScale));
        addStepper(332, y + 26, Component.translatable("screen.universal_translator.log.width").getString(),
                config.pinnedLogWidth, -2, 2, 20, 90, (current, value) ->
                        savePinned(true, current.pinnedLogPreset, current.pinnedLogX, current.pinnedLogY,
                                value, current.pinnedLogScale));
        addStepper(492, y + 26, Component.translatable("screen.universal_translator.log.scale").getString(),
                config.pinnedLogScale, -5, 5, 70, 160, (current, value) ->
                        savePinned(true, current.pinnedLogPreset, current.pinnedLogX, current.pinnedLogY,
                                current.pinnedLogWidth, value));
    }

    private void addStepper(int x, int y, String label, int value, int decrement, int increment,
                            int minimum, int maximum, StepAction action) {
        int clampedX = Math.min(x, Math.max(12, width - 150));
        addRenderableWidget(Button.builder(Component.literal("-"), button -> step(value + decrement, minimum, maximum, action))
                .bounds(clampedX, y, 24, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal(label + " " + value), button -> { })
                .bounds(clampedX + 26, y, 82, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> step(value + increment, minimum, maximum, action))
                .bounds(clampedX + 110, y, 24, BUTTON_HEIGHT).build());
    }

    private void step(int value, int minimum, int maximum, StepAction action) {
        FabricConfig current = FabricTranslationRuntime.currentConfig();
        if (current == null) {
            return;
        }
        action.apply(current, Math.max(minimum, Math.min(maximum, value)));
        refreshLogWidgets();
    }

    private Component pinnedText() {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        return Component.translatable(config != null && config.pinnedLogEnabled
                ? "screen.universal_translator.log.unpin"
                : "screen.universal_translator.log.pin");
    }

    private void savePinned(boolean enabled, String preset, int x, int y, int width, int scale) {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        if (config == null) {
            return;
        }
        try {
            FabricConfig updated = config.withPinnedLogSettings(enabled, preset, x, y, width, scale);
            updated.save();
            FabricTranslationRuntime.updateConfig(updated);
        } catch (IOException ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, title, width / 2, 12, 0xFFFFFF);
        renderEntries(graphics);
    }

    private void renderEntries(GuiGraphicsExtractor graphics) {
        List<TranslationLog.Entry> entries = TranslationLog.entries();
        int top = 32;
        int bottom = controlsTop() - 8;
        int contentChars = Math.max(20, (width - 56) / 6);
        int maxScroll = maxScroll(entries, contentChars, bottom - top);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        int y = top - scroll;
        if (entries.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("screen.universal_translator.log.empty"),
                    width / 2, top + 24, 0xAAAAAA);
            return;
        }
        for (TranslationLog.Entry entry : entries) {
            List<String> lines = wrappedLines(entry, contentChars);
            int entryHeight = entryHeight(lines);
            if (y + entryHeight >= top && y <= bottom) {
                int lineY = y;
                for (String line : lines) {
                    if (lineY >= top && lineY <= bottom - 9) {
                        graphics.text(this.font, Component.literal(line), 24, lineY, 0xFFFFFF);
                    }
                    lineY += 10;
                }
                graphics.text(this.font, Component.literal("----------------------------------------"), 24,
                        y + entryHeight - 9, 0x777777);
            }
            y += entryHeight;
        }
        renderScrollbar(graphics, top, bottom, maxScroll);
    }

    private List<String> wrappedLines(TranslationLog.Entry entry, int contentChars) {
        return TranslationLogText.wrap(entryText(entry), contentChars);
    }

    private int entryHeight(List<String> lines) {
        return lines.size() * 10 + 14;
    }

    private int contentHeight(List<TranslationLog.Entry> entries, int contentChars) {
        int total = 0;
        for (TranslationLog.Entry entry : entries) {
            total += entryHeight(wrappedLines(entry, contentChars));
        }
        return total;
    }

    private int maxScroll(List<TranslationLog.Entry> entries, int contentChars, int viewportHeight) {
        return Math.max(0, contentHeight(entries, contentChars) - Math.max(0, viewportHeight));
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int top, int bottom, int maxScroll) {
        ScrollbarMetrics metrics = scrollbarMetrics(top, bottom, maxScroll);
        if (metrics == null) {
            return;
        }
        for (int y = top; y <= bottom - 9; y += 8) {
            graphics.text(this.font, Component.literal("|"), metrics.barX, y, 0x555555);
        }
        for (int y = metrics.thumbTop; y <= metrics.thumbTop + metrics.thumbHeight - 9; y += 8) {
            graphics.text(this.font, Component.literal("|"), metrics.barX, y, 0xFFFFFF);
        }
    }

    private ScrollbarMetrics scrollbarMetrics(int top, int bottom, int maxScroll) {
        if (maxScroll <= 0 || bottom <= top) {
            return null;
        }
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbTop = top + (trackHeight - thumbHeight) * scroll / maxScroll;
        return new ScrollbarMetrics(width - 18, thumbTop, thumbHeight, trackHeight);
    }

    private int scrollForMouseY(double mouseY, ScrollbarMetrics metrics, int top, int maxScroll) {
        int movable = Math.max(1, metrics.trackHeight - metrics.thumbHeight);
        int thumbTop = (int) Math.round(mouseY) - scrollbarDragOffset;
        int relative = Math.max(0, Math.min(movable, thumbTop - top));
        return relative * maxScroll / movable;
    }

    private int controlsTop() {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        return config != null && config.pinnedLogEnabled ? height - 92 : height - 34;
    }

    private String entryText(TranslationLog.Entry entry) {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        if (config != null && config.displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED) {
            return entry.original + "\n" + entry.translated;
        }
        return entry.translated;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int top = 32;
        int bottom = controlsTop() - 8;
        int contentChars = Math.max(20, (width - 56) / 6);
        int maxScroll = maxScroll(TranslationLog.entries(), contentChars, bottom - top);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 18)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0) {
            int top = 32;
            int bottom = controlsTop() - 8;
            int contentChars = Math.max(20, (width - 56) / 6);
            int maxScroll = maxScroll(TranslationLog.entries(), contentChars, bottom - top);
            ScrollbarMetrics metrics = scrollbarMetrics(top, bottom, maxScroll);
            if (metrics != null && mouseX >= metrics.barX - 4 && mouseX <= metrics.barX + 9
                    && mouseY >= top && mouseY <= bottom) {
                if (mouseY >= metrics.thumbTop && mouseY <= metrics.thumbTop + metrics.thumbHeight) {
                    scrollbarDragOffset = (int) Math.round(mouseY) - metrics.thumbTop;
                } else {
                    scrollbarDragOffset = metrics.thumbHeight / 2;
                    scroll = scrollForMouseY(mouseY, metrics, top, maxScroll);
                }
                draggingScrollbar = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbar && event.button() == 0) {
            int top = 32;
            int bottom = controlsTop() - 8;
            int contentChars = Math.max(20, (width - 56) / 6);
            int maxScroll = maxScroll(TranslationLog.entries(), contentChars, bottom - top);
            ScrollbarMetrics metrics = scrollbarMetrics(top, bottom, maxScroll);
            if (metrics != null) {
                scroll = scrollForMouseY(event.y(), metrics, top, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static final class ScrollbarMetrics {
        final int barX;
        final int thumbTop;
        final int thumbHeight;
        final int trackHeight;

        ScrollbarMetrics(int barX, int thumbTop, int thumbHeight, int trackHeight) {
            this.barX = barX;
            this.thumbTop = thumbTop;
            this.thumbHeight = thumbHeight;
            this.trackHeight = trackHeight;
        }
    }

    private interface StepAction {
        void apply(FabricConfig config, int value);
    }
}

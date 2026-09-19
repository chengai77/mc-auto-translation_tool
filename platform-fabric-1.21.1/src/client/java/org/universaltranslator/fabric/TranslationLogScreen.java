package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.universaltranslator.core.TranslationDisplayMode;

import java.io.IOException;
import java.util.List;

final class TranslationLogScreen extends Screen implements LocalTranslationScreen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTROL_WIDTH = 74;
    private final Screen parent;
    private int scroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private ButtonWidget pinnedButton;

    TranslationLogScreen(Screen parent) {
        super(Text.translatable("screen.universal_translator.translation_log"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();
        int bottom = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                .dimensions(12, bottom, 90, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.log.clear"), button -> {
            FabricTranslationRuntime.clearTranslationHistory();
            scroll = 0;
            draggingScrollbar = false;
            rebuildWidgets();
        }).dimensions(108, bottom, 100, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.log.source_settings"), button ->
                MinecraftClient.getInstance().setScreen(new TranslationLogSourceScreen(this))).dimensions(216, bottom, 118, BUTTON_HEIGHT).build());
        pinnedButton = addDrawableChild(ButtonWidget.builder(pinnedText(), button -> {
            FabricConfig config = FabricTranslationRuntime.currentConfig();
            if (config != null) {
                savePinned(!config.pinnedLogEnabled, config.pinnedLogPreset, config.pinnedLogX,
                        config.pinnedLogY, config.pinnedLogWidth, config.pinnedLogScale);
            }
            rebuildWidgets();
        }).dimensions(width - 102, bottom, 90, BUTTON_HEIGHT).build());

        FabricConfig config = FabricTranslationRuntime.currentConfig();
        if (config == null || !config.pinnedLogEnabled) {
            return;
        }
        int y = bottom - 54;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.log.top_left"), button -> {
            FabricConfig current = FabricTranslationRuntime.currentConfig();
            if (current != null) {
                savePinned(true, "top-left", current.pinnedLogX, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale);
            }
            rebuildWidgets();
        }).dimensions(12, y, CONTROL_WIDTH, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.log.top_right"), button -> {
            FabricConfig current = FabricTranslationRuntime.currentConfig();
            if (current != null) {
                savePinned(true, "top-right", current.pinnedLogX, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale);
            }
            rebuildWidgets();
        }).dimensions(92, y, CONTROL_WIDTH, BUTTON_HEIGHT).build());
        addStepper(12, y + 26, "X", config.pinnedLogX, -4, 4, 0, 500, (current, value) ->
                savePinned(true, current.pinnedLogPreset, value, current.pinnedLogY,
                        current.pinnedLogWidth, current.pinnedLogScale));
        addStepper(172, y + 26, "Y", config.pinnedLogY, -4, 4, 0, 500, (current, value) ->
                savePinned(true, current.pinnedLogPreset, current.pinnedLogX, value,
                        current.pinnedLogWidth, current.pinnedLogScale));
        addStepper(332, y + 26, Text.translatable("screen.universal_translator.log.width").getString(),
                config.pinnedLogWidth, -2, 2, 20, 90, (current, value) ->
                        savePinned(true, current.pinnedLogPreset, current.pinnedLogX, current.pinnedLogY,
                                value, current.pinnedLogScale));
        addStepper(492, y + 26, Text.translatable("screen.universal_translator.log.scale").getString(),
                config.pinnedLogScale, -5, 5, 70, 160, (current, value) ->
                        savePinned(true, current.pinnedLogPreset, current.pinnedLogX, current.pinnedLogY,
                                current.pinnedLogWidth, value));
    }

    private void addStepper(int x, int y, String label, int value, int decrement, int increment,
                            int minimum, int maximum, StepAction action) {
        int clampedX = Math.min(x, Math.max(12, width - 150));
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> step(value + decrement, minimum, maximum, action))
                .dimensions(clampedX, y, 24, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(label + " " + value), button -> { })
                .dimensions(clampedX + 26, y, 82, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> step(value + increment, minimum, maximum, action))
                .dimensions(clampedX + 110, y, 24, BUTTON_HEIGHT).build());
    }

    private void step(int value, int minimum, int maximum, StepAction action) {
        FabricConfig current = FabricTranslationRuntime.currentConfig();
        if (current == null) {
            return;
        }
        action.apply(current, Math.max(minimum, Math.min(maximum, value)));
        rebuildWidgets();
    }

    private Text pinnedText() {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        return Text.translatable(config != null && config.pinnedLogEnabled
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        renderEntries(context);
    }

    private void renderEntries(DrawContext context) {
        List<TranslationLog.Entry> entries = TranslationLog.entries();
        int top = 32;
        int bottom = controlsTop() - 8;
        int contentWidth = Math.max(120, width - 56);
        int maxScroll = maxScroll(entries, contentWidth, bottom - top);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        int y = top - scroll;
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("screen.universal_translator.log.empty"), width / 2, top + 24, 0xFFAAAAAA);
            return;
        }
        for (TranslationLog.Entry entry : entries) {
            List<OrderedText> lines = wrappedLines(entry, contentWidth);
            int entryHeight = entryHeight(lines);
            if (y + entryHeight >= top && y <= bottom) {
                int lineY = y;
                for (OrderedText line : lines) {
                    if (lineY >= top && lineY <= bottom - textRenderer.fontHeight) {
                        context.drawTextWithShadow(textRenderer, line, 24, lineY, 0xFFFFFFFF);
                    }
                    lineY += textRenderer.fontHeight + 2;
                }
                int separatorY = y + entryHeight - 7;
                context.fill(24, separatorY, width - 32, separatorY + 1, 0x44FFFFFF);
            }
            y += entryHeight;
        }
        renderScrollbar(context, top, bottom, maxScroll);
    }

    private List<OrderedText> wrappedLines(TranslationLog.Entry entry, int contentWidth) {
        return textRenderer.wrapLines(Text.literal(entryText(entry)), contentWidth);
    }

    private int entryHeight(List<OrderedText> lines) {
        return lines.size() * (textRenderer.fontHeight + 2) + 14;
    }

    private int contentHeight(List<TranslationLog.Entry> entries, int contentWidth) {
        int total = 0;
        for (TranslationLog.Entry entry : entries) {
            total += entryHeight(wrappedLines(entry, contentWidth));
        }
        return total;
    }

    private int maxScroll(List<TranslationLog.Entry> entries, int contentWidth, int viewportHeight) {
        return Math.max(0, contentHeight(entries, contentWidth) - Math.max(0, viewportHeight));
    }

    private void renderScrollbar(DrawContext context, int top, int bottom, int maxScroll) {
        ScrollbarMetrics metrics = scrollbarMetrics(top, bottom, maxScroll);
        if (metrics == null) {
            return;
        }
        context.fill(metrics.barX, top, metrics.barX + 5, bottom, 0x44000000);
        context.fill(metrics.barX, metrics.thumbTop, metrics.barX + 5,
                metrics.thumbTop + metrics.thumbHeight, 0xCCFFFFFF);
    }

    private ScrollbarMetrics scrollbarMetrics(int top, int bottom, int maxScroll) {
        if (maxScroll <= 0 || bottom <= top) {
            return null;
        }
        int barX = width - 18;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbTop = top + (trackHeight - thumbHeight) * scroll / maxScroll;
        return new ScrollbarMetrics(barX, thumbTop, thumbHeight, trackHeight);
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
        int contentWidth = Math.max(120, width - 56);
        int maxScroll = maxScroll(TranslationLog.entries(), contentWidth, bottom - top);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 18)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int top = 32;
            int bottom = controlsTop() - 8;
            int contentWidth = Math.max(120, width - 56);
            int maxScroll = maxScroll(TranslationLog.entries(), contentWidth, bottom - top);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            int top = 32;
            int bottom = controlsTop() - 8;
            int contentWidth = Math.max(120, width - 56);
            int maxScroll = maxScroll(TranslationLog.entries(), contentWidth, bottom - top);
            ScrollbarMetrics metrics = scrollbarMetrics(top, bottom, maxScroll);
            if (metrics != null) {
                scroll = scrollForMouseY(mouseY, metrics, top, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
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

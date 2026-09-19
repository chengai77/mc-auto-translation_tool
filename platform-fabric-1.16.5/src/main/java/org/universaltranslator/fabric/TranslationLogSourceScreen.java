package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.universaltranslator.core.TextKind;

import java.io.IOException;

final class TranslationLogSourceScreen extends Screen implements LocalTranslationScreen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;
    private static final TextKind[] LOG_SOURCE_KINDS = new TextKind[] {
            TextKind.TITLE,
            TextKind.SUBTITLE,
            TextKind.ACTION_BAR,
            TextKind.CHAT,
            TextKind.SYSTEM_MESSAGE,
            TextKind.BOSS_BAR,
            TextKind.SCOREBOARD_TITLE,
            TextKind.SCOREBOARD_LINE,
            TextKind.PLAYER_LIST_HEADER,
            TextKind.PLAYER_LIST_FOOTER,
            TextKind.CONTAINER_TITLE,
            TextKind.BOOK,
            TextKind.SIGN,
            TextKind.DISCONNECT_REASON,
            TextKind.HOLOGRAM,
            TextKind.OTHER
    };

    private final Screen parent;

    TranslationLogSourceScreen(Screen parent) {
        super(new TranslatableText("screen.universal_translator.log.source_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = columns();
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int startX = Math.max(12, (width - totalWidth) / 2);
        int startY = 36;
        for (int i = 0; i < LOG_SOURCE_KINDS.length; i++) {
            TextKind kind = LOG_SOURCE_KINDS[i];
            int x = startX + (i % columns) * (BUTTON_WIDTH + GAP);
            int y = startY + (i / columns) * (BUTTON_HEIGHT + 4);
            addButton(TranslatorUiText.button(
                    x, y, BUTTON_WIDTH, BUTTON_HEIGHT, sourceText(kind), button -> toggleSource(kind)));
        }
        addButton(TranslatorUiText.button(width / 2 - 50, height - 28, 100, BUTTON_HEIGHT,
                new TranslatableText("screen.universal_translator.back"), button -> onClose()));
    }

    private void refreshWidgets() {
        clearUiChildren();
        init();
    }

    /** 1.16.5 无 clearChildren */
    private void clearUiChildren() {
        this.children.clear();
        this.buttons.clear();
    }

    private int columns() {
        return Math.max(1, Math.min(3, (width - 24 + GAP) / (BUTTON_WIDTH + GAP)));
    }

    private Text sourceText(TextKind kind) {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        boolean allowed = config != null && config.logAllowedKinds.contains(kind);
        return new LiteralText(allowed ? "[x] " : "[ ] ").append(
                new TranslatableText("screen.universal_translator.log.kind." + kind.name().toLowerCase()));
    }

    private void toggleSource(TextKind kind) {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        if (config == null) {
            return;
        }
        boolean allowed = !config.logAllowedKinds.contains(kind);
        try {
            FabricConfig updated = config.withLogAllowedKind(kind, allowed);
            updated.save();
            FabricTranslationRuntime.updateConfig(updated);
            refreshWidgets();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        TranslatorUiText.drawCentered(matrices, textRenderer, title,
                width / 2, 12, 0xFFFFFFFF, width - 20);
    }

    @Override
    public void onClose() {
        MinecraftClient.getInstance().openScreen(parent);
    }
}

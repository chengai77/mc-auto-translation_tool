package org.universaltranslator.forge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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
        super(Text.translatable("screen.universal_translator.log.source_settings.title"));
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
            addDrawableChild(ButtonWidget.builder(sourceText(kind), button -> toggleSource(kind))
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.back"), button -> close())
                .dimensions(width / 2 - 50, height - 28, 100, BUTTON_HEIGHT).build());
    }

    private void refreshWidgets() {
        clearChildren();
        init();
    }

    private int columns() {
        return Math.max(1, Math.min(3, (width - 24 + GAP) / (BUTTON_WIDTH + GAP)));
    }

    private Text sourceText(TextKind kind) {
        ForgeConfig config = ForgeTranslationRuntime.currentConfig();
        boolean allowed = config != null && config.logAllowedKinds.contains(kind);
        return Text.literal(allowed ? "[x] " : "[ ] ").append(Text.translatable("screen.universal_translator.log.kind." + kind.name().toLowerCase()));
    }

    private void toggleSource(TextKind kind) {
        ForgeConfig config = ForgeTranslationRuntime.currentConfig();
        if (config == null) {
            return;
        }
        boolean allowed = !config.logAllowedKinds.contains(kind);
        try {
            ForgeConfig updated = config.withLogAllowedKind(kind, allowed);
            updated.save();
            ForgeTranslationRuntime.updateConfig(updated);
            refreshWidgets();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        TranslatorUiText.drawCentered(context, textRenderer, title,
                width / 2, 12, 0xFFFFFFFF, width - 20);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}


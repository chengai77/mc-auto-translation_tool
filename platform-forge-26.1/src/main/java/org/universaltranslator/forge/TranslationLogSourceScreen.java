package org.universaltranslator.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.TextKind;

import java.io.IOException;

final class TranslationLogSourceScreen extends Screen {
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
        super(Component.translatable("screen.universal_translator.log.source_settings.title"));
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
            addRenderableWidget(Button.builder(sourceText(kind), button -> toggleSource(kind))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.back"), button -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, BUTTON_HEIGHT).build());
    }

    private void refreshSourceWidgets() {
        clearWidgets();
        init();
    }

    private int columns() {
        return Math.max(1, Math.min(3, (width - 24 + GAP) / (BUTTON_WIDTH + GAP)));
    }

    private Component sourceText(TextKind kind) {
        ForgeConfig config = ForgeTranslationRuntime.currentConfig();
        boolean allowed = config != null && config.logAllowedKinds.contains(kind);
        return Component.literal(allowed ? "[x] " : "[ ] ").append(Component.translatable("screen.universal_translator.log.kind." + kind.name().toLowerCase()));
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
            refreshSourceWidgets();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, title, width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}


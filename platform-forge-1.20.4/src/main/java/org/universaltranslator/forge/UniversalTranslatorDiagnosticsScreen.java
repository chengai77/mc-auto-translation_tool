package org.universaltranslator.forge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;

import java.util.List;

/** 运行时诊断页 */
final class UniversalTranslatorDiagnosticsScreen extends Screen implements LocalTranslationScreen {
    private final Screen parent;

    UniversalTranslatorDiagnosticsScreen(Screen parent) {
        super(Text.translatable("screen.universal_translator.diagnostics.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.max(120, Math.min(220, this.width - 40));
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.universal_translator.diagnostics.back"), button -> close())
                .dimensions((this.width - width) / 2, this.height - 28, width, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TranslatorUiText.drawCentered(context, textRenderer, title,
                width / 2, 18, 0xFFFFFF, width - 20);
        TranslationDiagnosticsSnapshot snapshot = ForgeTranslationRuntime.diagnostics();
        List<String> lines = snapshot.localizedLines(UniversalTranslatorDiagnosticsScreen::tr);
        int left = Math.max(10, (width - Math.min(360, width - 20)) / 2);
        int lineWidth = Math.max(1, width - left - 10);
        int y = 43;
        for (String line : lines) {
            TranslatorUiText.drawLeft(context, textRenderer, Text.literal(line),
                    left, y, 0xD0D0D0, lineWidth);
            y += 17;
        }
        TranslatorUiText.drawCentered(context, textRenderer,
                Text.translatable("screen.universal_translator.diagnostics.note"),
                width / 2, Math.min(y + 7, height - 43), 0x808080, width - 20);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }
}

package org.universaltranslator.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.universaltranslator.core.TranslationDiagnosticsSnapshot;

import java.util.List;

/** 运行时诊断页 */
final class UniversalTranslatorDiagnosticsScreen extends Screen implements LocalTranslationScreen {
    private final Screen parent;

    UniversalTranslatorDiagnosticsScreen(Screen parent) {
        super(new TranslatableText("screen.universal_translator.diagnostics.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.max(120, Math.min(220, this.width - 40));
        addButton(TranslatorUiText.button(
                (this.width - width) / 2,
                this.height - 28,
                width,
                20,
                new TranslatableText("screen.universal_translator.diagnostics.back"),
                button -> onClose()));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        TranslatorUiText.drawCentered(matrices, textRenderer, title,
                width / 2, 18, 0xFFFFFF, width - 20);
        TranslationDiagnosticsSnapshot snapshot = FabricTranslationRuntime.diagnostics();
        List<String> lines = snapshot.localizedLines(UniversalTranslatorDiagnosticsScreen::tr);
        int left = Math.max(10, (width - Math.min(360, width - 20)) / 2);
        int lineWidth = Math.max(1, width - left - 10);
        int y = 43;
        for (String line : lines) {
            TranslatorUiText.drawLeft(matrices, textRenderer, new LiteralText(line),
                    left, y, 0xD0D0D0, lineWidth);
            y += 17;
        }
        TranslatorUiText.drawCentered(matrices, textRenderer,
                new TranslatableText("screen.universal_translator.diagnostics.note"),
                width / 2, Math.min(y + 7, height - 43), 0x808080, width - 20);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (client != null) {
            client.openScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return new TranslatableText(key, arguments).getString();
    }
}

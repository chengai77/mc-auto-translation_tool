package org.universaltranslator.neoforge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 选择翻译引擎 */
final class UniversalTranslatorProviderScreen extends Screen implements LocalTranslationScreen {
    private final UniversalTranslatorConfigScreen parent;

    UniversalTranslatorProviderScreen(UniversalTranslatorConfigScreen parent) {
        super(Text.translatable("screen.universal_translator.provider.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = this.width >= 240 ? 2 : 1;
        int totalWidth = Math.max(180, Math.min(420, this.width - 20));
        int gap = 8;
        int buttonWidth = (totalWidth - gap * (columns - 1)) / columns;
        int left = (this.width - totalWidth) / 2;
        int top = 48;
        addProviderButton(left, top, buttonWidth, 0, columns,
                "offline", "value.universal_translator.provider_offline");
        addProviderButton(left, top, buttonWidth, 1, columns,
                "libretranslate", "value.universal_translator.provider_libre");
        addProviderButton(left, top, buttonWidth, 2, columns,
                "tencent-hunyuan", "value.universal_translator.provider_tencent");
        addProviderButton(left, top, buttonWidth, 3, columns,
                "deepseek", "value.universal_translator.provider_deepseek");
        addProviderButton(left, top, buttonWidth, 4, columns,
                "dashscope", "value.universal_translator.provider_dashscope");
        addProviderButton(left, top, buttonWidth, 5, columns,
                "zhipu", "value.universal_translator.provider_zhipu");
        addProviderButton(left, top, buttonWidth, 6, columns,
                "kimi", "value.universal_translator.provider_kimi");
        addProviderButton(left, top, buttonWidth, 7, columns,
                "custom-api", "value.universal_translator.provider_llm");
        int rows = (8 + columns - 1) / columns;
        int backY = Math.min(this.height - 28, top + rows * 24 + 8);
        ButtonWidget back = addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.universal_translator.back"), button -> close())
                .dimensions(left, backY, totalWidth, 20).build());
        TranslatorUiText.setButtonMessage(this.textRenderer, back,
                Text.translatable("screen.universal_translator.back"));
    }

    private void addProviderButton(
            int left,
            int top,
            int width,
            int index,
            int columns,
            String provider,
            String labelKey
    ) {
        int x = left + (index % columns) * (width + 8);
        int y = top + (index / columns) * 24;
        Text label = optionLabel(provider, labelKey);
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(label, pressed -> {
            parent.selectProvider(provider);
            close();
        }).dimensions(x, y, width, 20).build());
        TranslatorUiText.setButtonMessage(this.textRenderer, button, label);
    }

    private Text optionLabel(String provider, String labelKey) {
        Text label = Text.translatable(labelKey);
        return provider.equalsIgnoreCase(parent.provider()) ? label.copy().formatted(Formatting.RED) : label;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TranslatorUiText.drawCentered(context, this.textRenderer, this.title,
                this.width / 2, 14, 0xFFFFFF, this.width - 20);
        TranslatorUiText.drawCentered(context, this.textRenderer,
                Text.translatable("screen.universal_translator.provider.current", Text.literal(parent.providerLabel())),
                this.width / 2, 30, 0xA0A0A0, this.width - 20);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}


package org.universaltranslator.forge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 选择翻译引擎 */
final class UniversalTranslatorProviderScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;

    UniversalTranslatorProviderScreen(UniversalTranslatorConfigScreen parent) {
        super(Text.translatable("screen.universal_translator.provider.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.max(180, Math.min(260, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 250) / 2);
        addProviderButton(left, top, width, "offline", "value.universal_translator.provider_offline");
        addProviderButton(left, top + 26, width, "libretranslate", "value.universal_translator.provider_libre");
        addProviderButton(left, top + 52, width, "tencent-hunyuan", "value.universal_translator.provider_tencent");
        addProviderButton(left, top + 78, width, "deepseek", "value.universal_translator.provider_deepseek");
        addProviderButton(left, top + 104, width, "dashscope", "value.universal_translator.provider_dashscope");
        addProviderButton(left, top + 130, width, "zhipu", "value.universal_translator.provider_zhipu");
        addProviderButton(left, top + 156, width, "kimi", "value.universal_translator.provider_kimi");
        addProviderButton(left, top + 182, width, "custom-api", "value.universal_translator.provider_llm");
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.back"), button -> close())
                .dimensions(left, top + 218, width, 20).build());
    }

    private void addProviderButton(int x, int y, int width, String provider, String labelKey) {
        addDrawableChild(ButtonWidget.builder(optionLabel(provider, labelKey), button -> {
            parent.selectProvider(provider);
            close();
        }).dimensions(x, y, width, 20).build());
    }

    private Text optionLabel(String provider, String labelKey) {
        Text label = Text.translatable(labelKey);
        return provider.equalsIgnoreCase(parent.provider()) ? label.copy().formatted(Formatting.RED) : label;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.universal_translator.provider.current", Text.literal(parent.providerLabel())),
                this.width / 2, 32, 0xA0A0A0);
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


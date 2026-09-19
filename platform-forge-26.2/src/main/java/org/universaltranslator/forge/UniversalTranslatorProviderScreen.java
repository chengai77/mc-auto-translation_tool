package org.universaltranslator.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 选择翻译引擎 */
final class UniversalTranslatorProviderScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;

    UniversalTranslatorProviderScreen(UniversalTranslatorConfigScreen parent) {
        super(Component.translatable("screen.universal_translator.provider.title"));
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
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.back"), button -> onClose())
                .bounds(left, top + 218, width, 20).build());
    }

    private void addProviderButton(int x, int y, int width, String provider, String labelKey) {
        addRenderableWidget(Button.builder(optionLabel(provider, labelKey), button -> {
            parent.selectProvider(provider);
            onClose();
        }).bounds(x, y, width, 20).build());
    }

    private Component optionLabel(String provider, String labelKey) {
        Component label = Component.translatable(labelKey);
        return provider.equalsIgnoreCase(parent.provider()) ? label.copy().withStyle(ChatFormatting.RED) : label;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("screen.universal_translator.provider.current",
                        Component.literal(parent.providerLabel())),
                this.width / 2, 32, 0xA0A0A0);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


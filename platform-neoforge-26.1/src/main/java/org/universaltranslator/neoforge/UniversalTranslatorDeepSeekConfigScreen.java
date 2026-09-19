package org.universaltranslator.neoforge;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** 官方API配置 */
final class UniversalTranslatorDeepSeekConfigScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;
    private final String provider;
    private final String initialModel;
    private final boolean hasStoredKey;
    private EditBox model;
    private EditBox apiKey;
    private String status = "";

    UniversalTranslatorDeepSeekConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String provider,
            String model,
            boolean hasStoredKey
    ) {
        super(Component.translatable("screen.universal_translator.official.title", parent.providerLabel()));
        this.parent = parent;
        this.provider = provider;
        this.initialModel = model == null || model.trim().isEmpty()
                ? parent.defaultOfficialModel(provider) : model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 114) / 2);
        model = addRenderableWidget(new EditBox(
                this.font, left, top, formWidth, 20, Component.empty()));
        model.setMaxLength(128);
        model.setValue(initialModel);
        configurePlaceholder(model, providerKey("model_hint"));
        apiKey = addRenderableWidget(new EditBox(
                this.font, left, top + 36, formWidth, 20, Component.empty()));
        apiKey.setMaxLength(512);
        configurePlaceholder(apiKey, hasStoredKey
                ? providerKey("key_saved_hint")
                : providerKey("key_empty_hint"));
        int gap = 8;
        int buttonWidth = (formWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.deepseek.save"), button -> save())
                .bounds(left, top + 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + buttonWidth + gap, top + 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.deepseek.clear"), button -> clearAll())
                .bounds(left + (buttonWidth + gap) * 2, top + 72, buttonWidth, 20).build());
    }

    private void save() {
        String modelValue = model.getValue().trim();
        String enteredKey = apiKey.getValue().trim();
        String keyValue = enteredKey.isEmpty() ? parent.officialApiKey(provider) : enteredKey;
        if (modelValue.isEmpty() || keyValue.isEmpty()) {
            status = tr("error.universal_translator.official_required");
            return;
        }
        parent.applyOfficialProviderSettings(provider, modelValue, keyValue);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 114) / 2);
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.text(this.font, Component.translatable(providerKey("model")),
                left, top - 11, 0xFFFFFF);
        graphics.text(this.font,
                Component.translatable(providerKey("api_key")),
                left, top + 25, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.centeredText(this.font, Component.literal(status),
                    this.width / 2, top + 98, 0xFF5555);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void configurePlaceholder(EditBox field, String key) {
        updatePlaceholder(field, key, field.getValue());
        field.setResponder(value -> updatePlaceholder(field, key, value));
    }

    private static void updatePlaceholder(EditBox field, String key, String value) {
        field.setSuggestion(value == null || value.isEmpty() ? tr(key) : "");
    }

    private String providerKey(String suffix) {
        return "screen.universal_translator." + provider + "." + suffix;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private void clearAll() {
        model.setValue("");
        apiKey.setValue("");
        parent.applyOfficialProviderSettings(provider, parent.defaultOfficialModel(provider), "");
        onClose();
    }
}


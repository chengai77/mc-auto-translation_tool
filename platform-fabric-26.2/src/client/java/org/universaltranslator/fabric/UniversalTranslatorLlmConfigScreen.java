package org.universaltranslator.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 自定义API配置 */
final class UniversalTranslatorLlmConfigScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;
    private final String initialEndpoint;
    private final String initialModel;
    private final boolean hasStoredKey;
    private EditBox endpoint;
    private EditBox model;
    private EditBox apiKey;
    private String status = "";

    UniversalTranslatorLlmConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String endpoint,
            String model,
            boolean hasStoredKey
    ) {
        super(Component.translatable("screen.universal_translator.llm.title"));
        this.parent = parent;
        this.initialEndpoint = endpoint;
        this.initialModel = model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        endpoint = addRenderableWidget(new EditBox(
                this.font, left, top, formWidth, 20, Component.empty()));
        endpoint.setMaxLength(512);
        endpoint.setValue(initialEndpoint);
        configurePlaceholder(endpoint, "screen.universal_translator.llm.endpoint_hint");
        model = addRenderableWidget(new EditBox(
                this.font, left, top + 36, formWidth, 20, Component.empty()));
        model.setMaxLength(128);
        model.setValue(initialModel);
        configurePlaceholder(model, "screen.universal_translator.llm.model_hint");
        apiKey = addRenderableWidget(new EditBox(
                this.font, left, top + 72, formWidth, 20, Component.empty()));
        apiKey.setMaxLength(512);
        configurePlaceholder(apiKey, hasStoredKey
                ? "screen.universal_translator.llm.key_saved_hint"
                : "screen.universal_translator.llm.key_empty_hint");
        int gap = 8;
        int buttonWidth = (formWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.llm.save"), button -> save())
                .bounds(left, top + 108, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + buttonWidth + gap, top + 108, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.llm.clear"), button -> clearAll())
                .bounds(left + (buttonWidth + gap) * 2, top + 108, buttonWidth, 20).build());
    }

    private void save() {
        String endpointValue = endpoint.getValue().trim();
        String modelValue = model.getValue().trim();
        if (endpointValue.isEmpty() || modelValue.isEmpty()) {
            status = tr("error.universal_translator.llm_required");
            return;
        }
        String enteredKey = apiKey.getValue().trim();
        String keyValue = enteredKey.isEmpty() ? parent.llmApiKey() : enteredKey;
        parent.applyLlmSettings(endpointValue, modelValue, keyValue);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("screen.universal_translator.llm.endpoint"),
                left, top - 11, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("screen.universal_translator.llm.model"), left, top + 25, 0xFFFFFF);
        graphics.text(this.font,
                Component.translatable("screen.universal_translator.llm.api_key"),
                left, top + 61, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.centeredText(this.font, Component.literal(status),
                    this.width / 2, top + 134, 0xFF5555);
        }
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

    private void configurePlaceholder(EditBox field, String key) {
        updatePlaceholder(field, key, field.getValue());
        field.setResponder(value -> updatePlaceholder(field, key, value));
    }

    private static void updatePlaceholder(EditBox field, String key, String value) {
        field.setSuggestion(value == null || value.isEmpty() ? tr(key) : "");
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private void clearAll() {
        endpoint.setValue("");
        model.setValue("");
        apiKey.setValue("");
        parent.clearLlmSettings();
        onClose();
    }
}

package org.universaltranslator.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Local-only editor for custom OpenAI-compatible API settings. */
final class UniversalTranslatorLlmConfigScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;
    private final String initialEndpoint;
    private final String initialModel;
    private final boolean hasStoredKey;
    private TextFieldWidget endpoint;
    private TextFieldWidget model;
    private TextFieldWidget apiKey;
    private String status = "";

    UniversalTranslatorLlmConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String endpoint,
            String model,
            boolean hasStoredKey
    ) {
        super(Text.translatable("screen.universal_translator.llm.title"));
        this.parent = parent;
        this.initialEndpoint = endpoint;
        this.initialModel = model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        endpoint = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top, width, 20, Text.empty()));
        endpoint.setMaxLength(512);
        endpoint.setText(initialEndpoint);
        configurePlaceholder(endpoint, "screen.universal_translator.llm.endpoint_hint");
        model = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top + 36, width, 20, Text.empty()));
        model.setMaxLength(128);
        model.setText(initialModel);
        configurePlaceholder(model, "screen.universal_translator.llm.model_hint");
        apiKey = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top + 72, width, 20, Text.empty()));
        apiKey.setMaxLength(512);
        configurePlaceholder(apiKey, hasStoredKey
                ? "screen.universal_translator.llm.key_saved_hint"
                : "screen.universal_translator.llm.key_empty_hint");
        int gap = 8;
        int buttonWidth = (width - gap * 2) / 3;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.llm.save"), button -> save())
                .dimensions(left, top + 108, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left + buttonWidth + gap, top + 108, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.llm.clear"), button -> clearAll())
                .dimensions(left + (buttonWidth + gap) * 2, top + 108, buttonWidth, 20).build());
    }

    private void save() {
        String endpointValue = endpoint.getText().trim();
        String modelValue = model.getText().trim();
        if (endpointValue.isEmpty() || modelValue.isEmpty()) {
            status = tr("error.universal_translator.llm_required");
            return;
        }
        String enteredKey = apiKey.getText().trim();
        String keyValue = enteredKey.isEmpty() ? parent.llmApiKey() : enteredKey;
        parent.applyLlmSettings(endpointValue, modelValue, keyValue);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.universal_translator.llm.endpoint"), left, top - 11, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.universal_translator.llm.model"), left, top + 25, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("screen.universal_translator.llm.api_key"), left, top + 61, 0xFFFFFF);
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
                    this.width / 2, top + 134, 0xFF5555);
        }
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

    private void configurePlaceholder(TextFieldWidget field, String key) {
        updatePlaceholder(field, key, field.getText());
        field.setChangedListener(value -> updatePlaceholder(field, key, value));
    }

    private static void updatePlaceholder(TextFieldWidget field, String key, String value) {
        field.setSuggestion(value == null || value.isEmpty() ? tr(key) : "");
    }

    private static String tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).getString();
    }

    private void clearAll() {
        endpoint.setText("");
        model.setText("");
        apiKey.setText("");
        parent.clearLlmSettings();
        close();
    }
}

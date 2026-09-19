package org.universaltranslator.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
/** 官方API配置 */
final class UniversalTranslatorDeepSeekConfigScreen extends Screen implements LocalTranslationScreen {
    private final UniversalTranslatorConfigScreen parent;
    private final String provider;
    private final String initialModel;
    private final boolean hasStoredKey;
    private TextFieldWidget model;
    private TextFieldWidget apiKey;
    private String status = "";

    UniversalTranslatorDeepSeekConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String provider,
            String model,
            boolean hasStoredKey
    ) {
        super(new TranslatableText("screen.universal_translator.official.title", parent.providerLabel()));
        this.parent = parent;
        this.provider = provider;
        this.initialModel = model == null || model.trim().isEmpty()
                ? parent.defaultOfficialModel(provider) : model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 114) / 2);
        model = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top, width, 20, TranslatorUiText.empty()));
        model.setMaxLength(128);
        model.setText(initialModel);
        configurePlaceholder(model, providerKey("model_hint"));
        apiKey = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top + 36, width, 20, TranslatorUiText.empty()));
        apiKey.setMaxLength(512);
        configurePlaceholder(apiKey, hasStoredKey
                ? providerKey("key_saved_hint")
                : providerKey("key_empty_hint"));
        int gap = 8;
        int buttonWidth = (width - gap * 2) / 3;
        addDrawableChild(TranslatorUiText.button(left, top + 72, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.deepseek.save"), button -> save()));
        addDrawableChild(TranslatorUiText.button(left + buttonWidth + gap, top + 72, buttonWidth, 20,
                new TranslatableText("gui.cancel"), button -> onClose()));
        addDrawableChild(TranslatorUiText.button(left + (buttonWidth + gap) * 2, top + 72, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.deepseek.clear"), button -> clearAll()));
    }

    private void save() {
        String modelValue = model.getText().trim();
        String enteredKey = apiKey.getText().trim();
        String keyValue = enteredKey.isEmpty() ? parent.officialApiKey(provider) : enteredKey;
        if (modelValue.isEmpty() || keyValue.isEmpty()) {
            status = tr("error.universal_translator.official_required");
            return;
        }
        parent.applyOfficialProviderSettings(provider, modelValue, keyValue);
        onClose();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 114) / 2);
        TranslatorUiText.drawCentered(matrices, this.textRenderer, this.title,
                this.width / 2, 18, 0xFFFFFF, this.width - 20);
        TranslatorUiText.drawLeft(matrices, this.textRenderer,
                new TranslatableText(providerKey("model")), left, top - 11, 0xFFFFFF, width);
        TranslatorUiText.drawLeft(matrices, this.textRenderer,
                new TranslatableText(providerKey("api_key")), left, top + 25, 0xFFFFFF, width);
        if (!status.isEmpty()) {
            TranslatorUiText.drawCentered(matrices, this.textRenderer, new LiteralText(status),
                    this.width / 2, top + 98, 0xFF5555, this.width - 20);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
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

    private String providerKey(String suffix) {
        return "screen.universal_translator." + provider + "." + suffix;
    }

    private static String tr(String key, Object... arguments) {
        return new TranslatableText(key, arguments).getString();
    }

    private void clearAll() {
        model.setText("");
        apiKey.setText("");
        parent.applyOfficialProviderSettings(provider, parent.defaultOfficialModel(provider), "");
        onClose();
    }
}

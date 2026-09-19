package org.universaltranslator.neoforge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** 混元凭证配置 */
final class UniversalTranslatorTencentConfigScreen extends Screen implements LocalTranslationScreen {
    private final UniversalTranslatorConfigScreen parent;
    private final String initialSecretId;
    private final String initialModel;
    private final boolean hasStoredKey;
    private TextFieldWidget secretId;
    private TextFieldWidget secretKey;
    private TextFieldWidget model;
    private String status = "";

    UniversalTranslatorTencentConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String secretId,
            String model,
            boolean hasStoredKey
    ) {
        super(Text.translatable("screen.universal_translator.tencent.title"));
        this.parent = parent;
        this.initialSecretId = secretId;
        this.initialModel = model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        secretId = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top, width, 20, Text.empty()));
        secretId.setMaxLength(256);
        secretId.setText(initialSecretId);
        configurePlaceholder(secretId, "screen.universal_translator.tencent.secret_id_hint");
        secretKey = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top + 36, width, 20, Text.empty()));
        secretKey.setMaxLength(512);
        configurePlaceholder(secretKey, hasStoredKey
                ? "screen.universal_translator.tencent.key_saved_hint"
                : "screen.universal_translator.tencent.key_empty_hint");
        model = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, top + 72, width, 20, Text.empty()));
        model.setMaxLength(128);
        model.setText(initialModel);
        configurePlaceholder(model, "screen.universal_translator.tencent.model_hint");
        int gap = 8;
        int colWidth = (width - 2 * gap) / 3;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.tencent.save"), button -> save())
                .dimensions(left, top + 108, colWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left + colWidth + gap, top + 108, colWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.tencent.clear"), button -> clearAll())
                .dimensions(left + 2 * (colWidth + gap), top + 108, colWidth, 20).build());
    }

    private void save() {
        String secretIdValue = secretId.getText().trim();
        String modelValue = model.getText().trim();
        String enteredKey = secretKey.getText().trim();
        String keyValue = enteredKey.isEmpty() ? parent.tencentSecretKey() : enteredKey;
        if (secretIdValue.isEmpty() || keyValue.isEmpty() || modelValue.isEmpty()) {
            status = tr("error.universal_translator.tencent_required");
            return;
        }
        parent.applyTencentSettings(secretIdValue, keyValue, modelValue);
        close();
    }

    private void clearAll() {
        secretId.setText("");
        secretKey.setText("");
        model.setText("");
        updatePlaceholder(secretId, "screen.universal_translator.tencent.secret_id_hint", "");
        updatePlaceholder(secretKey, "screen.universal_translator.tencent.key_empty_hint", "");
        updatePlaceholder(model, "screen.universal_translator.tencent.model_hint", "");
        parent.clearTencentSettings();
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - width) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        TranslatorUiText.drawCentered(context, this.textRenderer, this.title,
                this.width / 2, 18, 0xFFFFFF, this.width - 20);
        TranslatorUiText.drawLeft(context, this.textRenderer,
                Text.translatable("screen.universal_translator.tencent.secret_id"),
                left, top - 11, 0xFFFFFF, width);
        TranslatorUiText.drawLeft(context, this.textRenderer,
                Text.translatable("screen.universal_translator.tencent.secret_key"),
                left, top + 25, 0xFFFFFF, width);
        TranslatorUiText.drawLeft(context, this.textRenderer,
                Text.translatable("screen.universal_translator.tencent.model"),
                left, top + 61, 0xFFFFFF, width);
        if (!status.isEmpty()) {
            TranslatorUiText.drawCentered(context, this.textRenderer, Text.literal(status),
                    this.width / 2, top + 134, 0xFF5555, this.width - 20);
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
}

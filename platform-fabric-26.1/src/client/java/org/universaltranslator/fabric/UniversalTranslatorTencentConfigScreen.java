package org.universaltranslator.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 混元凭证配置 */
final class UniversalTranslatorTencentConfigScreen extends Screen {
    private final UniversalTranslatorConfigScreen parent;
    private final String initialSecretId;
    private final String initialModel;
    private final boolean hasStoredKey;
    private EditBox secretId;
    private EditBox secretKey;
    private EditBox model;
    private String status = "";

    UniversalTranslatorTencentConfigScreen(
            UniversalTranslatorConfigScreen parent,
            String secretId,
            String model,
            boolean hasStoredKey
    ) {
        super(Component.translatable("screen.universal_translator.tencent.title"));
        this.parent = parent;
        this.initialSecretId = secretId;
        this.initialModel = model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    protected void init() {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        secretId = addRenderableWidget(new EditBox(
                this.font, left, top, formWidth, 20, Component.empty()));
        secretId.setMaxLength(256);
        secretId.setValue(initialSecretId);
        configurePlaceholder(secretId, "screen.universal_translator.tencent.secret_id_hint");
        secretKey = addRenderableWidget(new EditBox(
                this.font, left, top + 36, formWidth, 20, Component.empty()));
        secretKey.setMaxLength(512);
        configurePlaceholder(secretKey, hasStoredKey
                ? "screen.universal_translator.tencent.key_saved_hint"
                : "screen.universal_translator.tencent.key_empty_hint");
        model = addRenderableWidget(new EditBox(
                this.font, left, top + 72, formWidth, 20, Component.empty()));
        model.setMaxLength(128);
        model.setValue(initialModel);
        configurePlaceholder(model, "screen.universal_translator.tencent.model_hint");
        int gap = 8;
        int colWidth = (formWidth - 2 * gap) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.tencent.save"), button -> save())
                .bounds(left, top + 108, colWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + colWidth + gap, top + 108, colWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.tencent.clear"), button -> clearAll())
                .bounds(left + 2 * (colWidth + gap), top + 108, colWidth, 20).build());
    }

    private void save() {
        String secretIdValue = secretId.getValue().trim();
        String modelValue = model.getValue().trim();
        String enteredKey = secretKey.getValue().trim();
        String keyValue = enteredKey.isEmpty() ? parent.tencentSecretKey() : enteredKey;
        if (secretIdValue.isEmpty() || keyValue.isEmpty() || modelValue.isEmpty()) {
            status = tr("error.universal_translator.tencent_required");
            return;
        }
        parent.applyTencentSettings(secretIdValue, keyValue, modelValue);
        onClose();
    }

    private void clearAll() {
        secretId.setValue("");
        secretKey.setValue("");
        model.setValue("");
        updatePlaceholder(secretId, "screen.universal_translator.tencent.secret_id_hint", "");
        updatePlaceholder(secretKey, "screen.universal_translator.tencent.key_empty_hint", "");
        updatePlaceholder(model, "screen.universal_translator.tencent.model_hint", "");
        parent.clearTencentSettings();
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int formWidth = Math.max(180, Math.min(360, this.width - 20));
        int left = (this.width - formWidth) / 2;
        int top = Math.max(42, (this.height - 150) / 2);
        graphics.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("screen.universal_translator.tencent.secret_id"),
                left, top - 11, 0xFFFFFF);
        graphics.text(this.font, Component.translatable("screen.universal_translator.tencent.secret_key"), left, top + 25, 0xFFFFFF);
        graphics.text(this.font,
                Component.translatable("screen.universal_translator.tencent.model"),
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

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}

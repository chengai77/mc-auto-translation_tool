package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

/** Tencent Hunyuan credential settings shared by Forge 1.8.9 and 1.12.2. */
final class LegacyTencentConfigScreen extends GuiScreen {
    private static final int SAVE = 1;
    private static final int CANCEL = 2;
    private static final int CLEAR = 3;

    private final LegacyConfigScreen parent;
    private final String initialSecretId;
    private final String initialModel;
    private final boolean hasStoredKey;
    private FontRenderer renderer;
    private GuiTextField secretId;
    private GuiTextField secretKey;
    private GuiTextField model;
    private String status = "";

    LegacyTencentConfigScreen(
            LegacyConfigScreen parent,
            String secretId,
            String model,
            boolean hasStoredKey
    ) {
        this.parent = parent;
        this.initialSecretId = secretId;
        this.initialModel = model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int fieldWidth = Math.max(180, Math.min(360, width - 20));
        int left = (width - fieldWidth) / 2;
        int top = Math.max(42, (height - 150) / 2);
        secretId = new GuiTextField(10, renderer, left, top, fieldWidth, 20);
        secretId.setMaxStringLength(256);
        secretId.setText(initialSecretId);
        secretKey = new GuiTextField(11, renderer, left, top + 36, fieldWidth, 20);
        secretKey.setMaxStringLength(512);
        model = new GuiTextField(12, renderer, left, top + 72, fieldWidth, 20);
        model.setMaxStringLength(128);
        model.setText(initialModel);
        int gap = 8;
        int colWidth = (fieldWidth - 2 * gap) / 3;
        buttonList.add(new GuiButton(SAVE, left, top + 108, colWidth, 20,
                tr("screen.universal_translator.tencent.save")));
        buttonList.add(new GuiButton(CANCEL, left + colWidth + gap, top + 108, colWidth, 20, tr("gui.cancel")));
        buttonList.add(new GuiButton(CLEAR, left + 2 * (colWidth + gap), top + 108, colWidth, 20,
                tr("screen.universal_translator.tencent.clear")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == CANCEL) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == CLEAR) {
            clearAll();
            return;
        }
        if (button.id != SAVE) {
            return;
        }
        String secretIdValue = secretId.getText().trim();
        String modelValue = model.getText().trim();
        String enteredKey = secretKey.getText().trim();
        String keyValue = enteredKey.isEmpty() ? parent.tencentSecretKey() : enteredKey;
        if (secretIdValue.isEmpty() || keyValue.isEmpty() || modelValue.isEmpty()) {
            status = tr("error.universal_translator.tencent_required");
            return;
        }
        parent.applyTencentSettings(secretIdValue, keyValue, modelValue);
        mc.displayGuiScreen(parent);
    }

    private void clearAll() {
        secretId.setText("");
        secretKey.setText("");
        model.setText("");
        parent.clearTencentSettings();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void updateScreen() {
        secretId.updateCursorCounter();
        secretKey.updateCursorCounter();
        model.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (secretId.textboxKeyTyped(typedChar, keyCode)
                || secretKey.textboxKeyTyped(typedChar, keyCode)
                || model.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        secretId.mouseClicked(mouseX, mouseY, mouseButton);
        secretKey.mouseClicked(mouseX, mouseY, mouseButton);
        model.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int fieldWidth = Math.max(180, Math.min(360, width - 20));
        int left = (width - fieldWidth) / 2;
        int top = Math.max(42, (height - 150) / 2);
        drawCenteredString(renderer, tr("screen.universal_translator.tencent.title"), width / 2, 18, 0xFFFFFF);
        drawString(renderer, tr("screen.universal_translator.tencent.secret_id"), left, top - 11, 0xFFFFFF);
        drawString(renderer, tr("screen.universal_translator.tencent.secret_key"), left, top + 25, 0xFFFFFF);
        drawString(renderer, tr("screen.universal_translator.tencent.model"), left, top + 61, 0xFFFFFF);
        secretId.drawTextBox();
        secretKey.drawTextBox();
        model.drawTextBox();
        drawPlaceholder(secretId, tr("screen.universal_translator.tencent.secret_id_hint"), left, top);
        drawPlaceholder(secretKey, tr(hasStoredKey
                ? "screen.universal_translator.tencent.key_saved_hint"
                : "screen.universal_translator.tencent.key_empty_hint"), left, top + 36);
        drawPlaceholder(model, tr("screen.universal_translator.tencent.model_hint"), left, top + 72);
        if (!status.isEmpty()) {
            drawCenteredString(renderer, status, width / 2, top + 134, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawPlaceholder(GuiTextField field, String text, int x, int y) {
        if (field.getText().isEmpty()) {
            drawString(renderer, text, x + 4, y + 6, 0xA0A0A0);
        }
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }
}

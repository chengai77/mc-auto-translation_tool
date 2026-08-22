package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

/** 旧版官方API */
final class LegacyDeepSeekConfigScreen extends GuiScreen {
    private static final int SAVE = 1;
    private static final int CANCEL = 2;
    private static final int CLEAR = 3;
    private final LegacyConfigScreen parent;
    private final String provider;
    private final String initialModel;
    private final boolean hasStoredKey;
    private FontRenderer renderer;
    private GuiTextField model;
    private GuiTextField apiKey;
    private String status = "";

    LegacyDeepSeekConfigScreen(LegacyConfigScreen parent, String provider, String model, boolean hasStoredKey) {
        this.parent = parent;
        this.provider = provider;
        this.initialModel = model == null || model.trim().isEmpty()
                ? parent.defaultOfficialModel(provider) : model;
        this.hasStoredKey = hasStoredKey;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int fieldWidth = Math.max(180, Math.min(360, width - 20));
        int left = (width - fieldWidth) / 2;
        int top = Math.max(42, (height - 114) / 2);
        model = new GuiTextField(10, renderer, left, top, fieldWidth, 20);
        model.setMaxStringLength(128);
        model.setText(initialModel);
        apiKey = new GuiTextField(11, renderer, left, top + 36, fieldWidth, 20);
        apiKey.setMaxStringLength(512);
        int gap = 8;
        int colWidth = (fieldWidth - 2 * gap) / 3;
        buttonList.add(new GuiButton(SAVE, left, top + 72, colWidth, 20,
                tr("screen.universal_translator.deepseek.save")));
        buttonList.add(new GuiButton(CANCEL, left + colWidth + gap, top + 72, colWidth, 20, tr("gui.cancel")));
        buttonList.add(new GuiButton(CLEAR, left + 2 * (colWidth + gap), top + 72, colWidth, 20,
                tr("screen.universal_translator.deepseek.clear")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == CANCEL) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == CLEAR) {
            parent.applyOfficialProviderSettings(provider, parent.defaultOfficialModel(provider), "");
            mc.displayGuiScreen(parent);
            return;
        }
        String modelValue = model.getText().trim();
        String enteredKey = apiKey.getText().trim();
        String keyValue = enteredKey.isEmpty() ? parent.officialApiKey(provider) : enteredKey;
        if (modelValue.isEmpty() || keyValue.isEmpty()) {
            status = tr("error.universal_translator.official_required");
            return;
        }
        parent.applyOfficialProviderSettings(provider, modelValue, keyValue);
        mc.displayGuiScreen(parent);
    }

    @Override
    public void updateScreen() {
        model.updateCursorCounter();
        apiKey.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (model.textboxKeyTyped(typedChar, keyCode) || apiKey.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        model.mouseClicked(mouseX, mouseY, mouseButton);
        apiKey.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int fieldWidth = Math.max(180, Math.min(360, width - 20));
        int left = (width - fieldWidth) / 2;
        int top = Math.max(42, (height - 114) / 2);
        drawCenteredString(renderer, tr("screen.universal_translator.official.title", parent.providerLabel()), width / 2, 18, 0xFFFFFF);
        drawString(renderer, tr(providerKey("model")), left, top - 11, 0xFFFFFF);
        drawString(renderer, tr(providerKey("api_key")), left, top + 25, 0xFFFFFF);
        model.drawTextBox();
        apiKey.drawTextBox();
        drawPlaceholder(model, tr(providerKey("model_hint")), left, top);
        drawPlaceholder(apiKey, tr(hasStoredKey
                ? providerKey("key_saved_hint")
                : providerKey("key_empty_hint")), left, top + 36);
        if (!status.isEmpty()) {
            drawCenteredString(renderer, status, width / 2, top + 98, 0xFF5555);
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

    private String providerKey(String suffix) {
        return "screen.universal_translator." + provider + "." + suffix;
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }
}

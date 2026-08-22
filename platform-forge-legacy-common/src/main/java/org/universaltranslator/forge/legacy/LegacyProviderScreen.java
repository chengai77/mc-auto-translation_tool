package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

/** 旧版引擎选择 */
final class LegacyProviderScreen extends GuiScreen {
    private static final int BACK = 1;
    private final LegacyConfigScreen parent;
    private FontRenderer renderer;

    LegacyProviderScreen(LegacyConfigScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int buttonWidth = Math.max(180, Math.min(260, width - 20));
        int left = (width - buttonWidth) / 2;
        int top = Math.max(42, (height - 250) / 2);
        addProviderButton(left, top, buttonWidth, "offline", "value.universal_translator.provider_offline");
        addProviderButton(left, top + 26, buttonWidth, "libretranslate", "value.universal_translator.provider_libre");
        addProviderButton(left, top + 52, buttonWidth, "tencent-hunyuan", "value.universal_translator.provider_tencent");
        addProviderButton(left, top + 78, buttonWidth, "deepseek", "value.universal_translator.provider_deepseek");
        addProviderButton(left, top + 104, buttonWidth, "dashscope", "value.universal_translator.provider_dashscope");
        addProviderButton(left, top + 130, buttonWidth, "zhipu", "value.universal_translator.provider_zhipu");
        addProviderButton(left, top + 156, buttonWidth, "kimi", "value.universal_translator.provider_kimi");
        addProviderButton(left, top + 182, buttonWidth, "custom-api", "value.universal_translator.provider_llm");
        buttonList.add(new GuiButton(BACK, left, top + 218, buttonWidth, 20,
                tr("screen.universal_translator.back")));
    }

    private void addProviderButton(int x, int y, int width, String provider, String labelKey) {
        String label = tr(labelKey);
        if (provider.equalsIgnoreCase(parent.provider())) {
            label = "§c" + label;
        }
        buttonList.add(new GuiButton(buttonList.size() + 10, x, y, width, 20, label));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BACK) {
            mc.displayGuiScreen(parent);
            return;
        }
        String[] providers = {"offline", "libretranslate", "tencent-hunyuan", "deepseek", "dashscope", "zhipu", "kimi", "custom-api"};
        int index = button.id - 10;
        if (index >= 0 && index < providers.length) {
            parent.selectProvider(providers[index]);
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(renderer, tr("screen.universal_translator.provider.title"), width / 2, 18, 0xFFFFFF);
        drawCenteredString(renderer,
                tr("screen.universal_translator.provider.current", parent.providerLabel()),
                width / 2, 32, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }

}

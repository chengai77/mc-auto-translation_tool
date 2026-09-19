package org.universaltranslator.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.universaltranslator.core.TranslationDisplayMode;
import org.universaltranslator.core.OfflineModel;
import org.universaltranslator.core.TargetLanguage;
import org.universaltranslator.core.TranslationStatusLocalizer;
import org.universaltranslator.core.TranslationTextColor;

/** U键设置页 */
final class UniversalTranslatorConfigScreen extends Screen implements LocalTranslationScreen {
    private final Screen parent;
    private final FabricConfig original;
    private boolean enabled;
    private boolean translateChat;
    private boolean translateOther;
    private boolean translateOutgoing;
    private boolean diskCache;
    private boolean offlineAutoDownload;
    private OfflineModel offlineModel;
    private TranslationDisplayMode displayMode;
    private boolean translateEnglishOnly;
    private TranslationTextColor translatedTextColor;
    private String provider;
    private String llmEndpoint;
    private String llmApiKey;
    private String llmModel;
    private String tencentSecretId;
    private String tencentSecretKey;
    private String tencentModel;
    private String deepSeekApiKey;
    private String deepSeekModel;
    private String dashScopeApiKey;
    private String dashScopeModel;
    private String zhipuApiKey;
    private String zhipuModel;
    private String kimiApiKey;
    private String kimiModel;
    private TextFieldWidget targetLanguage;
    private TextFieldWidget endpoint;
    private ButtonWidget enabledButton;
    private ButtonWidget chatButton;
    private ButtonWidget otherButton;
    private ButtonWidget cacheButton;
    private ButtonWidget providerButton;
    private ButtonWidget displayButton;
    private ButtonWidget downloadButton;
    private ButtonWidget modelButton;
    private ButtonWidget cacheEditorButton;
    private ButtonWidget mixedTextButton;
    private ButtonWidget colorButton;
    private ButtonWidget outgoingButton;
    private ButtonWidget targetLanguageButton;
    private String status = "";

    UniversalTranslatorConfigScreen(Screen parent, FabricConfig config) {
        super(new TranslatableText("screen.universal_translator.settings.title"));
        this.parent = parent;
        this.original = config;
        this.enabled = config.enabled;
        this.translateChat = config.translateChat;
        this.translateOther = config.translateOther;
        this.translateOutgoing = config.translateOutgoing;
        this.diskCache = config.diskCache;
        this.offlineAutoDownload = config.offlineAutoDownload;
        this.offlineModel = config.offlineModel;
        this.displayMode = config.displayMode;
        this.translateEnglishOnly = config.translateEnglishOnly;
        this.translatedTextColor = config.translatedTextColor;
        this.provider = config.provider;
        this.llmEndpoint = config.llmEndpoint;
        this.llmApiKey = config.llmApiKey;
        this.llmModel = config.llmModel;
        this.tencentSecretId = config.tencentSecretId;
        this.tencentSecretKey = config.tencentSecretKey;
        this.tencentModel = config.tencentModel;
        this.deepSeekApiKey = config.deepSeekApiKey;
        this.deepSeekModel = config.deepSeekModel;
        this.dashScopeApiKey = config.dashScopeApiKey;
        this.dashScopeModel = config.dashScopeModel;
        this.zhipuApiKey = config.zhipuApiKey;
        this.zhipuModel = config.zhipuModel;
        this.kimiApiKey = config.kimiApiKey;
        this.kimiModel = config.kimiModel;
    }

    @Override
    protected void init() {
        String targetValue = targetLanguage == null
                ? defaultTargetLanguage(original.targetLanguage) : targetLanguage.getText();
        String endpointValue = endpoint == null ? original.endpoint : endpoint.getText();
        Layout layout = layout();
        int left = layout.left;
        this.enabledButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(0), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    enabled = !enabled;
                    refreshLabels();
                }));
        this.cacheButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(0), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    diskCache = !diskCache;
                    refreshLabels();
                }));
        this.chatButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(1), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    translateChat = !translateChat;
                    refreshLabels();
                }));
        this.otherButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(1), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    translateOther = !translateOther;
                    refreshLabels();
                }));
        this.providerButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(2), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    if (this.client != null) {
                        this.client.setScreen(new UniversalTranslatorProviderScreen(this));
                    }
                }));
        this.displayButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(2), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    displayMode = displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                            ? TranslationDisplayMode.TRANSLATED_ONLY
                            : TranslationDisplayMode.ORIGINAL_AND_TRANSLATED;
                    refreshLabels();
                }));
        this.mixedTextButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(3), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    translateEnglishOnly = !translateEnglishOnly;
                    refreshLabels();
                }));
        this.colorButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(3), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    translatedTextColor = translatedTextColor.next();
                    refreshLabels();
                }));
        this.downloadButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(4), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    if (isLlm()) {
                        if (this.client != null) {
                            this.client.setScreen(new UniversalTranslatorLlmConfigScreen(
                                    this, llmEndpoint, llmModel, !llmApiKey.isEmpty()));
                        }
                    } else if (isOfficialProvider()) {
                        if (this.client != null) {
                            this.client.setScreen(new UniversalTranslatorDeepSeekConfigScreen(
                                    this, provider, officialModel(provider),
                                    !officialApiKey(provider).isEmpty()));
                        }
                    } else if (isTencent()) {
                        if (this.client != null) {
                            this.client.setScreen(new UniversalTranslatorTencentConfigScreen(
                                    this, tencentSecretId, tencentModel, !tencentSecretKey.isEmpty()));
                        }
                    } else {
                        offlineAutoDownload = !offlineAutoDownload;
                    }
                    refreshLabels();
                }));
        this.outgoingButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(4), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    translateOutgoing = !translateOutgoing;
                    refreshLabels();
                }));
        this.modelButton = addDrawableChild(TranslatorUiText.button(
                left, layout.row(5), layout.buttonWidth, 20, TranslatorUiText.empty(), button -> {
                    offlineModel = offlineModel.next();
                    refreshLabels();
                }));
        this.cacheEditorButton = addDrawableChild(TranslatorUiText.button(
                layout.right, layout.row(5), layout.buttonWidth, 20,
                new TranslatableText("screen.universal_translator.option.cache_editor"), button -> {
                    if (this.client != null) {
                        this.client.setScreen(new UniversalTranslatorCacheScreen(this));
                    }
                }));
        setButtonLabel(cacheEditorButton,
                new TranslatableText("screen.universal_translator.option.cache_editor"));
        int presetWidth = Math.max(46, Math.min(68, layout.buttonWidth / 2));
        int languageWidth = layout.buttonWidth - presetWidth - 4;
        this.targetLanguage = addDrawableChild(new TextFieldWidget(
                this.textRenderer, left, layout.targetY, languageWidth, 20,
                new TranslatableText("screen.universal_translator.target_language")));
        this.targetLanguage.setMaxLength(32);
        this.targetLanguage.setText(targetValue);
        this.targetLanguageButton = addDrawableChild(TranslatorUiText.button(
                left + languageWidth + 4, layout.targetY, presetWidth, 20,
                TranslatorUiText.empty(), button -> {
                    targetLanguage.setText(TargetLanguage.nextPreset(targetLanguage.getText()));
                    refreshLabels();
                }));
        this.endpoint = addDrawableChild(new TextFieldWidget(
                this.textRenderer, layout.right, layout.targetY, layout.buttonWidth, 20,
                new TranslatableText("screen.universal_translator.endpoint")));
        this.endpoint.setMaxLength(512);
        this.endpoint.setText(endpointValue);
        this.endpoint.setSuggestion(endpointPlaceholder(endpointValue));
        this.endpoint.setChangedListener(value ->
                this.endpoint.setSuggestion(endpointPlaceholder(value)));
        addDrawableChild(TranslatorUiText.button(left, layout.saveY, layout.buttonWidth, 20,
                new TranslatableText("screen.universal_translator.save"), button -> saveAndApply()));
        addDrawableChild(TranslatorUiText.button(layout.right, layout.saveY, layout.buttonWidth, 20,
                new TranslatableText("gui.cancel"), button -> onClose()));
        refreshLabels();
    }

    private void refreshLabels() {
        setButtonLabel(enabledButton,
                new TranslatableText("screen.universal_translator.option.automatic", onOff(enabled)));
        setButtonLabel(chatButton,
                new TranslatableText("screen.universal_translator.option.chat", onOff(translateChat)));
        setButtonLabel(otherButton,
                new TranslatableText("screen.universal_translator.option.other", onOff(translateOther)));
        setButtonLabel(cacheButton,
                new TranslatableText("screen.universal_translator.option.cache", onOff(diskCache)));
        setButtonLabel(providerButton,
                new TranslatableText("screen.universal_translator.option.change_provider"));
        setButtonLabel(displayButton, new TranslatableText("screen.universal_translator.option.display",
                tr(displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                        ? "value.universal_translator.display_bilingual"
                        : "value.universal_translator.display_translated")));
        setButtonLabel(mixedTextButton,
                new TranslatableText("screen.universal_translator.option.mixed", onOff(translateEnglishOnly)));
        setButtonLabel(colorButton,
                new TranslatableText("screen.universal_translator.option.color", colorLabel(translatedTextColor)));
        setButtonLabel(downloadButton, isLlm()
                ? new TranslatableText("screen.universal_translator.option.llm_settings")
                : (isOfficialProvider()
                ? new TranslatableText("screen.universal_translator.option.official_settings", providerLabel())
                : (isTencent()
                ? new TranslatableText("screen.universal_translator.option.tencent_settings")
                : new TranslatableText("screen.universal_translator.option.download", onOff(offlineAutoDownload)))));
        setButtonLabel(modelButton,
                new TranslatableText("screen.universal_translator.option.model", offlineModel.displayName()));
        setButtonLabel(outgoingButton,
                new TranslatableText("screen.universal_translator.option.outgoing", onOff(translateOutgoing)));
        setButtonLabel(targetLanguageButton,
                new TranslatableText("screen.universal_translator.option.target_preset",
                        TargetLanguage.displayName(targetLanguage.getText())));
        downloadButton.active = isOffline() || isLlm() || isOfficialProvider() || isTencent();
        modelButton.active = isOffline();
    }

    private void setButtonLabel(ButtonWidget button, Text message) {
        TranslatorUiText.setButtonMessage(this.textRenderer, button, message);
    }

    private static String onOff(boolean value) {
        return tr(value ? "value.universal_translator.on" : "value.universal_translator.off");
    }

    private static boolean isFailureStatus(String value) {
        return TranslationStatusLocalizer.isFailure(value);
    }

    private void saveAndApply() {
        boolean runtimeChanged = false;
        try {
            if (targetLanguage.getText().trim().isEmpty()) {
                throw new IllegalArgumentException(tr("error.universal_translator.target_required"));
            }
            String selectedProvider = isLlm() ? "custom-api" : provider;
            if ("custom-api".equalsIgnoreCase(selectedProvider)
                    && (llmEndpoint.trim().isEmpty() || llmModel.trim().isEmpty())) {
                throw new IllegalArgumentException(tr("error.universal_translator.llm_required"));
            }
            if (FabricConfig.isOfficialProvider(selectedProvider)
                    && (officialApiKey(selectedProvider).trim().isEmpty() || officialModel(selectedProvider).trim().isEmpty())) {
                throw new IllegalArgumentException(tr("error.universal_translator.official_required"));
            }
            FabricConfig updated = buildConfig();
            if (updated.enabled && "tencent-hunyuan".equalsIgnoreCase(updated.provider)
                    && (updated.tencentSecretId.isEmpty() || updated.tencentSecretKey.isEmpty())) {
                throw new IllegalArgumentException(tr("error.universal_translator.tencent_credentials"));
            }
            if (updated.enabled && FabricConfig.isOfficialProvider(updated.provider)
                    && updated.officialApiKey(updated.provider).isEmpty()) {
                throw new IllegalArgumentException(tr("error.universal_translator.official_credentials"));
            }
            if (updated.enabled) {
                updated.validateProviderConfiguration();
            }
            runtimeChanged = true;
            FabricTranslationRuntime.initialize(updated);
            updated.save();
            status = tr("status.universal_translator.saved");
            onClose();
        } catch (Exception exception) {
            if (runtimeChanged) {
                try {
                    FabricTranslationRuntime.initialize(original);
                } catch (Exception restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            status = tr("status.universal_translator.save_failed", exception.getMessage());
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        Layout layout = layout();
        TranslatorUiText.drawCentered(matrices, this.textRenderer, this.title,
                this.width / 2, layout.titleY, 0xFFFFFF, this.width - 20);
        TranslatorUiText.drawLeft(matrices, this.textRenderer,
                new TranslatableText("screen.universal_translator.target_language_hint"),
                layout.left, layout.targetY - 11, 0xA0A0A0, layout.buttonWidth);
        TranslatorUiText.drawLeft(matrices, this.textRenderer,
                new TranslatableText("screen.universal_translator.endpoint_hint"),
                layout.right, layout.targetY - 11, 0xA0A0A0, layout.buttonWidth);
        String rawRuntimeStatus = FabricTranslationRuntime.status();
        String runtimeStatus = TranslationStatusLocalizer.localize(rawRuntimeStatus,
                UniversalTranslatorConfigScreen::tr);
        int belowSave = layout.saveY + 28;
        int messageY = belowSave <= this.height - 10 ? belowSave : layout.saveY - 14;
        if (!status.isEmpty()) {
            TranslatorUiText.drawCentered(matrices, this.textRenderer, new LiteralText(status),
                    this.width / 2, messageY, 0xFF5555, this.width - 20);
        } else if (!runtimeStatus.isEmpty()) {
            TranslatorUiText.drawCentered(matrices, this.textRenderer, new LiteralText(runtimeStatus),
                    this.width / 2, messageY,
                    isFailureStatus(rawRuntimeStatus) ? 0xFF5555 : 0x55FF55, this.width - 20);
        } else if (layout.saveY - layout.endpointY >= 52) {
            int infoY = layout.endpointY + 28;
            TranslatorUiText.drawCentered(matrices, this.textRenderer,
                    new TranslatableText(isOffline()
                            ? "screen.universal_translator.info.offline"
                            : "screen.universal_translator.info.api"),
                    this.width / 2, infoY, 0xFFAA55, this.width - 20);
            TranslatorUiText.drawCentered(matrices, this.textRenderer,
                    new TranslatableText("screen.universal_translator.info.keybind"),
                    this.width / 2, infoY + 15, 0xA0A0A0, this.width - 20);
        }
        super.render(matrices, mouseX, mouseY, delta);
        Text watermark = new LiteralText("由承挨二次开发");
        this.textRenderer.drawWithShadow(matrices, watermark,
                Math.max(4, this.width - this.textRenderer.getWidth(watermark) - 6),
                Math.max(4, this.height - this.textRenderer.fontHeight - 4), 0x88B0B0B0);
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

    private boolean isTencent() {
        return "tencent-hunyuan".equalsIgnoreCase(provider);
    }

    private boolean isOfficialProvider() {
        return FabricConfig.isOfficialProvider(provider);
    }

    private boolean isOffline() {
        return "offline".equalsIgnoreCase(provider);
    }

    private boolean isLlm() {
        return "custom-api".equalsIgnoreCase(provider)
                || "openai-compatible".equalsIgnoreCase(provider);
    }

    String providerLabel() {
        if (isOffline()) {
            return tr("value.universal_translator.provider_offline");
        }
        if (isTencent()) {
            return tr("value.universal_translator.provider_tencent");
        }
        if ("deepseek".equalsIgnoreCase(provider)) {
            return tr("value.universal_translator.provider_deepseek");
        }
        if ("dashscope".equalsIgnoreCase(provider)) {
            return tr("value.universal_translator.provider_dashscope");
        }
        if ("zhipu".equalsIgnoreCase(provider)) {
            return tr("value.universal_translator.provider_zhipu");
        }
        if ("kimi".equalsIgnoreCase(provider)) {
            return tr("value.universal_translator.provider_kimi");
        }
        return isLlm() ? tr("value.universal_translator.provider_llm") : "Libre";
    }

    String provider() {
        return provider;
    }

    void selectProvider(String provider) {
        this.provider = provider == null || provider.trim().isEmpty() ? "offline" : provider.trim();
        refreshLabels();
    }

    void applyLlmSettings(String endpoint, String model, String apiKey) {
        this.llmEndpoint = endpoint;
        this.llmModel = model;
        this.llmApiKey = apiKey;
    }

    String llmApiKey() {
        return llmApiKey;
    }

    void applyTencentSettings(String secretId, String secretKey, String model) {
        this.tencentSecretId = secretId;
        this.tencentSecretKey = secretKey;
        this.tencentModel = model;
    }

    String tencentSecretKey() {
        return tencentSecretKey;
    }

    void applyOfficialProviderSettings(String selectedProvider, String model, String apiKey) {
        String normalized = normalizeOfficialProvider(selectedProvider);
        if ("dashscope".equals(normalized)) {
            this.dashScopeModel = model;
            this.dashScopeApiKey = apiKey;
        } else if ("zhipu".equals(normalized)) {
            this.zhipuModel = model;
            this.zhipuApiKey = apiKey;
        } else if ("kimi".equals(normalized)) {
            this.kimiModel = model;
            this.kimiApiKey = apiKey;
        } else {
            this.deepSeekModel = model;
            this.deepSeekApiKey = apiKey;
        }
    }

    String officialApiKey(String selectedProvider) {
        String normalized = normalizeOfficialProvider(selectedProvider);
        if ("dashscope".equals(normalized)) {
            return dashScopeApiKey;
        }
        if ("zhipu".equals(normalized)) {
            return zhipuApiKey;
        }
        if ("kimi".equals(normalized)) {
            return kimiApiKey;
        }
        return deepSeekApiKey;
    }

    String officialModel(String selectedProvider) {
        String normalized = normalizeOfficialProvider(selectedProvider);
        if ("dashscope".equals(normalized)) {
            return dashScopeModel;
        }
        if ("zhipu".equals(normalized)) {
            return zhipuModel;
        }
        if ("kimi".equals(normalized)) {
            return kimiModel;
        }
        return deepSeekModel;
    }

    String defaultOfficialModel(String selectedProvider) {
        return original.defaultOfficialModel(selectedProvider);
    }

    private static String normalizeOfficialProvider(String selectedProvider) {
        if (selectedProvider == null) {
            return "deepseek";
        }
        String normalized = selectedProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if ("aliyun-dashscope".equals(normalized)) {
            return "dashscope";
        }
        if ("zhipu-ai".equals(normalized)) {
            return "zhipu";
        }
        if ("moonshot".equals(normalized) || "moonshot-kimi".equals(normalized)) {
            return "kimi";
        }
        return normalized;
    }

    private FabricConfig buildConfig() {
        String selectedProvider = isLlm() ? "custom-api" : provider;
        return original.withSettings(
                enabled,
                translateChat,
                translateOther,
                translateOutgoing,
                targetLanguage.getText(),
                original.outgoingTargetLanguage,
                displayMode,
                translateEnglishOnly,
                translatedTextColor,
                selectedProvider,
                endpoint.getText(),
                llmEndpoint,
                llmApiKey,
                llmModel,
                offlineAutoDownload,
                offlineModel,
                original.apiFallback,
                diskCache)
                .withTencentSettings(tencentSecretId, tencentSecretKey, tencentModel)
                .withOfficialProviderSettings("deepseek", deepSeekApiKey, deepSeekModel)
                .withOfficialProviderSettings("dashscope", dashScopeApiKey, dashScopeModel)
                .withOfficialProviderSettings("zhipu", zhipuApiKey, zhipuModel)
                .withOfficialProviderSettings("kimi", kimiApiKey, kimiModel);
    }

    void clearTencentSettings() {
        this.tencentSecretId = "";
        this.tencentSecretKey = "";
        this.tencentModel = "";
        saveClearedConfig();
    }

    void clearLlmSettings() {
        this.llmEndpoint = "";
        this.llmApiKey = "";
        this.llmModel = "";
        saveClearedConfig();
    }

    private void saveClearedConfig() {
        FabricConfig cleared = buildConfig();
        try {
            cleared.save();
        } catch (Exception ignored) {
            // 清空后忽略保存错误
        }
        try {
            FabricTranslationRuntime.initialize(cleared);
        } catch (Exception ignored) {
            // 下次保存刷新
        }
    }

    private static String colorLabel(TranslationTextColor color) {
        switch (color) {
            case ORIGINAL: return tr("value.universal_translator.color.original");
            case GREEN: return tr("value.universal_translator.color.green");
            case GOLD: return tr("value.universal_translator.color.gold");
            case LIGHT_PURPLE: return tr("value.universal_translator.color.light_purple");
            case YELLOW: return tr("value.universal_translator.color.yellow");
            case WHITE: return tr("value.universal_translator.color.white");
            case AQUA:
            default: return tr("value.universal_translator.color.aqua");
        }
    }

    private static String tr(String key, Object... arguments) {
        return new TranslatableText(key, arguments).getString();
    }

    private static String defaultTargetLanguage(String value) {
        return value == null || value.trim().isEmpty() || "-".equals(value.trim()) ? "zh-CN" : value;
    }

    /** 1.17 无 setPlaceholder，用 suggestion 模拟空输入占位 */
    private static String endpointPlaceholder(String value) {
        return value == null || value.isEmpty() ? "http://127.0.0.1:5000/..." : "";
    }

    private Layout layout() {
        int totalWidth = Math.max(180, Math.min(420, this.width - 20));
        int gap = 8;
        int buttonWidth = (totalWidth - gap) / 2;
        int left = (this.width - totalWidth) / 2;
        int top = this.height >= 240
                ? Math.max(34, Math.min(44, 20 + Math.max(0, this.height - 220) / 4))
                : Math.max(14, this.height - 200);
        int titleY = this.height >= 240 ? 18 : Math.max(4, top - 14);
        int rowStep = this.height >= 300 ? 26
                : (this.height >= 240 ? 22 : (this.height >= 220 ? 20 : 19));
        int targetY = top + rowStep * 5 + 33;
        int endpointY = targetY + (this.height >= 300 ? 32 : 28);
        int saveY = this.height >= 330 ? 270
                : Math.max(targetY + 24, this.height - 24);
        return new Layout(left, left + buttonWidth + gap, totalWidth, buttonWidth,
                titleY, top, rowStep, targetY, endpointY, saveY);
    }

    private static final class Layout {
        private final int left, right, totalWidth, buttonWidth;
        private final int titleY, top, rowStep, targetY, endpointY, saveY;

        private Layout(int left, int right, int totalWidth, int buttonWidth,
                       int titleY, int top, int rowStep,
                       int targetY, int endpointY, int saveY) {
            this.left = left; this.right = right;
            this.totalWidth = totalWidth; this.buttonWidth = buttonWidth;
            this.titleY = titleY;
            this.top = top; this.rowStep = rowStep;
            this.targetY = targetY; this.endpointY = endpointY; this.saveY = saveY;
        }

        private int row(int index) {
            return top + rowStep * index;
        }
    }
}

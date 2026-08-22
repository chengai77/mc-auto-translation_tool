package org.universaltranslator.core.provider;

import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;

/** DashScope端点 */
public final class DashScopeOfficialProvider implements TranslationProvider {
    public static final String PROVIDER_ID = "dashscope";
    public static final String DEFAULT_MODEL = "qwen-plus";
    private static final String CHAT_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final OpenAiChatTranslationProvider delegate;

    public DashScopeOfficialProvider(String apiKey, String model) {
        delegate = new OpenAiChatTranslationProvider(
                CHAT_ENDPOINT,
                apiKey,
                normalizeModel(model),
                PROVIDER_ID);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String translate(TranslationRequest request) throws Exception {
        return delegate.translate(request);
    }

    private static String normalizeModel(String model) {
        return model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
    }
}

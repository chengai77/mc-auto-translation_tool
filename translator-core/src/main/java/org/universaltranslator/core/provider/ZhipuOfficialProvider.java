package org.universaltranslator.core.provider;

import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;

/** 智谱端点 */
public final class ZhipuOfficialProvider implements TranslationProvider {
    public static final String PROVIDER_ID = "zhipu";
    public static final String DEFAULT_MODEL = "glm-4-flash";
    private static final String CHAT_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private final OpenAiChatTranslationProvider delegate;

    public ZhipuOfficialProvider(String apiKey, String model) {
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

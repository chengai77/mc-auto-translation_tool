package org.universaltranslator.core.provider;

import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;

/** DeepSeek端点 */
public final class DeepSeekOfficialProvider implements TranslationProvider {
    public static final String PROVIDER_ID = "deepseek";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final String CHAT_ENDPOINT = "https://api.deepseek.com/chat/completions";

    private final OpenAiChatTranslationProvider delegate;

    public DeepSeekOfficialProvider(String apiKey, String model) {
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

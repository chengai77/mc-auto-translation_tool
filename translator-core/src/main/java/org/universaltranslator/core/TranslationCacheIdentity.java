package org.universaltranslator.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

/** 与翻译服务无关的缓存身份 */
final class TranslationCacheIdentity {
    private static final String PROVIDER_HISTORY_KEY =
            "universal-translator-provider-history-v1";
    private static final int MAX_PROVIDER_HISTORY = 64;
    private static final int MAX_PROVIDER_ID_LENGTH = 512;
    /**
     * 旧版本没有记录 provider 历史，只能用已发布的固定 provider id 补迁移。
     */
    private static final List<String> KNOWN_LEGACY_PROVIDER_IDS = Arrays.asList(
            "deepseek:deepseek-chat",
            "deepseek:deepseek-v4-flash",
            "dashscope:qwen-plus",
            "zhipu:glm-4-flash",
            "kimi:kimi-k2-0905-preview",
            "tencent-hunyuan:hunyuan-translation-lite",
            "offline-llama:lite",
            "offline-llama:quality"
    );

    private TranslationCacheIdentity() {
    }

    static void registerProvider(TranslationStore cache, String providerId) {
        if (cache == null || !isValidProviderId(providerId)) {
            return;
        }
        synchronized (cache) {
            LinkedHashSet<String> providers =
                    new LinkedHashSet<String>(readProviders(cache));
            providers.remove(providerId);
            providers.add(providerId);
            while (providers.size() > MAX_PROVIDER_HISTORY) {
                providers.remove(providers.iterator().next());
            }
            cache.put(PROVIDER_HISTORY_KEY, encodeProviders(providers));
        }
    }

    static String get(
            TranslationStore cache,
            String providerId,
            String formatVersion,
            String identity
    ) {
        String key = canonicalKey(formatVersion, identity);
        String value = cache.get(key);
        if (value != null) {
            return value;
        }
        for (String legacyProvider : providerCandidates(cache, providerId)) {
            value = cache.get(legacyKey(formatVersion, legacyProvider, identity));
            if (value != null) {
                cache.put(key, value);
                return value;
            }
        }
        return null;
    }

    static void put(
            TranslationStore cache,
            String formatVersion,
            String identity,
            String value
    ) {
        cache.put(canonicalKey(formatVersion, identity), value);
    }

    private static List<String> providerCandidates(
            TranslationStore cache, String currentProvider
    ) {
        LinkedHashSet<String> providers =
                new LinkedHashSet<String>(readProviders(cache));
        if (isValidProviderId(currentProvider)) {
            providers.add(currentProvider);
        }
        for (String provider : KNOWN_LEGACY_PROVIDER_IDS) {
            providers.add(provider);
        }
        return new ArrayList<String>(providers);
    }

    private static List<String> readProviders(TranslationStore cache) {
        List<String> providers = new ArrayList<String>();
        String encoded = cache.get(PROVIDER_HISTORY_KEY);
        if (encoded == null || encoded.isEmpty()) {
            return providers;
        }
        String[] values = encoded.split("\\n");
        for (String value : values) {
            try {
                String provider = new String(
                        Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                if (isValidProviderId(provider) && !providers.contains(provider)) {
                    providers.add(provider);
                }
            } catch (IllegalArgumentException ignored) {
                // 忽略损坏记录
            }
        }
        return providers;
    }

    private static String encodeProviders(Iterable<String> providers) {
        StringBuilder output = new StringBuilder();
        for (String provider : providers) {
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                    provider.getBytes(StandardCharsets.UTF_8)));
        }
        return output.toString();
    }

    private static boolean isValidProviderId(String providerId) {
        return providerId != null
                && !providerId.trim().isEmpty()
                && providerId.length() <= MAX_PROVIDER_ID_LENGTH
                && providerId.indexOf('\n') < 0
                && providerId.indexOf('\r') < 0;
    }

    private static String canonicalKey(String formatVersion, String identity) {
        return formatVersion + "\n" + identity;
    }

    private static String legacyKey(
            String formatVersion, String providerId, String identity
    ) {
        return formatVersion + "\n" + providerId + "\n" + identity;
    }
}

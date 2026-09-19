package org.universaltranslator.neoforge;

import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationDisplayMode;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.LocalConfigSecurity;
import org.universaltranslator.core.OfflineModel;
import org.universaltranslator.core.provider.LibreTranslateProvider;
import org.universaltranslator.core.provider.TencentHunyuanProvider;
import org.universaltranslator.core.provider.DashScopeOfficialProvider;
import org.universaltranslator.core.provider.DeepSeekOfficialProvider;
import org.universaltranslator.core.provider.FallbackTranslationProvider;
import org.universaltranslator.core.provider.KimiOfficialProvider;
import org.universaltranslator.core.provider.ZhipuOfficialProvider;
import org.universaltranslator.core.provider.LlamaCppOfflineProvider;
import org.universaltranslator.core.provider.OpenAiChatTranslationProvider;
import org.universaltranslator.core.offline.OfflineStoragePaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

final class NeoForgeConfig {
    private static final String FILE_NAME = "universal-translator.properties";

    final boolean enabled;
    final boolean translateChat;
    final boolean translateOther;
    final boolean translateOutgoing;
    final String targetLanguage;
    final String outgoingTargetLanguage;
    final TranslationDisplayMode displayMode;
    final boolean translateEnglishOnly;
    final TranslationTextColor translatedTextColor;
    final String provider;
    final String endpoint;
    final String apiKey;
    final String tencentSecretId;
    final String tencentSecretKey;
    final String tencentModel;
    final String llmEndpoint;
    final String llmApiKey;
    final String llmModel;
    final String deepSeekApiKey;
    final String deepSeekModel;
    final String dashScopeApiKey;
    final String dashScopeModel;
    final String zhipuApiKey;
    final String zhipuModel;
    final String kimiApiKey;
    final String kimiModel;
    final boolean offlineAutoDownload;
    final OfflineModel offlineModel;
    final boolean apiFallback;
    final String apiFallbackProvider;
    final Path offlineDirectory;
    final boolean diskCache;
    final boolean pinnedLogEnabled;
    final String pinnedLogPreset;
    final int pinnedLogX;
    final int pinnedLogY;
    final int pinnedLogWidth;
    final int pinnedLogScale;
    final Set<TextKind> logAllowedKinds;
    final Path cacheFile;
    private final Path configFile;

    private NeoForgeConfig(Properties properties, Path configFile, Path cacheFile) {
        this.enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
        this.translateChat = Boolean.parseBoolean(properties.getProperty("translate-chat", "true"));
        this.translateOther = Boolean.parseBoolean(properties.getProperty("translate-other", "true"));
        this.translateOutgoing = Boolean.parseBoolean(
                properties.getProperty("translate-outgoing", "false"));
        this.targetLanguage = properties.getProperty("target-language", "zh-CN").trim();
        this.outgoingTargetLanguage = properties.getProperty(
                "outgoing-target-language", "en").trim();
        this.displayMode = TranslationDisplayMode.fromConfig(
                properties.getProperty("display-mode", "translated-only"));
        this.translateEnglishOnly = Boolean.parseBoolean(
                properties.getProperty("translate-english-only", "true"));
        this.translatedTextColor = TranslationTextColor.fromConfig(
                properties.getProperty("translated-text-color", "original"));
        this.provider = properties.getProperty("provider", "offline").trim();
        this.endpoint = properties.getProperty(
                "libretranslate-endpoint", "http://127.0.0.1:5000/translate").trim();
        this.apiKey = properties.getProperty("api-key", "").trim();
        this.tencentSecretId = properties.getProperty("tencent-secret-id", "").trim();
        this.tencentSecretKey = properties.getProperty("tencent-secret-key", "").trim();
        this.tencentModel = properties.getProperty(
                "tencent-model", "hunyuan-translation-lite").trim();
        this.llmEndpoint = properties.getProperty("llm-api-endpoint", "").trim();
        this.llmApiKey = properties.getProperty("llm-api-key", "").trim();
        this.llmModel = properties.getProperty("llm-api-model", "").trim();
        this.deepSeekApiKey = properties.getProperty("deepseek-api-key", "").trim();
        this.deepSeekModel = properties.getProperty(
                "deepseek-model", DeepSeekOfficialProvider.DEFAULT_MODEL).trim();
        this.dashScopeApiKey = properties.getProperty("dashscope-api-key", "").trim();
        this.dashScopeModel = properties.getProperty(
                "dashscope-model", DashScopeOfficialProvider.DEFAULT_MODEL).trim();
        this.zhipuApiKey = properties.getProperty("zhipu-api-key", "").trim();
        this.zhipuModel = properties.getProperty(
                "zhipu-model", ZhipuOfficialProvider.DEFAULT_MODEL).trim();
        this.kimiApiKey = properties.getProperty("kimi-api-key", "").trim();
        this.kimiModel = properties.getProperty(
                "kimi-model", KimiOfficialProvider.DEFAULT_MODEL).trim();
        this.offlineAutoDownload = Boolean.parseBoolean(
                properties.getProperty("offline-auto-download", "true"));
        this.offlineModel = OfflineModel.fromConfig(properties.getProperty("offline-model", "lite"));
        this.apiFallback = Boolean.parseBoolean(properties.getProperty("api-fallback", "false"));
        this.apiFallbackProvider = properties.getProperty(
                "api-fallback-provider", "libretranslate").trim();
        this.diskCache = Boolean.parseBoolean(properties.getProperty("disk-cache", "true"));
        this.pinnedLogEnabled = Boolean.parseBoolean(
                properties.getProperty("pinned-log-enabled", "false"));
        this.pinnedLogPreset = normalizePinnedPreset(
                properties.getProperty("pinned-log-preset", "top-right"));
        this.pinnedLogX = clampInt(properties.getProperty("pinned-log-x", "12"), 0, 500);
        this.pinnedLogY = clampInt(properties.getProperty("pinned-log-y", "12"), 0, 500);
        this.pinnedLogWidth = clampInt(properties.getProperty("pinned-log-width", "38"), 20, 90);
        this.pinnedLogScale = clampInt(properties.getProperty("pinned-log-scale", "100"), 70, 160);
        this.logAllowedKinds = Collections.unmodifiableSet(parseLogAllowedKinds(
                properties.getProperty("log-allowed-kinds", defaultLogAllowedKinds())));
        this.configFile = configFile;
        this.cacheFile = cacheFile;
        this.offlineDirectory = OfflineStoragePaths.sharedDirectory();
    }

    static NeoForgeConfig load(Path configDirectory) throws IOException {
        Files.createDirectories(configDirectory);
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            Properties defaults = defaults();
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                defaults.store(writer,
                        "MC Auto Translation Tool - online translation may send selected server text to this endpoint");
            }
        }

        Properties stored = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            stored.load(reader);
        }
        Properties properties = defaults();
        properties.putAll(stored);
        boolean legacyMigration = !stored.containsKey("config-version");
        boolean migrated = configVersion(stored) < 6;
        if (legacyMigration) {
            properties.setProperty("display-mode", "translated-only");
            properties.setProperty("translate-english-only", "true");
            properties.setProperty("translated-text-color", "original");
        } else if (migrated
                && "aqua".equalsIgnoreCase(properties.getProperty("translated-text-color", ""))) {
            // 旧默认色迁移为保留原色
            properties.setProperty("translated-text-color", "original");
        }
        properties.setProperty("config-version", "6");
        LocalConfigSecurity.restrictToOwner(file);
        NeoForgeConfig loaded = new NeoForgeConfig(
                properties, file, configDirectory.resolve("universal-translator-cache.properties"));
        if (migrated) {
            loaded.save();
        }
        return loaded;
    }

    NeoForgeConfig withSettings(
            boolean enabled,
            boolean translateChat,
            boolean translateOther,
            boolean translateOutgoing,
            String targetLanguage,
            String outgoingTargetLanguage,
            TranslationDisplayMode displayMode,
            boolean translateEnglishOnly,
            TranslationTextColor translatedTextColor,
            String provider,
            String endpoint,
            String llmEndpoint,
            String llmApiKey,
            String llmModel,
            boolean offlineAutoDownload,
            OfflineModel offlineModel,
            boolean apiFallback,
            boolean diskCache
    ) {
        Properties properties = toProperties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("translate-chat", Boolean.toString(translateChat));
        properties.setProperty("translate-other", Boolean.toString(translateOther));
        properties.setProperty("translate-outgoing", Boolean.toString(translateOutgoing));
        properties.setProperty("target-language", targetLanguage.trim());
        properties.setProperty("outgoing-target-language", outgoingTargetLanguage.trim());
        properties.setProperty("display-mode", displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? "bilingual" : "translated-only");
        properties.setProperty("translate-english-only", Boolean.toString(translateEnglishOnly));
        properties.setProperty("translated-text-color", translatedTextColor.configName());
        properties.setProperty("provider", provider.trim());
        properties.setProperty("libretranslate-endpoint", endpoint.trim());
        properties.setProperty("llm-api-endpoint", llmEndpoint.trim());
        properties.setProperty("llm-api-key", llmApiKey.trim());
        properties.setProperty("llm-api-model", llmModel.trim());
        properties.setProperty("offline-auto-download", Boolean.toString(offlineAutoDownload));
        properties.setProperty("offline-model",
                (offlineModel == null ? OfflineModel.LITE : offlineModel).configName());
        properties.setProperty("api-fallback", Boolean.toString(apiFallback));
        properties.setProperty("disk-cache", Boolean.toString(diskCache));
        return new NeoForgeConfig(properties, configFile, cacheFile);
    }

    NeoForgeConfig withTencentSettings(String secretId, String secretKey, String model) {
        Properties properties = toProperties();
        properties.setProperty("tencent-secret-id", secretId == null ? "" : secretId);
        properties.setProperty("tencent-secret-key", secretKey == null ? "" : secretKey);
        properties.setProperty("tencent-model", model == null ? "hunyuan-translation-lite" : model);
        return new NeoForgeConfig(properties, configFile, cacheFile);
    }

    NeoForgeConfig withOfficialProviderSettings(String selectedProvider, String apiKey, String model) {
        Properties properties = toProperties();
        String normalized = normalizeOfficialProvider(selectedProvider);
        properties.setProperty(normalized + "-api-key", apiKey == null ? "" : apiKey.trim());
        properties.setProperty(normalized + "-model", normalizeOfficialModel(normalized, model));
        return new NeoForgeConfig(properties, configFile, cacheFile);
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
        return defaultOfficialModelFor(normalizeOfficialProvider(selectedProvider));
    }

    NeoForgeConfig withEnabled(boolean enabled) {
        Properties properties = toProperties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        return new NeoForgeConfig(properties, configFile, cacheFile);
    }

    NeoForgeConfig withPinnedLogSettings(
            boolean pinnedLogEnabled,
            String pinnedLogPreset,
            int pinnedLogX,
            int pinnedLogY,
            int pinnedLogWidth,
            int pinnedLogScale
    ) {
        Properties properties = toProperties();
        properties.setProperty("pinned-log-enabled", Boolean.toString(pinnedLogEnabled));
        properties.setProperty("pinned-log-preset", normalizePinnedPreset(pinnedLogPreset));
        properties.setProperty("pinned-log-x", Integer.toString(clamp(pinnedLogX, 0, 500)));
        properties.setProperty("pinned-log-y", Integer.toString(clamp(pinnedLogY, 0, 500)));
        properties.setProperty("pinned-log-width", Integer.toString(clamp(pinnedLogWidth, 20, 90)));
        properties.setProperty("pinned-log-scale", Integer.toString(clamp(pinnedLogScale, 70, 160)));
        return new NeoForgeConfig(properties, configFile, cacheFile);
    }

    NeoForgeConfig withLogAllowedKind(TextKind kind, boolean allowed) {
        Properties properties = toProperties();
        EnumSet<TextKind> kinds = EnumSet.noneOf(TextKind.class);
        kinds.addAll(logAllowedKinds);
        if (allowed) {
            kinds.add(kind);
        } else {
            kinds.remove(kind);
        }
        properties.setProperty("log-allowed-kinds", joinLogAllowedKinds(kinds));
        return new NeoForgeConfig(properties, configFile, cacheFile);
    }

    void save() throws IOException {
        Path temporary = configFile.resolveSibling(configFile.getFileName().toString() + ".tmp");
        try {
            Files.deleteIfExists(temporary);
            Files.createFile(temporary);
            LocalConfigSecurity.restrictToOwner(temporary);
            try (Writer writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                toProperties().store(writer,
                        "MC Auto Translation Tool - online translation may send selected server text to this endpoint");
            }
            try {
                Files.move(temporary, configFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 保留原错误
            }
        }
        LocalConfigSecurity.restrictToOwner(configFile);
    }

    void validateProviderConfiguration() throws Exception {
        TranslationProvider candidate = createProvider();
        if (candidate instanceof AutoCloseable) {
            ((AutoCloseable) candidate).close();
        }
    }

    boolean allows(TextKind kind) {
        if (!enabled) {
            return false;
        }
        return kind == TextKind.CHAT || kind == TextKind.SYSTEM_MESSAGE
                ? translateChat
                : translateOther;
    }

    TranslationProvider createProvider() {
        if ("offline".equalsIgnoreCase(provider)) {
            TranslationProvider local = LlamaCppOfflineProvider.forModel(
                    offlineDirectory, offlineAutoDownload, offlineModel,
                    OfflineStoragePaths.legacySearchRoots(configFile.getParent()));
            return apiFallback
                    ? new FallbackTranslationProvider(local, createApiProvider(apiFallbackProvider))
                    : local;
        }
        return createApiProvider(provider);
    }

    private TranslationProvider createApiProvider(String selectedProvider) {
        if ("libretranslate".equalsIgnoreCase(selectedProvider)) {
            return new LibreTranslateProvider(endpoint, apiKey);
        }
        if ("tencent-hunyuan".equalsIgnoreCase(selectedProvider)) {
            return new TencentHunyuanProvider(tencentSecretId, tencentSecretKey, tencentModel);
        }
        String officialProvider = normalizeOfficialProvider(selectedProvider);
        if ("deepseek".equals(officialProvider)) {
            return new DeepSeekOfficialProvider(deepSeekApiKey, deepSeekModel);
        }
        if ("dashscope".equals(officialProvider)) {
            return new DashScopeOfficialProvider(dashScopeApiKey, dashScopeModel);
        }
        if ("zhipu".equals(officialProvider)) {
            return new ZhipuOfficialProvider(zhipuApiKey, zhipuModel);
        }
        if ("kimi".equals(officialProvider)) {
            return new KimiOfficialProvider(kimiApiKey, kimiModel);
        }
        if (isCustomApiProvider(selectedProvider)) {
            return new OpenAiChatTranslationProvider(
                    llmEndpoint, llmApiKey, llmModel, "custom-api");
        }
        throw new IllegalArgumentException("Unsupported translation provider: " + selectedProvider);
    }

    static boolean isOfficialProvider(String selectedProvider) {
        String normalized = normalizeOfficialProvider(selectedProvider);
        return "deepseek".equals(normalized)
                || "dashscope".equals(normalized)
                || "zhipu".equals(normalized)
                || "kimi".equals(normalized);
    }

    private static String normalizeOfficialProvider(String selectedProvider) {
        if (selectedProvider == null) {
            return "deepseek";
        }
        String normalized = selectedProvider.trim().toLowerCase(Locale.ROOT);
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

    private static String normalizeOfficialModel(String selectedProvider, String model) {
        return model == null || model.trim().isEmpty()
                ? defaultOfficialModelFor(selectedProvider)
                : model.trim();
    }

    private static String defaultOfficialModelFor(String selectedProvider) {
        if ("dashscope".equals(selectedProvider)) {
            return DashScopeOfficialProvider.DEFAULT_MODEL;
        }
        if ("zhipu".equals(selectedProvider)) {
            return ZhipuOfficialProvider.DEFAULT_MODEL;
        }
        if ("kimi".equals(selectedProvider)) {
            return KimiOfficialProvider.DEFAULT_MODEL;
        }
        return DeepSeekOfficialProvider.DEFAULT_MODEL;
    }

    private static boolean isCustomApiProvider(String selectedProvider) {
        return "custom-api".equalsIgnoreCase(selectedProvider)
                || "openai-compatible".equalsIgnoreCase(selectedProvider);
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("config-version", "6");
        properties.setProperty("enabled", "false");
        properties.setProperty("translate-chat", "true");
        properties.setProperty("translate-other", "true");
        properties.setProperty("translate-outgoing", "false");
        properties.setProperty("target-language", "zh-CN");
        properties.setProperty("outgoing-target-language", "en");
        properties.setProperty("display-mode", "translated-only");
        properties.setProperty("translate-english-only", "true");
        properties.setProperty("translated-text-color", "original");
        properties.setProperty("provider", "offline");
        properties.setProperty("libretranslate-endpoint", "http://127.0.0.1:5000/translate");
        properties.setProperty("api-key", "");
        properties.setProperty("tencent-secret-id", "");
        properties.setProperty("tencent-secret-key", "");
        properties.setProperty("tencent-model", "hunyuan-translation-lite");
        properties.setProperty("llm-api-endpoint", "");
        properties.setProperty("llm-api-key", "");
        properties.setProperty("llm-api-model", "");
        properties.setProperty("deepseek-api-key", "");
        properties.setProperty("deepseek-model", DeepSeekOfficialProvider.DEFAULT_MODEL);
        properties.setProperty("dashscope-api-key", "");
        properties.setProperty("dashscope-model", DashScopeOfficialProvider.DEFAULT_MODEL);
        properties.setProperty("zhipu-api-key", "");
        properties.setProperty("zhipu-model", ZhipuOfficialProvider.DEFAULT_MODEL);
        properties.setProperty("kimi-api-key", "");
        properties.setProperty("kimi-model", KimiOfficialProvider.DEFAULT_MODEL);
        properties.setProperty("offline-auto-download", "true");
        properties.setProperty("offline-model", "lite");
        properties.setProperty("api-fallback", "false");
        properties.setProperty("api-fallback-provider", "libretranslate");
        properties.setProperty("disk-cache", "true");
        properties.setProperty("pinned-log-enabled", "false");
        properties.setProperty("pinned-log-preset", "top-right");
        properties.setProperty("pinned-log-x", "12");
        properties.setProperty("pinned-log-y", "12");
        properties.setProperty("pinned-log-width", "38");
        properties.setProperty("pinned-log-scale", "100");
        return properties;
    }

    private Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("config-version", "6");
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("translate-chat", Boolean.toString(translateChat));
        properties.setProperty("translate-other", Boolean.toString(translateOther));
        properties.setProperty("translate-outgoing", Boolean.toString(translateOutgoing));
        properties.setProperty("target-language", targetLanguage);
        properties.setProperty("outgoing-target-language", outgoingTargetLanguage);
        properties.setProperty("display-mode", displayMode == TranslationDisplayMode.ORIGINAL_AND_TRANSLATED
                ? "bilingual" : "translated-only");
        properties.setProperty("translate-english-only", Boolean.toString(translateEnglishOnly));
        properties.setProperty("translated-text-color", translatedTextColor.configName());
        properties.setProperty("provider", provider);
        properties.setProperty("libretranslate-endpoint", endpoint);
        properties.setProperty("api-key", apiKey);
        properties.setProperty("tencent-secret-id", tencentSecretId);
        properties.setProperty("tencent-secret-key", tencentSecretKey);
        properties.setProperty("tencent-model", tencentModel);
        properties.setProperty("llm-api-endpoint", llmEndpoint);
        properties.setProperty("llm-api-key", llmApiKey);
        properties.setProperty("llm-api-model", llmModel);
        properties.setProperty("deepseek-api-key", deepSeekApiKey);
        properties.setProperty("deepseek-model", deepSeekModel);
        properties.setProperty("dashscope-api-key", dashScopeApiKey);
        properties.setProperty("dashscope-model", dashScopeModel);
        properties.setProperty("zhipu-api-key", zhipuApiKey);
        properties.setProperty("zhipu-model", zhipuModel);
        properties.setProperty("kimi-api-key", kimiApiKey);
        properties.setProperty("kimi-model", kimiModel);
        properties.setProperty("offline-auto-download", Boolean.toString(offlineAutoDownload));
        properties.setProperty("offline-model", offlineModel.configName());
        properties.setProperty("api-fallback", Boolean.toString(apiFallback));
        properties.setProperty("api-fallback-provider", apiFallbackProvider);
        properties.setProperty("disk-cache", Boolean.toString(diskCache));
        properties.setProperty("pinned-log-enabled", Boolean.toString(pinnedLogEnabled));
        properties.setProperty("pinned-log-preset", pinnedLogPreset);
        properties.setProperty("pinned-log-x", Integer.toString(pinnedLogX));
        properties.setProperty("pinned-log-y", Integer.toString(pinnedLogY));
        properties.setProperty("pinned-log-width", Integer.toString(pinnedLogWidth));
        properties.setProperty("pinned-log-scale", Integer.toString(pinnedLogScale));
        properties.setProperty("log-allowed-kinds", joinLogAllowedKinds(logAllowedKinds));
        return properties;
    }

    private static EnumSet<TextKind> parseLogAllowedKinds(String value) {
        EnumSet<TextKind> kinds = EnumSet.noneOf(TextKind.class);
        if (value != null) {
            for (String token : value.split(",")) {
                String normalized = token.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                try {
                    kinds.add(TextKind.valueOf(normalized));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return kinds;
    }

    private static String defaultLogAllowedKinds() {
        return "TITLE,SUBTITLE,ACTION_BAR";
    }

    private static String joinLogAllowedKinds(Set<TextKind> kinds) {
        StringBuilder builder = new StringBuilder();
        for (TextKind kind : TextKind.values()) {
            if (!kinds.contains(kind)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(kind.name());
        }
        return builder.toString();
    }

    private static int clampInt(String value, int minimum, int maximum) {
        try {
            return clamp(Integer.parseInt(value.trim()), minimum, maximum);
        } catch (RuntimeException ignored) {
            return minimum;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String normalizePinnedPreset(String value) {
        return "top-left".equalsIgnoreCase(value) ? "top-left" : "top-right";
    }

    private static int configVersion(Properties properties) {
        try {
            return Integer.parseInt(properties.getProperty("config-version", "1").trim());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}

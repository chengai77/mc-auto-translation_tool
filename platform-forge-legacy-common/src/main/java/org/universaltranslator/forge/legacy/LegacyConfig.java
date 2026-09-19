package org.universaltranslator.forge.legacy;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import java.util.Properties;

final class LegacyConfig {
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
    final File offlineDirectory;
    final boolean diskCache;
    final boolean pinnedLogEnabled;
    final String pinnedLogPreset;
    final int pinnedLogX;
    final int pinnedLogY;
    final int pinnedLogWidth;
    final int pinnedLogScale;
    final Set<TextKind> logAllowedKinds;
    final File cacheFile;
    private final File configFile;

    private LegacyConfig(Properties properties, File configFile, File cacheFile) {
        enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
        translateChat = Boolean.parseBoolean(properties.getProperty("translate-chat", "true"));
        translateOther = Boolean.parseBoolean(properties.getProperty("translate-other", "true"));
        translateOutgoing = Boolean.parseBoolean(
                properties.getProperty("translate-outgoing", "false"));
        targetLanguage = properties.getProperty("target-language", "zh-CN").trim();
        outgoingTargetLanguage = properties.getProperty(
                "outgoing-target-language", "en").trim();
        displayMode = TranslationDisplayMode.fromConfig(
                properties.getProperty("display-mode", "translated-only"));
        translateEnglishOnly = Boolean.parseBoolean(
                properties.getProperty("translate-english-only", "true"));
        translatedTextColor = TranslationTextColor.fromConfig(
                properties.getProperty("translated-text-color", "original"));
        provider = properties.getProperty("provider", "offline").trim();
        endpoint = properties.getProperty(
                "libretranslate-endpoint", "http://127.0.0.1:5000/translate").trim();
        apiKey = properties.getProperty("api-key", "").trim();
        tencentSecretId = properties.getProperty("tencent-secret-id", "").trim();
        tencentSecretKey = properties.getProperty("tencent-secret-key", "").trim();
        tencentModel = properties.getProperty(
                "tencent-model", "hunyuan-translation-lite").trim();
        llmEndpoint = properties.getProperty("llm-api-endpoint", "").trim();
        llmApiKey = properties.getProperty("llm-api-key", "").trim();
        llmModel = properties.getProperty("llm-api-model", "").trim();
        deepSeekApiKey = properties.getProperty("deepseek-api-key", "").trim();
        deepSeekModel = properties.getProperty(
                "deepseek-model", DeepSeekOfficialProvider.DEFAULT_MODEL).trim();
        dashScopeApiKey = properties.getProperty("dashscope-api-key", "").trim();
        dashScopeModel = properties.getProperty(
                "dashscope-model", DashScopeOfficialProvider.DEFAULT_MODEL).trim();
        zhipuApiKey = properties.getProperty("zhipu-api-key", "").trim();
        zhipuModel = properties.getProperty(
                "zhipu-model", ZhipuOfficialProvider.DEFAULT_MODEL).trim();
        kimiApiKey = properties.getProperty("kimi-api-key", "").trim();
        kimiModel = properties.getProperty(
                "kimi-model", KimiOfficialProvider.DEFAULT_MODEL).trim();
        offlineAutoDownload = Boolean.parseBoolean(
                properties.getProperty("offline-auto-download", "true"));
        offlineModel = OfflineModel.fromConfig(properties.getProperty("offline-model", "lite"));
        apiFallback = Boolean.parseBoolean(properties.getProperty("api-fallback", "false"));
        apiFallbackProvider = properties.getProperty(
                "api-fallback-provider", "libretranslate").trim();
        diskCache = Boolean.parseBoolean(properties.getProperty("disk-cache", "true"));
        pinnedLogEnabled = Boolean.parseBoolean(
                properties.getProperty("pinned-log-enabled", "false"));
        pinnedLogPreset = normalizePinnedPreset(properties.getProperty("pinned-log-preset", "top-right"));
        pinnedLogX = clampInt(properties.getProperty("pinned-log-x", "12"), 0, 500);
        pinnedLogY = clampInt(properties.getProperty("pinned-log-y", "12"), 0, 500);
        pinnedLogWidth = clampInt(properties.getProperty("pinned-log-width", "38"), 20, 90);
        pinnedLogScale = clampInt(properties.getProperty("pinned-log-scale", "100"), 70, 160);
        logAllowedKinds = parseLogAllowedKinds(
                properties.getProperty("log-allowed-kinds", defaultLogAllowedKinds()));
        this.configFile = configFile;
        this.cacheFile = cacheFile;
        this.offlineDirectory = OfflineStoragePaths.sharedDirectory().toFile();
    }

    static LegacyConfig load(File configDirectory) throws IOException {
        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            throw new IOException("Could not create config directory: " + configDirectory);
        }
        File file = new File(configDirectory, "universal-translator.properties");
        if (!file.exists()) {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                defaults().store(writer,
                        "MC Auto Translation Tool - online translation may send selected server text to this endpoint");
            }
        }
        Properties stored = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
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
        LocalConfigSecurity.restrictToOwner(file.toPath());
        LegacyConfig loaded = new LegacyConfig(
                properties, file, new File(configDirectory, "universal-translator-cache.properties"));
        if (migrated) {
            loaded.save();
        }
        return loaded;
    }

    LegacyConfig withSettings(
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
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    LegacyConfig withTencentSettings(String secretId, String secretKey, String model) {
        Properties properties = toProperties();
        properties.setProperty("tencent-secret-id", secretId == null ? "" : secretId);
        properties.setProperty("tencent-secret-key", secretKey == null ? "" : secretKey);
        properties.setProperty("tencent-model", model == null ? "hunyuan-translation-lite" : model);
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    LegacyConfig withOfficialProviderSettings(String selectedProvider, String apiKey, String model) {
        Properties properties = toProperties();
        String normalized = normalizeOfficialProvider(selectedProvider);
        properties.setProperty(normalized + "-api-key", apiKey == null ? "" : apiKey.trim());
        properties.setProperty(normalized + "-model", normalizeOfficialModel(normalized, model));
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    LegacyConfig withEnabled(boolean enabled) {
        Properties properties = toProperties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    LegacyConfig withPinnedLogSettings(
            boolean enabled, String preset, int x, int y, int width, int scale) {
        Properties properties = toProperties();
        properties.setProperty("pinned-log-enabled", Boolean.toString(enabled));
        properties.setProperty("pinned-log-preset", normalizePinnedPreset(preset));
        properties.setProperty("pinned-log-x", Integer.toString(clamp(x, 0, 500)));
        properties.setProperty("pinned-log-y", Integer.toString(clamp(y, 0, 500)));
        properties.setProperty("pinned-log-width", Integer.toString(clamp(width, 20, 90)));
        properties.setProperty("pinned-log-scale", Integer.toString(clamp(scale, 70, 160)));
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    LegacyConfig withLogAllowedKind(TextKind kind, boolean allowed) {
        Properties properties = toProperties();
        EnumSet<TextKind> kinds = EnumSet.noneOf(TextKind.class);
        kinds.addAll(logAllowedKinds);
        if (allowed) {
            kinds.add(kind);
        } else {
            kinds.remove(kind);
        }
        properties.setProperty("log-allowed-kinds", joinLogAllowedKinds(kinds));
        return new LegacyConfig(properties, configFile, cacheFile);
    }

    void save() throws IOException {
        Path file = configFile.toPath();
        Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Files.deleteIfExists(temporary);
            Files.createFile(temporary);
            LocalConfigSecurity.restrictToOwner(temporary);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    Files.newOutputStream(temporary,
                            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                    StandardCharsets.UTF_8)) {
                toProperties().store(writer,
                        "MC Auto Translation Tool - online translation may send selected server text to this endpoint");
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 保留原错误
            }
        }
        LocalConfigSecurity.restrictToOwner(file);
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
                    offlineDirectory.toPath(), offlineAutoDownload, offlineModel,
                    OfflineStoragePaths.legacySearchRoots(configFile.getParentFile().toPath()));
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
        properties.setProperty("log-allowed-kinds", defaultLogAllowedKinds());
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

    private static String defaultLogAllowedKinds() {
        return "TITLE,SUBTITLE,ACTION_BAR";
    }

    private static Set<TextKind> parseLogAllowedKinds(String value) {
        EnumSet<TextKind> kinds = EnumSet.noneOf(TextKind.class);
        if (value != null) {
            for (String token : value.split(",")) {
                try {
                    kinds.add(TextKind.valueOf(token.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return java.util.Collections.unmodifiableSet(kinds);
    }

    private static String joinLogAllowedKinds(Set<TextKind> kinds) {
        StringBuilder value = new StringBuilder();
        for (TextKind kind : TextKind.values()) {
            if (kinds.contains(kind)) {
                if (value.length() > 0) {
                    value.append(',');
                }
                value.append(kind.name());
            }
        }
        return value.toString();
    }

    private static String normalizePinnedPreset(String value) {
        return "top-left".equalsIgnoreCase(value) ? "top-left" : "top-right";
    }

    private static int clampInt(String value, int minimum, int maximum) {
        try {
            return clamp(Integer.parseInt(value.trim()), minimum, maximum);
        } catch (Exception ignored) {
            return minimum;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int configVersion(Properties properties) {
        try {
            return Integer.parseInt(properties.getProperty("config-version", "1").trim());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}

package org.universaltranslator.core;

/** 状态本地化 */
public final class TranslationStatusLocalizer {
    private TranslationStatusLocalizer() {
    }

    public static String localize(String status, UiTranslator translator) {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty()) {
            return value;
        }
        if ("等待首次离线翻译".equals(value)) {
            return translator.translate("status.universal_translator.waiting_first_offline");
        }
        if ("离线模型运行中".equals(value)) {
            return translator.translate("status.universal_translator.offline_running");
        }
        if ("正在启动离线模型".equals(value)) {
            return translator.translate("status.universal_translator.offline_starting");
        }
        if ("离线模型已就绪".equals(value)) {
            return translator.translate("status.universal_translator.offline_ready");
        }
        if ("离线引擎下载并校验完成".equals(value)) {
            return translator.translate("status.universal_translator.engine_verified");
        }
        if ("正在安装离线引擎".equals(value)) {
            return translator.translate("status.universal_translator.engine_installing");
        }
        if ("离线模型下载并校验完成".equals(value)) {
            return translator.translate("status.universal_translator.model_verified");
        }
        if ("离线模型已停止".equals(value)) {
            return translator.translate("status.universal_translator.offline_stopped");
        }
        if ("主翻译服务运行中".equals(value)) {
            return translator.translate("status.universal_translator.primary_running");
        }
        if ("主翻译服务失败，已使用 API 回退".equals(value)) {
            return translator.translate("status.universal_translator.fallback_used");
        }
        if ("主翻译服务和 API 回退均失败".equals(value)) {
            return translator.translate("status.universal_translator.fallback_failed");
        }
        if (value.startsWith("翻译失败：")) {
            return translator.translate("status.universal_translator.translation_failed",
                    value.substring("翻译失败：".length()));
        }
        if (value.startsWith("离线翻译失败：")) {
            return translator.translate("status.universal_translator.offline_failed",
                    value.substring("离线翻译失败：".length()));
        }
        if (value.startsWith("正在下载离线引擎（约 ") && value.endsWith(" MB）")) {
            return translator.translate("status.universal_translator.engine_downloading_size",
                    value.substring("正在下载离线引擎（约 ".length(), value.length() - " MB）".length()));
        }
        if (value.startsWith("正在下载离线模型（国内源优先，约 ") && value.endsWith(" MB）")) {
            return translator.translate("status.universal_translator.model_downloading_size",
                    value.substring("正在下载离线模型（国内源优先，约 ".length(),
                            value.length() - " MB）".length()));
        }
        if (value.startsWith("正在下载离线引擎：")) {
            return translator.translate("status.universal_translator.engine_downloading_progress",
                    normalizeMetrics(value.substring("正在下载离线引擎：".length())));
        }
        if (value.startsWith("正在下载离线模型：")) {
            return translator.translate("status.universal_translator.model_downloading_progress",
                    normalizeMetrics(value.substring("正在下载离线模型：".length())));
        }
        return value;
    }

    public static boolean isFailure(String status) {
        String value = status == null ? "" : status;
        return value.startsWith("翻译失败：")
                || value.startsWith("离线翻译失败：")
                || value.contains("均失败");
    }

    public static boolean isDownloadProgress(String status) {
        String value = status == null ? "" : status.trim();
        return value.startsWith("正在下载离线引擎：")
                || value.startsWith("正在下载离线模型：");
    }

    public static DownloadProgressDisplay downloadProgressDisplay(
            String status, UiTranslator translator) {
        String value = status == null ? "" : status.trim();
        if (!isDownloadProgress(value)) {
            return null;
        }
        int metricStart = value.lastIndexOf('（');
        int metricEnd = value.endsWith("）") ? value.length() - 1 : -1;
        String progressStatus = metricStart >= 0 ? value.substring(0, metricStart) : value;
        String size = metricStart >= 0 && metricEnd > metricStart
                ? value.substring(metricStart + 1, metricEnd).trim() : "";
        return new DownloadProgressDisplay(localize(progressStatus, translator), size);
    }

    private static String normalizeMetrics(String value) {
        return value.replace('（', '(').replace('）', ')').trim();
    }

    public static final class DownloadProgressDisplay {
        private final String progress;
        private final String size;

        private DownloadProgressDisplay(String progress, String size) {
            this.progress = progress;
            this.size = size;
        }

        public String progress() {
            return progress;
        }

        public String size() {
            return size;
        }
    }
}

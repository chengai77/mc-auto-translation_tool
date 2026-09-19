package org.universaltranslator.core;

import java.util.Locale;
import java.util.regex.Pattern;

/** 拦截模型元话语 */
final class TranslationOutputGuard {
    private static final Pattern QUOTED_DEFINITION = Pattern.compile(
            "(?:是指|指的是|意思是|意为|意為|也就是)\\s*[\\\"'“‘]");
    private static final Pattern THOUGHT_BLOCK = Pattern.compile(
            "<\\s*(?:think|analysis|reasoning)\\b[^>]*>.*?<\\s*/\\s*(?:think|analysis|reasoning)\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DEFINITION_SENTENCE = Pattern.compile(
            "(?:应|應|可|可以|應該|应该)?\\s*翻译\\s*(?:为|為|成)|"
                    + "(?:should|can|could)\\s+be\\s+translated\\s+as",
            Pattern.CASE_INSENSITIVE);
    private static final String[] INSTRUCTION_FRAGMENTS = {
            "translate minecraft server interface text",
            "minecraft server interface text from",
            "translate the minecraft ui text",
            "translate the user text to",
            "minecraft ui text in zh-cn",
            "return only the translation",
            "reply with only the translation",
            "output translated text only",
            "you are a translation engine",
            "preserve tokens like",
            "keep every __ut_",
            "copy every __ut_",
            "player names, numbers, urls",
            "minecraft formatting markers",
            "urls, punctuation and minecraft",
            "minecraft/game glossary",
            "preset glossary",
            "recent context from cached translations",
            "resolve ambiguous words",
            "do not explain",
            "never repeat text",
            "游戏服务器界面文本从自动翻译",
            "服务器界面文本从自动翻译",
            "返回的是原文本翻译",
            "只返回译文",
            "仅返回翻译",
            "玩家名、数字、url",
            "minecraft格式标记",
            "不要解释",
            "翻译引擎",
            "思考过程", "思考過程", "分析过程", "分析過程",
            "以下是翻译", "以下為翻譯"
    };
    private static final String[] COMMENTARY_FRAGMENTS = {
            "应翻译为", "应该翻译为", "应译为", "可翻译为", "可以翻译为", "可译为",
            "應翻譯為", "應該翻譯為", "應譯為", "可翻譯為", "可以翻譯為", "可譯為",
            "翻译结果", "翻譯結果", "译文如下", "譯文如下", "译文：", "譯文：",
            "翻译：", "翻譯：", "直译为", "直譯為", "意译为", "意譯為",
            "更自然的译法", "更自然的譯法", "更合适的译法", "更合適的譯法",
            "should be translated as", "can be translated as", "could be translated as",
            "translation is", "translation:", "translated text:"
    };

    private TranslationOutputGuard() {
    }

    static void requireClean(String source, String output) {
        if (containsInstructionArtifact(output)) {
            throw TranslationOutputValidator.invalidOutput(
                    "Translation output echoed its instructions");
        }
        if (containsUnexpectedLiteExample(source, output)) {
            throw TranslationOutputValidator.invalidOutput(
                    "Translation output echoed an offline prompt example");
        }
        if (containsUnexpectedCommentary(source, output)) {
            throw TranslationOutputValidator.invalidOutput(
                    "Translation output added translation commentary");
        }
    }

    static boolean containsInstructionArtifact(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("<|system|>")
                || normalized.contains("<|assistant|>")
                || normalized.contains("[quote]")
                || normalized.contains("<think>")
                || normalized.contains("<analysis>")
                || normalized.contains("<reasoning>")
                || THOUGHT_BLOCK.matcher(text).find()) {
            return true;
        }
        for (String fragment : INSTRUCTION_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnexpectedLiteExample(String source, String output) {
        String normalizedOutput = output.toLowerCase(Locale.ROOT);
        if (!normalizedOutput.contains("你是什么模型")
                && !normalizedOutput.contains("你是什麼模型")) {
            return false;
        }
        String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (normalizedSource.contains("你是什么模型")
                || normalizedSource.contains("你是什麼模型")) {
            return false;
        }
        return !(normalizedSource.contains("model")
                && normalizedSource.contains("you")
                && (normalizedSource.contains("what")
                        || normalizedSource.contains("which")));
    }

    private static boolean containsUnexpectedCommentary(String source, String output) {
        if (hasMetaLanguageIntent(source)) {
            return false;
        }
        String normalizedOutput = output.toLowerCase(Locale.ROOT);
        if (DEFINITION_SENTENCE.matcher(output).find()) {
            return true;
        }
        if (QUOTED_DEFINITION.matcher(output).find()) {
            return true;
        }
        for (String fragment : COMMENTARY_FRAGMENTS) {
            if (normalizedOutput.contains(fragment)) {
                return true;
            }
        }
        String normalizedSource = source == null ? "" : source.trim();
        if (normalizedSource.isEmpty() || normalizedSource.length() > 32
                || output.length() <= normalizedSource.length()
                || !output.regionMatches(true, 0, normalizedSource, 0,
                        normalizedSource.length())) {
            return false;
        }
        String tail = output.substring(normalizedSource.length()).trim();
        while (!tail.isEmpty() && ":：,，-—".indexOf(tail.charAt(0)) >= 0) {
            tail = tail.substring(1).trim();
        }
        String lowerTail = tail.toLowerCase(Locale.ROOT);
        return lowerTail.startsWith("是指") || lowerTail.startsWith("指的是")
                || lowerTail.startsWith("意思是") || lowerTail.startsWith("意为")
                || lowerTail.startsWith("意為") || lowerTail.startsWith("也就是")
                || lowerTail.startsWith("means ") || lowerTail.startsWith("refers to ");
    }

    private static boolean hasMetaLanguageIntent(String source) {
        String normalized = source == null ? "" : source.toLowerCase(Locale.ROOT);
        return normalized.contains("translate") || normalized.contains("translation")
                || normalized.contains("localize") || normalized.contains("localization")
                || normalized.contains("mean") || normalized.contains("refer")
                || normalized.contains("indicat") || normalized.contains("represent")
                || normalized.contains("define") || normalized.contains("denote")
                || normalized.contains("stand for") || normalized.contains("explain")
                || normalized.contains("翻译") || normalized.contains("翻譯")
                || normalized.contains("译文") || normalized.contains("譯文")
                || normalized.contains("是指") || normalized.contains("指的是")
                || normalized.contains("意思") || normalized.contains("意为")
                || normalized.contains("意為") || normalized.contains("表示")
                || normalized.contains("定义") || normalized.contains("定義");
    }
}

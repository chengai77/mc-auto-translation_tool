package org.universaltranslator.core;

import java.util.Locale;

/** 离线预检避免联网 */
public final class LanguageHeuristics {
    private LanguageHeuristics() {
    }

    public static boolean shouldTranslate(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        // 自身文本已本地
        if (text.contains("[MC Auto Translation Tool]")
                || text.contains("[MC 自动翻译工具]")
                || text.contains("[Universal Translator]")) {
            return false;
        }
        if (TranslationOutputValidator.containsInternalArtifact(text)) {
            return false;
        }

        int letters = 0;
        int han = 0;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isLetter(value)) {
                letters++;
                Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
                if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                        || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                    han++;
                }
            }
        }

        if (letters == 0) {
            return false;
        }
        // 保留汉字部分
        // 全汉字则跳过
        if (isChineseTarget(targetLanguage) && han == letters) {
            return false;
        }
        return true;
    }

    /** 全大写文本转为句首大写，无需处理时返回 null */
    public static String normalizeAllCaps(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        boolean hasLetter = false;
        boolean hasLowerCase = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isLetter(value)) {
                hasLetter = true;
                if (Character.isLowerCase(value)) {
                    hasLowerCase = true;
                    break;
                }
            }
        }
        if (!hasLetter || hasLowerCase) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean isChineseTarget(String language) {
        if (language == null) {
            return false;
        }
        String normalized = language.toLowerCase().replace('_', '-');
        return normalized.equals("zh") || normalized.startsWith("zh-");
    }
}

package org.universaltranslator.core;

/** 译文格式封装 */
public final class TranslationDisplayText {
    private static final String BILINGUAL_SEPARATOR = " \u00a78| \u00a7f";

    private TranslationDisplayText() {
    }

    public static String bilingual(String original, String translated) {
        return original + BILINGUAL_SEPARATOR + translated;
    }

    public static String translatedOnly(String value, TranslationDisplayMode mode) {
        if (value == null) {
            return "";
        }
        if (mode != TranslationDisplayMode.ORIGINAL_AND_TRANSLATED) {
            return value;
        }
        int separator = value.lastIndexOf(BILINGUAL_SEPARATOR);
        return separator >= 0
                ? value.substring(separator + BILINGUAL_SEPARATOR.length()) : value;
    }
}

package org.universaltranslator.core;

/** 全息短语对齐 */
public final class HologramTextAlignment {
    private static final int MAX_CODE_POINTS = 28;
    private static final int MAX_WORDS = 6;

    private HologramTextAlignment() {
    }

    public static boolean shouldCenter(String original, String translated) {
        if (original == null || translated == null || original.equals(translated)
                || translated.indexOf('\n') >= 0 || translated.indexOf('\r') >= 0) {
            return false;
        }
        String visible = TranslationTextStyling.stripLegacyFormatting(
                StyledTranslationTemplate.strip(translated)).trim();
        if (visible.isEmpty()
                || visible.codePointCount(0, visible.length()) > MAX_CODE_POINTS
                || containsSentencePunctuation(visible)) {
            return false;
        }
        int words = 0;
        boolean inWord = false;
        for (int index = 0; index < visible.length(); index++) {
            boolean word = !Character.isWhitespace(visible.charAt(index));
            if (word && !inWord) {
                words++;
                if (words > MAX_WORDS) {
                    return false;
                }
            }
            inWord = word;
        }
        return true;
    }

    private static boolean containsSentencePunctuation(String text) {
        for (int index = 0; index < text.length(); index++) {
            switch (text.charAt(index)) {
                case '.':
                case '!':
                case '?':
                case ';':
                case '\u3002':
                case '\uff01':
                case '\uff1f':
                case '\uff1b':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}

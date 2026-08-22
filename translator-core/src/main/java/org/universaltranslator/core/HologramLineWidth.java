package org.universaltranslator.core;

/** 全息译文行宽 */
public final class HologramLineWidth {
    private static final int MAX_EXPANSION_FACTOR = 2;
    private static final int MAX_EXPANDED_WIDTH = 512;

    private HologramLineWidth() {
    }

    public static int layoutLimit(
            int originalWidth, String originalText, String translatedText) {
        if (originalWidth <= 0 || !mergesVisualLines(originalText, translatedText)) {
            return originalWidth;
        }
        long expanded = (long) originalWidth * MAX_EXPANSION_FACTOR;
        int capped = (int) Math.min((long) MAX_EXPANDED_WIDTH, expanded);
        return Math.max(originalWidth, capped);
    }

    public static int resolve(
            int originalWidth,
            int measuredWidth,
            String originalText,
            String translatedText
    ) {
        int limit = layoutLimit(originalWidth, originalText, translatedText);
        if (limit <= originalWidth || measuredWidth <= originalWidth) {
            return originalWidth;
        }
        return Math.min(limit, measuredWidth);
    }

    private static boolean mergesVisualLines(String originalText, String translatedText) {
        return visibleLineCount(originalText) > visibleLineCount(translatedText);
    }

    private static int visibleLineCount(String text) {
        int count = 0;
        for (String line : VisualTextBoundaries.splitLines(text)) {
            String visible = TranslationTextStyling.stripLegacyFormatting(
                    StyledTranslationTemplate.strip(line)).trim();
            if (!visible.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}

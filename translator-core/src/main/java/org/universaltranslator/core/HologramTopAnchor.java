package org.universaltranslator.core;

/** 全息顶部偏移修正 */
public final class HologramTopAnchor {
    private static final float LINE_HEIGHT = 0.25F;

    private HologramTopAnchor() {
    }

    public static float offset(int originalLines, int translatedLines) {
        if (originalLines < 1 || translatedLines < 1 || originalLines == translatedLines) {
            return 0.0F;
        }
        return (originalLines - translatedLines) * LINE_HEIGHT;
    }
}

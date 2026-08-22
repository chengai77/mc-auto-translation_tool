package org.universaltranslator.core;

import java.util.Objects;

/** 翻译结果对象 */
public final class TranslationResult {
    private final String originalText;
    private final String translatedText;
    private final boolean translated;
    private final String errorMessage;
    private final boolean recoverableFailure;

    private TranslationResult(
            String originalText,
            String translatedText,
            boolean translated,
            String errorMessage,
            boolean recoverableFailure
    ) {
        this.originalText = Objects.requireNonNull(originalText, "originalText");
        this.translatedText = Objects.requireNonNull(translatedText, "translatedText");
        this.translated = translated;
        this.errorMessage = errorMessage;
        this.recoverableFailure = recoverableFailure;
    }

    public static TranslationResult success(String originalText, String translatedText) {
        return new TranslationResult(originalText, translatedText, true, null, false);
    }

    public static TranslationResult unchanged(String text) {
        return new TranslationResult(text, text, false, null, false);
    }

    public static TranslationResult failure(String text, String errorMessage) {
        return failure(text, errorMessage, false);
    }

    public static TranslationResult recoverableFailure(String text, String errorMessage) {
        return failure(text, errorMessage, true);
    }

    private static TranslationResult failure(
            String text, String errorMessage, boolean recoverableFailure) {
        return new TranslationResult(
                text, text, false,
                errorMessage == null ? "Translation failed" : errorMessage,
                recoverableFailure);
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public boolean isTranslated() {
        return translated;
    }

    public boolean isFailure() {
        return errorMessage != null;
    }

    public boolean isRecoverableFailure() {
        return isFailure() && recoverableFailure;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

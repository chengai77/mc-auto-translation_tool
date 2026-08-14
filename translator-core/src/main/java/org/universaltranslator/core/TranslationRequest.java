package org.universaltranslator.core;

import java.util.Objects;

/** Immutable provider request. The text has already had protected tokens replaced. */
public final class TranslationRequest {
    private final String text;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final TextKind kind;
    private final String contextHint;
    private final String glossaryHint;

    public TranslationRequest(String text, String sourceLanguage, String targetLanguage, TextKind kind) {
        this(text, sourceLanguage, targetLanguage, kind, "");
    }

    public TranslationRequest(
            String text,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            String contextHint
    ) {
        this.text = Objects.requireNonNull(text, "text");
        this.sourceLanguage = sourceLanguage == null ? "auto" : sourceLanguage;
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        this.kind = kind == null ? TextKind.OTHER : kind;
        this.contextHint = contextHint == null ? "" : contextHint.trim();
        this.glossaryHint = GameTranslationHints.glossaryFor(this.targetLanguage);
    }

    public String getText() {
        return text;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public TextKind getKind() {
        return kind;
    }

    public String getContextHint() {
        return contextHint;
    }

    public String getGlossaryHint() {
        return glossaryHint;
    }
}

package org.universaltranslator.core;

/** 结构化整句翻译 */
final class StructuredTemplateTranslator {
    private static final int MAX_COMPLEX_CHAT_SPANS = 6;
    private static final int MAX_COMPLEX_HOLOGRAM_VALUES = 12;

    private StructuredTemplateTranslator() {
    }

    static boolean shouldUse(ProtectedText protectedText, TextKind kind) {
        return !protectedText.getValues().isEmpty()
                && !requiresLocalChatSegmentation(protectedText, kind)
                && !requiresHologramSegmentation(protectedText, kind)
                && (kind == TextKind.SIGN
                || kind == TextKind.HOLOGRAM
                || kind == TextKind.CHAT
                || kind == TextKind.SYSTEM_MESSAGE
                || NumericTranslationContext.of(protectedText).requiresWholeSentence()
                || StyledTranslationTemplate.contains(protectedText.getOriginal())
                || InlineTextureCode.matcher(protectedText.getOriginal()).find());
    }

    static boolean requiresLocalChatSegmentation(ProtectedText protectedText, TextKind kind) {
        return (kind == TextKind.CHAT || kind == TextKind.SYSTEM_MESSAGE)
                && styledSpanCount(protectedText.getOriginal()) > MAX_COMPLEX_CHAT_SPANS
                && StyledTranslationTemplate.contains(protectedText.getOriginal())
                && VisualTextLayout.containsLineBreak(protectedText.getOriginal());
    }

    private static int styledSpanCount(String text) {
        int count = 0;
        java.util.regex.Matcher matcher = StyledTranslationTemplate.matcher(text);
        while (matcher.find()) {
            if ("START".equals(matcher.group(2))) {
                count++;
            }
        }
        return count;
    }

    static boolean requiresHologramSegmentation(ProtectedText protectedText, TextKind kind) {
        return kind == TextKind.HOLOGRAM
                && protectedText.getValues().size() > MAX_COMPLEX_HOLOGRAM_VALUES
                && HologramTextLayout.containsTemplate(protectedText.getOriginal())
                && HologramTextLayout.translationBatch(protectedText.getOriginal()) != null;
    }

    static String translate(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            TranslationProvider provider,
            TranslationStore cache,
            RecentTranslationContext recentContext,
            String cacheFormatVersion
    ) throws Exception {
        String template = protectedText.getTemplate();
        String cacheKey = templateCacheKey(
                cacheFormatVersion, provider.id(), sourceLanguage, targetLanguage, template);
        String translated = cache.get(cacheKey);
        if (translated != null) {
            try {
                translated = TranslationOutputValidator.requireValid(
                        template, translated, targetLanguage);
                translated = ProtectedStyleTemplateValidator.requireValid(
                        protectedText, translated);
                String normalized = LocalizedNumericGrammar.normalizeTemplate(
                        protectedText, translated, targetLanguage);
                if (!normalized.equals(translated)) {
                    translated = ProtectedStyleTemplateValidator.requireValid(
                            protectedText, normalized);
                    cache.put(cacheKey, translated);
                }
            } catch (IllegalArgumentException invalidCachedValue) {
                translated = null;
            }
        }
        if (translated == null) {
            translated = requestValidatedTranslation(
                    protectedText, sourceLanguage, targetLanguage,
                    kind, provider, recentContext);
            if (translated == null) {
                throw new TemplateTranslationException(
                        "Provider returned an empty translation", false, null);
            }
            translated = restoreStructure(template, translated, kind);
            cache.put(cacheKey, translated);
        } else {
            translated = restoreStructure(template, translated, kind);
        }
        return rememberRestored(
                protectedText, translated, targetLanguage, kind, recentContext);
    }

    static String translateCached(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            TranslationProvider provider,
            TranslationStore cache,
            RecentTranslationContext recentContext,
            String cacheFormatVersion
    ) {
        String template = protectedText.getTemplate();
        String translated = cache.get(templateCacheKey(
                cacheFormatVersion, provider.id(), sourceLanguage, targetLanguage, template));
        if (translated == null) {
            throw new IllegalStateException("Cached translation is missing");
        }
        translated = TranslationOutputValidator.requireValid(
                template, translated, targetLanguage);
        translated = ProtectedStyleTemplateValidator.requireValid(
                protectedText, translated);
        translated = LocalizedNumericGrammar.normalizeTemplate(
                protectedText, translated, targetLanguage);
        translated = ProtectedStyleTemplateValidator.requireValid(
                protectedText, translated);
        translated = restoreStructure(template, translated, kind);
        return rememberRestored(
                protectedText, translated, targetLanguage, kind, recentContext);
    }

    private static String restoreStructure(String template, String translated, TextKind kind) {
        return kind == TextKind.SIGN
                ? StructuredPunctuation.restoreTokenAdjacent(template, translated) : translated;
    }

    private static String requestValidatedTranslation(
            ProtectedText protectedText,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            TranslationProvider provider,
            RecentTranslationContext recentContext
    ) throws Exception {
        String template = protectedText.getTemplate();
        String context = recentContext.snapshot(kind);
        String numericHint = NumericTranslationContext.of(protectedText)
                .describe(targetLanguage);
        IllegalArgumentException lastInvalidOutput = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String translated;
            try {
                translated = provider.translate(new TranslationRequest(
                        template, sourceLanguage, targetLanguage, kind,
                        context, numericHint));
            } catch (Exception providerFailure) {
                if (TranslationOutputValidator.isProtectedTokenFailure(providerFailure)) {
                    return requestWithAlternateTokens(
                            protectedText, template, sourceLanguage, targetLanguage, kind,
                            provider, recentContext, context, numericHint, providerFailure);
                }
                if (TranslationOutputValidator.isOutputValidationFailure(providerFailure)) {
                    lastInvalidOutput = asInvalidOutput(providerFailure);
                    continue;
                }
                throw providerFailure;
            }
            if (translated == null || translated.trim().isEmpty()) {
                continue;
            }
            try {
                String valid = TranslationOutputValidator.requireValid(
                        template, translated, targetLanguage);
                valid = ProtectedStyleTemplateValidator.requireValid(
                        protectedText, valid);
                valid = LocalizedNumericGrammar.normalizeTemplate(
                        protectedText, valid, targetLanguage);
                valid = ProtectedStyleTemplateValidator.requireValid(
                        protectedText, valid);
                recentContext.remember(template, valid, kind);
                return valid;
            } catch (IllegalArgumentException invalidOutput) {
                lastInvalidOutput = invalidOutput;
                // 异常译文不入缓存
            }
        }
        if (lastInvalidOutput != null) {
            if (TranslationOutputValidator.isProtectedTokenFailure(lastInvalidOutput)) {
                return requestWithAlternateTokens(
                        protectedText, template, sourceLanguage, targetLanguage, kind,
                        provider, recentContext, context, numericHint, lastInvalidOutput);
            }
            String message = lastInvalidOutput.getMessage();
            throw new TemplateTranslationException(
                    message == null ? "Invalid structured translation" : message,
                    false,
                    lastInvalidOutput);
        }
        return null;
    }

    private static IllegalArgumentException asInvalidOutput(Throwable failure) {
        if (failure instanceof IllegalArgumentException) {
            return (IllegalArgumentException) failure;
        }
        String message = failure == null ? null : failure.getMessage();
        IllegalArgumentException invalid = TranslationOutputValidator.invalidOutput(
                message == null ? "Invalid structured translation" : message);
        if (failure != null) {
            invalid.addSuppressed(failure);
        }
        return invalid;
    }

    private static String requestWithAlternateTokens(
            ProtectedText protectedText,
            String template,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind,
            TranslationProvider provider,
            RecentTranslationContext recentContext,
            String context,
            String numericHint,
            Throwable primaryFailure
    ) throws Exception {
        String alternateTemplate = TranslationOutputValidator.alternateProtectedTokens(template);
        if (alternateTemplate.equals(template)) {
            throw tokenFailure(primaryFailure);
        }
        try {
            String translated = provider.translate(new TranslationRequest(
                    alternateTemplate, sourceLanguage, targetLanguage, kind,
                    context,
                    TranslationOutputValidator.alternateProtectedTokens(numericHint)));
            if (translated == null || translated.trim().isEmpty()) {
                throw tokenFailure(primaryFailure);
            }
            String valid = TranslationOutputValidator.requireValid(
                    template, translated, targetLanguage);
            valid = ProtectedStyleTemplateValidator.requireValid(
                    protectedText, valid);
            valid = LocalizedNumericGrammar.normalizeTemplate(
                    protectedText, valid, targetLanguage);
            valid = ProtectedStyleTemplateValidator.requireValid(
                    protectedText, valid);
            recentContext.remember(template, valid, kind);
            return valid;
        } catch (TemplateTranslationException failure) {
            throw failure;
        } catch (Exception alternateFailure) {
            if (!TranslationOutputValidator.isProtectedTokenFailure(alternateFailure)) {
                throw alternateFailure;
            }
            if (primaryFailure != null && primaryFailure != alternateFailure) {
                alternateFailure.addSuppressed(primaryFailure);
            }
            throw tokenFailure(alternateFailure);
        }
    }

    private static TemplateTranslationException tokenFailure(Throwable cause) {
        String message = cause == null ? null : cause.getMessage();
        return new TemplateTranslationException(
                message == null ? "Invalid structured translation tokens" : message,
                true,
                cause);
    }

    private static String rememberRestored(
            ProtectedText protectedText,
            String translatedTemplate,
            String targetLanguage,
            TextKind kind,
            RecentTranslationContext recentContext
    ) {
        String restored = LocalizedDateOrder.normalize(
                protectedText.restore(translatedTemplate), targetLanguage);
        restored = LocalizedNumericGrammar.normalize(
                protectedText.getOriginal(), restored, targetLanguage);
        restored = LocalizedClauseOrder.normalize(
                protectedText.getOriginal(), restored, targetLanguage);
        recentContext.remember(protectedText.getOriginal(), restored, kind);
        return restored;
    }

    private static String templateCacheKey(
            String cacheFormatVersion,
            String providerId,
            String sourceLanguage,
            String targetLanguage,
            String template
    ) {
        return cacheFormatVersion + "\n" + providerId
                + "\n" + sourceLanguage + "\n" + targetLanguage + "\nstructured-template\n" + template;
    }

    static final class TemplateTranslationException extends IllegalStateException {
        private final boolean protectedTokenFailure;

        private TemplateTranslationException(
                String message, boolean protectedTokenFailure, Throwable cause) {
            super(message, cause);
            this.protectedTokenFailure = protectedTokenFailure;
        }

        boolean canFallbackToSegments() {
            return protectedTokenFailure;
        }
    }
}

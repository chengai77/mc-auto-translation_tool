package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 复杂片段批量翻译 */
final class SegmentBatchTranslator {
    private static final int MAX_BATCH_LINES = 16;
    private static final int MAX_BATCH_CHARACTERS = 800;

    private final TranslationProvider provider;
    private final TranslationStore cache;
    private final RecentTranslationContext recentContext;
    private final String cacheFormatVersion;
    private final String sourceLanguage;
    private final String targetLanguage;
    private final TextKind kind;

    private SegmentBatchTranslator(
            TranslationProvider provider,
            TranslationStore cache,
            RecentTranslationContext recentContext,
            String cacheFormatVersion,
            String sourceLanguage,
            String targetLanguage,
            TextKind kind
    ) {
        this.provider = provider;
        this.cache = cache;
        this.recentContext = recentContext;
        this.cacheFormatVersion = cacheFormatVersion;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.kind = kind;
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
        SegmentBatchTranslator translator = new SegmentBatchTranslator(
                provider, cache, recentContext, cacheFormatVersion,
                sourceLanguage, targetLanguage, kind);
        return translator.translate(protectedText, false);
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
        SegmentBatchTranslator translator = new SegmentBatchTranslator(
                provider, cache, recentContext, cacheFormatVersion,
                sourceLanguage, targetLanguage, kind);
        try {
            return translator.translate(protectedText, true);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cached translation is missing", failure);
        }
    }

    private String translate(ProtectedText protectedText, boolean cachedOnly) throws Exception {
        List<Part> parts = new ArrayList<Part>();
        LinkedHashMap<String, List<Part>> missing =
                new LinkedHashMap<String, List<Part>>();
        for (ProtectedText.Segment segment : protectedText.getSegments()) {
            if (segment.isProtectedValue()) {
                parts.add(Part.fixed(segment.text()));
            } else {
                appendTextParts(parts, missing, segment.text(), cachedOnly);
            }
        }
        if (!missing.isEmpty()) {
            if (cachedOnly) {
                throw new IllegalStateException("Cached translation is missing");
            }
            resolveMissing(missing);
        }
        StringBuilder output = new StringBuilder(protectedText.getOriginal().length() + 32);
        for (Part part : parts) {
            output.append(part.output());
        }
        return output.toString();
    }

    private void appendTextParts(
            List<Part> parts,
            Map<String, List<Part>> missing,
            String text,
            boolean cachedOnly
    ) {
        int cursor = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value != '\n' && value != '\r') {
                continue;
            }
            appendTextPart(parts, missing, text.substring(cursor, index), cachedOnly);
            if (value == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                parts.add(Part.fixed("\r\n"));
                index++;
            } else {
                parts.add(Part.fixed(String.valueOf(value)));
            }
            cursor = index + 1;
        }
        appendTextPart(parts, missing, text.substring(cursor), cachedOnly);
    }

    private void appendTextPart(
            List<Part> parts,
            Map<String, List<Part>> missing,
            String text,
            boolean cachedOnly
    ) {
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        String core = text.substring(start, end);
        if (!LanguageHeuristics.shouldTranslate(core, targetLanguage)) {
            parts.add(Part.fixed(text));
            return;
        }

        String translated = GameTranslationHints.localTranslation(core, targetLanguage);
        if (translated == null) {
            translated = cached(core);
        }
        Part part = Part.translatable(
                text.substring(0, start), core, text.substring(end), translated);
        parts.add(part);
        if (translated != null) {
            recentContext.remember(core, translated, kind);
            return;
        }
        if (cachedOnly) {
            throw new IllegalStateException("Cached translation is missing");
        }
        List<Part> waiting = missing.get(core);
        if (waiting == null) {
            waiting = new ArrayList<Part>();
            missing.put(core, waiting);
        }
        waiting.add(part);
    }

    private String cached(String source) {
        String translated = cache.get(cacheKey(source));
        if (translated == null) {
            return null;
        }
        try {
            return TranslationOutputValidator.requireValid(
                    source, translated, targetLanguage);
        } catch (IllegalArgumentException invalidCachedValue) {
            return null;
        }
    }

    private void resolveMissing(LinkedHashMap<String, List<Part>> missing) throws Exception {
        List<String> batch = new ArrayList<String>();
        int characters = 0;
        for (String source : missing.keySet()) {
            int added = source.length() + (batch.isEmpty() ? 0 : 1);
            if (!batch.isEmpty()
                    && (batch.size() >= MAX_BATCH_LINES
                    || characters + added > MAX_BATCH_CHARACTERS)) {
                resolveBatch(batch, missing);
                batch.clear();
                characters = 0;
                added = source.length();
            }
            batch.add(source);
            characters += added;
        }
        if (!batch.isEmpty()) {
            resolveBatch(batch, missing);
        }
    }

    private void resolveBatch(
            List<String> sources,
            Map<String, List<Part>> missing
    ) throws Exception {
        List<String> translated = requestBatch(sources);
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            String output = translated.get(index);
            cache.put(cacheKey(source), output);
            recentContext.remember(source, output, kind);
            for (Part part : missing.get(source)) {
                part.resolve(output);
            }
        }
    }

    private List<String> requestBatch(List<String> sources) throws Exception {
        if (sources.size() == 1) {
            return requestSingle(sources.get(0));
        }
        IllegalArgumentException invalidBatch;
        try {
            SegmentBatchProtocol.Batch batch = SegmentBatchProtocol.encode(sources);
            String translated = provider.translate(new TranslationRequest(
                    batch.request(), sourceLanguage, targetLanguage, kind,
                    recentContext.snapshot(kind)));
            if (translated == null || translated.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Provider returned an empty batch translation");
            }
            return batch.decode(translated, targetLanguage);
        } catch (IllegalArgumentException invalidOutput) {
            invalidBatch = invalidOutput;
        }

        int midpoint = sources.size() / 2;
        List<String> output = new ArrayList<String>(sources.size());
        try {
            output.addAll(requestBatch(new ArrayList<String>(
                    sources.subList(0, midpoint))));
            output.addAll(requestBatch(new ArrayList<String>(
                    sources.subList(midpoint, sources.size()))));
            return output;
        } catch (Exception fallbackFailure) {
            fallbackFailure.addSuppressed(invalidBatch);
            throw fallbackFailure;
        }
    }

    private List<String> requestSingle(String source) throws Exception {
        String translated = provider.translate(new TranslationRequest(
                source, sourceLanguage, targetLanguage, kind,
                recentContext.snapshot(kind)));
        return java.util.Collections.singletonList(
                TranslationOutputValidator.requireValid(
                        source, translated, targetLanguage));
    }

    private String cacheKey(String source) {
        return cacheFormatVersion + "\n" + provider.id()
                + "\n" + sourceLanguage + "\n" + targetLanguage + "\n" + source;
    }

    private static final class Part {
        private final String fixed;
        private final String prefix;
        private final String source;
        private final String suffix;
        private String translated;

        private Part(
                String fixed,
                String prefix,
                String source,
                String suffix,
                String translated
        ) {
            this.fixed = fixed;
            this.prefix = prefix;
            this.source = source;
            this.suffix = suffix;
            this.translated = translated;
        }

        private static Part fixed(String value) {
            return new Part(value, null, null, null, null);
        }

        private static Part translatable(
                String prefix,
                String source,
                String suffix,
                String translated
        ) {
            return new Part(null, prefix, source, suffix, translated);
        }

        private void resolve(String value) {
            translated = value;
        }

        private String output() {
            if (fixed != null) {
                return fixed;
            }
            return prefix + (translated == null ? source : translated) + suffix;
        }
    }
}

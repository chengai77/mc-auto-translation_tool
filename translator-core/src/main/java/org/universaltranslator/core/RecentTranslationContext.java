package org.universaltranslator.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 提示词上下文 */
final class RecentTranslationContext {
    private static final int MAX_ENTRIES = 6;
    private static final int MAX_TEXT_LENGTH = 80;
    private static final int MAX_SNAPSHOT_LENGTH = 320;
    private static final int MIN_CONTEXT_SOURCE_LENGTH = 12;

    private final Deque<Entry> entries = new ArrayDeque<Entry>();

    synchronized void remember(String source, String translated, TextKind kind) {
        String cleanSource = clean(source);
        String cleanTranslated = clean(translated);
        if (cleanSource.isEmpty() || cleanTranslated.isEmpty()
                || cleanSource.equals(cleanTranslated)) {
            return;
        }
        removeDuplicate(cleanSource);
        entries.addFirst(new Entry(cleanSource, cleanTranslated, kind));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    synchronized String snapshot(TextKind preferredKind, String currentSource) {
        if (preferredKind == TextKind.CHAT || preferredKind == TextKind.SYSTEM_MESSAGE) {
            return "";
        }
        if (currentSource == null || currentSource.trim().length() < MIN_CONTEXT_SOURCE_LENGTH) {
            return "";
        }
        Set<String> currentTerms = significantTerms(clean(currentSource));
        if (currentTerms.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder(MAX_SNAPSHOT_LENGTH);
        appendEntries(output, preferredKind, currentTerms);
        return output.length() > MAX_SNAPSHOT_LENGTH
                ? output.substring(0, MAX_SNAPSHOT_LENGTH) : output.toString();
    }

    private void appendEntries(
            StringBuilder output, TextKind preferredKind, Set<String> currentTerms) {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext() && output.length() < MAX_SNAPSHOT_LENGTH) {
            Entry entry = iterator.next();
            if (preferredKind == null || preferredKind != entry.kind
                    || !sharesTerm(currentTerms, entry.source)) {
                continue;
            }
            if (output.length() > 0) {
                output.append("; ");
            }
            output.append(entry.source).append("=>").append(entry.translated);
        }
    }

    private static boolean sharesTerm(Set<String> currentTerms, String previousSource) {
        for (String term : significantTerms(previousSource)) {
            if (currentTerms.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> significantTerms(String value) {
        Set<String> terms = new HashSet<String>();
        if (value == null || value.isEmpty()) {
            return terms;
        }
        for (String term : value.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (term.length() >= 3) {
                terms.add(term);
            }
        }
        return terms;
    }

    private void removeDuplicate(String source) {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().source.equals(source)) {
                iterator.remove();
            }
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = StyledTranslationTemplate.strip(
                InlineTextureCode.strip(TranslationTextStyling.stripLegacyFormatting(value)))
                .replaceAll("__UT_\\d+__", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > MAX_TEXT_LENGTH || containsSensitiveBoundary(cleaned)) {
            return "";
        }
        return cleaned;
    }

    private static boolean containsSensitiveBoundary(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("http://")
                || lower.contains("https://")
                || lower.contains("@")
                || lower.matches(".*\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b.*");
    }

    private static final class Entry {
        private final String source;
        private final String translated;
        private final TextKind kind;

        private Entry(String source, String translated, TextKind kind) {
            this.source = source;
            this.translated = translated;
            this.kind = kind == null ? TextKind.OTHER : kind;
        }
    }
}

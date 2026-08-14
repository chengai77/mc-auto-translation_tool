package org.universaltranslator.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Small bounded context memory for provider prompts. */
final class RecentTranslationContext {
    private static final int MAX_ENTRIES = 8;
    private static final int MAX_TEXT_LENGTH = 80;
    private static final int MAX_SNAPSHOT_LENGTH = 480;

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

    synchronized String snapshot(TextKind preferredKind) {
        StringBuilder output = new StringBuilder(MAX_SNAPSHOT_LENGTH);
        appendEntries(output, preferredKind, true);
        appendEntries(output, preferredKind, false);
        return output.length() > MAX_SNAPSHOT_LENGTH
                ? output.substring(0, MAX_SNAPSHOT_LENGTH) : output.toString();
    }

    private void appendEntries(StringBuilder output, TextKind preferredKind, boolean sameKindOnly) {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext() && output.length() < MAX_SNAPSHOT_LENGTH) {
            Entry entry = iterator.next();
            boolean sameKind = preferredKind != null && preferredKind == entry.kind;
            if (sameKindOnly != sameKind) {
                continue;
            }
            if (output.length() > 0) {
                output.append("; ");
            }
            output.append(entry.source).append("=>").append(entry.translated);
        }
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
        String cleaned = TranslationTextStyling.stripLegacyFormatting(value)
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

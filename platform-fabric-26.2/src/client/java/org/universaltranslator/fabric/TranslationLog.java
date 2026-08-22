package org.universaltranslator.fabric;

import org.universaltranslator.core.InlineTextureCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TranslationLog {
    private static final int MAX_ENTRIES = 300;
    private static final List<Entry> ENTRIES = new ArrayList<Entry>();

    private TranslationLog() {
    }

    static synchronized void add(String original, String translated) {
        if (original == null || translated == null) {
            return;
        }
        String cleanOriginal = InlineTextureCode.strip(original).trim();
        String cleanTranslated = InlineTextureCode.strip(translated).trim();
        if (cleanOriginal.isEmpty() || cleanTranslated.isEmpty()
                || cleanOriginal.equals(cleanTranslated)) {
            return;
        }
        for (int index = 0; index < ENTRIES.size(); index++) {
            Entry entry = ENTRIES.get(index);
            if (entry.original.equals(cleanOriginal)) {
                if (entry.translated.equals(cleanTranslated)) {
                    return;
                }
                ENTRIES.remove(index);
                break;
            }
        }
        ENTRIES.add(0, new Entry(cleanOriginal, cleanTranslated, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(ENTRIES.size() - 1);
        }
    }

    static synchronized void clear() {
        ENTRIES.clear();
    }

    static synchronized List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<Entry>(ENTRIES));
    }

    static synchronized Entry latest() {
        return ENTRIES.isEmpty() ? null : ENTRIES.get(0);
    }

    static final class Entry {
        final String original;
        final String translated;
        final long timestampMillis;

        Entry(String original, String translated, long timestampMillis) {
            this.original = original;
            this.translated = translated;
            this.timestampMillis = timestampMillis;
        }
    }
}

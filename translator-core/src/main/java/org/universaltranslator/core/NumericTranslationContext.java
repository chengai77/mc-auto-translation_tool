package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 数值占位语义 */
final class NumericTranslationContext {
    private static final char ANCHOR = '\ue000';
    private static final char BLOCKER = '\ue001';
    private static final int MAX_ENTRIES = 12;
    private static final int WINDOW_RADIUS = 42;
    private static final String NUMBER_SOURCE =
            "(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)";
    private static final Pattern PLAIN_NUMBER = Pattern.compile(NUMBER_SOURCE);
    private static final Pattern PERCENTAGE = Pattern.compile(NUMBER_SOURCE + "%");
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            NUMBER_SOURCE + "(?:ms|s|m|h|d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("__UT_(\\d+)__");
    private static final Pattern COMPLETION_VERB = Pattern.compile(
            "(?iu)\\b(?:complete(?:d|s|ing)?|finish(?:ed|es|ing)?|clear(?:ed|s|ing)?"
                    + "|finir|finis|finit|fini|finie|finies|terminer|termine|terminé|achever"
                    + "|completar|completado|completarla|terminar|terminado"
                    + "|concluir|concluido|concluído|beenden|abschließen|abgeschlossen|fertigstellen"
                    + "|completare|completato|finire|voltooien|afronden"
                    + "|ukończyć|ukonczyc|zakończyć|zakonczyc"
                    + "|завершить|закончить|пройти|выполнить)\\b");
    private static final Set<String> INDEX_CUES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "level", "lvl", "stage", "chapter", "version", "ver", "id",
                    "number", "no", "rank", "tier", "floor", "room", "phase", "wave",
                    "niveau", "étape", "etape", "chapitre", "version", "salle", "vague",
                    "nivel", "etapa", "capítulo", "capitulo", "sala", "ola",
                    "stufe", "kapitel", "raum", "welle")));

    private final List<Entry> entries;

    private NumericTranslationContext(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    static NumericTranslationContext of(ProtectedText protectedText) {
        if (protectedText == null) {
            return new NumericTranslationContext(Collections.<Entry>emptyList());
        }
        List<Entry> entries = new ArrayList<Entry>();
        List<String> values = protectedText.getValues();
        for (int index = 0; index < values.size() && entries.size() < MAX_ENTRIES; index++) {
            Kind kind = kindOf(values.get(index));
            if (kind == null) {
                continue;
            }
            String token = token(index);
            String visible = visibleTemplate(protectedText, token);
            int anchor = visible.indexOf(ANCHOR);
            boolean likelyCount = kind == Kind.PLAIN_NUMBER
                    && anchor >= 0 && likelyCount(visible, anchor);
            boolean completionPercentage = kind == Kind.PERCENTAGE
                    && anchor >= 0 && completionPercentage(visible, anchor);
            boolean wholeSentence = kind == Kind.PERCENTAGE || likelyCount
                    || (kind == Kind.NUMBER_WITH_UNIT
                    && anchor >= 0 && hasWordAfter(visible, anchor + 1));
            entries.add(new Entry(
                    token, values.get(index), kind, likelyCount,
                    completionPercentage, wholeSentence,
                    sourceWindow(protectedText.getTemplate(), token)));
        }
        return new NumericTranslationContext(entries);
    }

    boolean requiresWholeSentence() {
        for (Entry entry : entries) {
            if (entry.wholeSentence()) {
                return true;
            }
        }
        return false;
    }

    List<Entry> entries() {
        return entries;
    }

    String describe(String targetLanguage) {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder(720);
        output.append("numeric_token_reference:\n");
        for (Entry entry : entries) {
            output.append(entry.token()).append('=').append(entry.kind().label());
            if (entry.likelyCount()) {
                output.append("; role=count_or_measurement");
            }
            if (entry.completionPercentage()) {
                output.append("; role=completion_percentage");
            }
            output.append("; source_window=\"")
                    .append(entry.sourceWindow()).append("\"\n");
        }
        output.append("Use this metadata only to interpret protected token types; never output it. ")
                .append("Infer whether a plain number is a count, index, score, date, version, or measurement. ");
        if (TargetLanguage.isSimplifiedChinese(targetLanguage)
                || TargetLanguage.isTraditionalChinese(targetLanguage)) {
            output.append("When a number counts a Chinese noun, add a natural classifier even though the numeral is hidden. ")
                    .append("Express a completion percentage with natural completion/progress predicate-object order; ")
                    .append("do not treat it as a destination or object.");
        }
        return output.toString();
    }

    static boolean isFormattingValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.length() == 2 && value.charAt(0) == '\u00a7') {
            return true;
        }
        return value.matches("\\{UT_(?:STYLE|HOLOGRAM_BLOCK)_\\d+_(?:START|END)\\}");
    }

    private static Kind kindOf(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (PERCENTAGE.matcher(value).matches()) {
            return Kind.PERCENTAGE;
        }
        if (NUMBER_WITH_UNIT.matcher(value).matches()) {
            return Kind.NUMBER_WITH_UNIT;
        }
        return PLAIN_NUMBER.matcher(value).matches() ? Kind.PLAIN_NUMBER : null;
    }

    private static boolean likelyCount(String text, int anchor) {
        int previousContent = previousContent(text, anchor);
        if (previousContent >= 0) {
            char prefix = text.charAt(previousContent);
            if (prefix == '+' || prefix == '-' || prefix == '\u2212') {
                return false;
            }
        }
        String next = wordAfter(text, anchor + 1);
        if (next.isEmpty()) {
            return false;
        }
        String previous = wordBefore(text, anchor);
        return previous.isEmpty()
                || !INDEX_CUES.contains(previous.toLowerCase(Locale.ROOT));
    }

    private static boolean completionPercentage(String text, int anchor) {
        int start = clauseStart(text, anchor);
        String before = text.substring(Math.max(start, anchor - 96), anchor);
        return COMPLETION_VERB.matcher(before).find()
                && hasWordAfter(text, anchor + 1);
    }

    private static boolean hasWordAfter(String text, int start) {
        return !wordAfter(text, start).isEmpty();
    }

    private static String wordAfter(String text, int start) {
        int cursor = start;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor < text.length() && text.charAt(cursor) == '-') {
            cursor++;
            while (cursor < text.length()
                    && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
        }
        int wordStart = cursor;
        while (cursor < text.length() && Character.isLetter(text.charAt(cursor))) {
            cursor++;
        }
        return wordStart == cursor ? "" : text.substring(wordStart, cursor);
    }

    private static String wordBefore(String text, int end) {
        int cursor = end - 1;
        while (cursor >= 0 && isPreviousSkippable(text.charAt(cursor))) {
            cursor--;
        }
        int wordEnd = cursor + 1;
        while (cursor >= 0 && Character.isLetter(text.charAt(cursor))) {
            cursor--;
        }
        return wordEnd == cursor + 1 ? "" : text.substring(cursor + 1, wordEnd);
    }

    private static int previousContent(String text, int end) {
        for (int index = end - 1; index >= 0; index--) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isPreviousSkippable(char value) {
        return Character.isWhitespace(value) || value == ':' || value == '#'
                || value == '.' || value == '-' || value == '(' || value == '[';
    }

    private static int clauseStart(String text, int end) {
        for (int index = end - 1; index >= 0; index--) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r' || value == '.' || value == '!'
                    || value == '?' || value == ';') {
                return index + 1;
            }
        }
        return 0;
    }

    private static String visibleTemplate(ProtectedText protectedText, String anchorToken) {
        String template = protectedText.getTemplate();
        StringBuilder visible = new StringBuilder(template.length());
        Matcher matcher = TOKEN.matcher(template);
        int cursor = 0;
        while (matcher.find()) {
            visible.append(template, cursor, matcher.start());
            if (matcher.group().equals(anchorToken)) {
                visible.append(ANCHOR);
            } else {
                int index = Integer.parseInt(matcher.group(1));
                String value = index < protectedText.getValues().size()
                        ? protectedText.getValues().get(index) : "";
                if (!isFormattingValue(value)) {
                    visible.append(BLOCKER);
                }
            }
            cursor = matcher.end();
        }
        visible.append(template, cursor, template.length());
        return visible.toString();
    }

    private static String sourceWindow(String template, String token) {
        int index = template.indexOf(token);
        if (index < 0) {
            return token;
        }
        int start = Math.max(0, index - WINDOW_RADIUS);
        int end = Math.min(template.length(), index + token.length() + WINDOW_RADIUS);
        String window = template.substring(start, end)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('"', '\'')
                .replaceAll("\\s+", " ")
                .trim();
        return (start > 0 ? "..." : "") + window + (end < template.length() ? "..." : "");
    }

    private static String token(int index) {
        return "__UT_" + index + "__";
    }

    enum Kind {
        PLAIN_NUMBER("plain_number"),
        PERCENTAGE("percentage"),
        NUMBER_WITH_UNIT("number_with_unit");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    static final class Entry {
        private final String token;
        private final String value;
        private final Kind kind;
        private final boolean likelyCount;
        private final boolean completionPercentage;
        private final boolean wholeSentence;
        private final String sourceWindow;

        private Entry(
                String token,
                String value,
                Kind kind,
                boolean likelyCount,
                boolean completionPercentage,
                boolean wholeSentence,
                String sourceWindow
        ) {
            this.token = token;
            this.value = value;
            this.kind = kind;
            this.likelyCount = likelyCount;
            this.completionPercentage = completionPercentage;
            this.wholeSentence = wholeSentence;
            this.sourceWindow = sourceWindow;
        }

        String token() { return token; }
        String value() { return value; }
        Kind kind() { return kind; }
        boolean likelyCount() { return likelyCount; }
        boolean completionPercentage() { return completionPercentage; }
        boolean wholeSentence() { return wholeSentence; }
        String sourceWindow() { return sourceWindow; }
    }
}

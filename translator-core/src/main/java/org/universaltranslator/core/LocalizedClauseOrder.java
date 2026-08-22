package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 中文从句语序 */
final class LocalizedClauseOrder {
    private static final Pattern ENGLISH_WHEN = Pattern.compile(
            "(?i)\\b(?:when|whenever)\\b");
    private static final Pattern INTERNAL_TOKEN = Pattern.compile("__UT_\\d+__");
    private static final Pattern SENTENCE = Pattern.compile("[^\\n。！？!?]+[。！？!?]?");
    private static final Pattern ZH_CN_CONDITION = Pattern.compile(
            "(?:当|在)[^\\n。！？!?]{1,96}?(?:的时候|时)");
    private static final Pattern ZH_TW_CONDITION = Pattern.compile(
            "(?:當|在)[^\\n。！？!?]{1,96}?(?:的時候|時)");
    private static final Pattern HIDDEN = Pattern.compile(
            "(?:__UT_\\d+__"
                    + "|\\{UT_HOLOGRAM_BLOCK_\\d+_(?:START|END)}"
                    + "|\\{UT_STYLE_\\d+_(?:START|END)}"
                    + "|" + InlineTextureCode.REGEX_SOURCE
                    + "|\\u00a7[0-9A-FK-ORa-fk-or])");

    private LocalizedClauseOrder() {
    }

    static void requireNatural(String source, String translated, String targetLanguage) {
        if (!normalize(source, translated, targetLanguage).equals(translated)) {
            throw new IllegalArgumentException(
                    "Translation uses source-language Chinese clause order");
        }
    }

    static String normalize(String source, String translated, String targetLanguage) {
        Pattern condition;
        if (TargetLanguage.isSimplifiedChinese(targetLanguage)) {
            condition = ZH_CN_CONDITION;
        } else if (TargetLanguage.isTraditionalChinese(targetLanguage)) {
            condition = ZH_TW_CONDITION;
        } else {
            return translated;
        }
        if (translated == null || translated.isEmpty()
                || !ENGLISH_WHEN.matcher(visible(source)).find()
                || INTERNAL_TOKEN.matcher(translated).find()) {
            return translated;
        }

        String output = translated;
        for (int attempt = 0; attempt < 8; attempt++) {
            Correction correction = findCorrection(output, condition);
            if (correction == null) {
                break;
            }
            String normalized = correction.apply(output);
            if (normalized.equals(output)) {
                break;
            }
            output = normalized;
        }
        return output;
    }

    private static Correction findCorrection(String translated, Pattern condition) {
        Projection projection = Projection.create(translated);
        List<StyleRange> styles = styleRanges(translated);
        Matcher sentences = SENTENCE.matcher(projection.text());
        Correction latest = null;
        while (sentences.find()) {
            int sentenceStart = firstContentIndex(
                    projection.text(), sentences.start(), sentences.end());
            int sentenceEnd = trimSentencePunctuationEnd(
                    projection.text(), sentenceStart, sentences.end());
            if (sentenceStart >= sentenceEnd) {
                continue;
            }
            String sentence = projection.text().substring(sentenceStart, sentenceEnd);
            Matcher conditions = condition.matcher(sentence);
            while (conditions.find()) {
                if (isTrailingCondition(sentence, conditions)) {
                    int conditionVisibleStart = sentenceStart + conditions.start();
                    int conditionVisibleEnd = sentenceStart + conditions.end();
                    int insertion = projection.rawStart(sentenceStart);
                    insertion = expandInsertion(insertion, styles);
                    boolean comma = true;
                    boolean inlineCondition = false;
                    String conditionText = sentence.substring(
                            conditions.start(), conditions.end());
                    int predicateStart = conditionText.startsWith("在")
                            || conditionText.startsWith("当")
                            ? trailingPredicateStart(
                            sentence.substring(0, conditions.start())) : -1;
                    if (predicateStart >= 0) {
                        int predicateInsertion = expandPredicateInsertion(
                                translated,
                                projection.rawStart(sentenceStart + predicateStart),
                                styles);
                        if (predicateInsertion >= insertion) {
                            insertion = predicateInsertion;
                            comma = false;
                            inlineCondition = true;
                        }
                    }
                    RawRange conditionRange = expandCondition(
                            translated,
                            projection.rawStart(conditionVisibleStart),
                            projection.rawEnd(conditionVisibleEnd),
                            styles);
                    if (conditionRange != null && insertion < conditionRange.start()) {
                        latest = new Correction(
                                insertion, conditionRange.start(),
                                conditionRange.end(), comma, inlineCondition);
                    }
                }
            }
        }
        return latest;
    }

    private static boolean isTrailingCondition(String sentence, Matcher condition) {
        if (!sentence.substring(condition.end()).trim().isEmpty()) {
            return false;
        }
        return hanCount(sentence.substring(0, condition.start())) >= 4;
    }

    private static int hanCount(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static String visible(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Projection.create(text).text();
    }

    private static int firstContentIndex(String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    private static int trimSentencePunctuationEnd(String text, int start, int end) {
        while (end > start) {
            char value = text.charAt(end - 1);
            if (Character.isWhitespace(value) || value == '。' || value == '！'
                    || value == '？' || value == '!' || value == '?') {
                end--;
                continue;
            }
            break;
        }
        return end;
    }

    private static int expandInsertion(int insertion, List<StyleRange> styles) {
        for (StyleRange style : styles) {
            if (style.containsContentPosition(insertion)) {
                return style.markerStart();
            }
        }
        return insertion;
    }

    private static int expandPredicateInsertion(
            String text, int insertion, List<StyleRange> styles) {
        for (StyleRange style : styles) {
            if (!style.containsContentPosition(insertion)) {
                continue;
            }
            return hasVisibleText(text, style.contentStart(), insertion)
                    ? -1 : style.markerStart();
        }
        return insertion;
    }

    private static int trailingPredicateStart(String prefix) {
        String[] anchors = {
                "将不会", "將不會", "不会再", "不會再", "不会", "不會",
                "不能", "无法", "無法", "不再", "未能",
                "将会", "將會", "可以", "会", "會", "将", "將"
        };
        int selected = -1;
        int selectedEnd = -1;
        int selectedLength = 0;
        for (String anchor : anchors) {
            int index = prefix.lastIndexOf(anchor);
            int end = index + anchor.length();
            if (index >= 0 && (end > selectedEnd
                    || (end == selectedEnd && anchor.length() > selectedLength))) {
                selected = index;
                selectedEnd = end;
                selectedLength = anchor.length();
            }
        }
        if (selected < 0 || prefix.length() - selected > 24
                || hanCount(prefix.substring(selected)) < 2) {
            return -1;
        }
        return selected;
    }

    private static RawRange expandCondition(
            String text, int start, int end, List<StyleRange> styles) {
        int expandedStart = start;
        int expandedEnd = end;
        for (StyleRange style : styles) {
            if (!style.intersectsContent(start, end)) {
                continue;
            }
            if (hasVisibleText(text, style.contentStart(), start)
                    || hasVisibleText(text, end, style.contentEnd())) {
                return null;
            }
            expandedStart = Math.min(expandedStart, style.markerStart());
            expandedEnd = Math.max(expandedEnd, style.markerEnd());
        }
        return new RawRange(expandedStart, expandedEnd);
    }

    private static boolean hasVisibleText(String text, int start, int end) {
        if (start >= end) {
            return false;
        }
        return !visible(text.substring(start, end)).trim().isEmpty();
    }

    private static List<StyleRange> styleRanges(String text) {
        Map<Integer, MutableStyleRange> pending =
                new TreeMap<Integer, MutableStyleRange>();
        Matcher matcher = StyledTranslationTemplate.matcher(text);
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            MutableStyleRange range = pending.get(Integer.valueOf(id));
            if (range == null) {
                range = new MutableStyleRange();
                pending.put(Integer.valueOf(id), range);
            }
            if ("START".equals(matcher.group(2))) {
                range.markerStart = matcher.start();
                range.contentStart = matcher.end();
            } else {
                range.contentEnd = matcher.start();
                range.markerEnd = matcher.end();
            }
        }
        List<StyleRange> output = new ArrayList<StyleRange>(pending.size());
        for (MutableStyleRange range : pending.values()) {
            if (range.markerStart >= 0 && range.contentStart >= 0
                    && range.contentEnd >= range.contentStart && range.markerEnd >= 0) {
                output.add(new StyleRange(
                        range.markerStart, range.contentStart,
                        range.contentEnd, range.markerEnd));
            }
        }
        return output;
    }

    private static String stripTrailingSeparator(String text) {
        Projection projection = Projection.create(text);
        List<RawRange> removals = new ArrayList<RawRange>();
        int index = projection.text().length() - 1;
        while (index >= 0 && Character.isWhitespace(projection.text().charAt(index))) {
            removals.add(new RawRange(
                    projection.rawStart(index), projection.rawEnd(index + 1)));
            index--;
        }
        if (index >= 0 && isClauseSeparator(projection.text().charAt(index))) {
            removals.add(new RawRange(
                    projection.rawStart(index), projection.rawEnd(index + 1)));
        }
        StringBuilder output = new StringBuilder(text);
        for (RawRange removal : removals) {
            output.delete(removal.start(), removal.end());
        }
        return output.toString();
    }

    private static boolean isClauseSeparator(char value) {
        return value == ',' || value == '，' || value == ';'
                || value == '；' || value == ':' || value == '：';
    }

    private static String normalizeInlineCondition(String text) {
        Projection projection = Projection.create(text);
        int index = firstContentIndex(
                projection.text(), 0, projection.text().length());
        if (index >= projection.text().length()
                || projection.text().charAt(index) != '当') {
            return text;
        }
        int start = projection.rawStart(index);
        int end = projection.rawEnd(index + 1);
        return text.substring(0, start) + "在" + text.substring(end);
    }

    private static final class Correction {
        private final int insertion;
        private final int conditionStart;
        private final int conditionEnd;
        private final boolean comma;
        private final boolean inlineCondition;

        private Correction(
                int insertion,
                int conditionStart,
                int conditionEnd,
                boolean comma,
                boolean inlineCondition
        ) {
            this.insertion = insertion;
            this.conditionStart = conditionStart;
            this.conditionEnd = conditionEnd;
            this.comma = comma;
            this.inlineCondition = inlineCondition;
        }

        String apply(String text) {
            String beforeCondition = stripTrailingSeparator(
                    text.substring(insertion, conditionStart));
            if (visible(beforeCondition).trim().isEmpty()) {
                return text;
            }
            String condition = text.substring(conditionStart, conditionEnd);
            if (inlineCondition) {
                condition = normalizeInlineCondition(condition);
            }
            return text.substring(0, insertion)
                    + condition
                    + (comma ? "，" : "") + beforeCondition
                    + text.substring(conditionEnd);
        }
    }

    private static final class Projection {
        private final String text;
        private final List<Integer> rawStarts;
        private final List<Integer> rawEnds;

        private Projection(
                String text, List<Integer> rawStarts, List<Integer> rawEnds) {
            this.text = text;
            this.rawStarts = rawStarts;
            this.rawEnds = rawEnds;
        }

        static Projection create(String raw) {
            String source = raw == null ? "" : raw;
            StringBuilder visible = new StringBuilder(source.length());
            List<Integer> starts = new ArrayList<Integer>();
            List<Integer> ends = new ArrayList<Integer>();
            Matcher hidden = HIDDEN.matcher(source);
            int cursor = 0;
            while (hidden.find()) {
                append(source, cursor, hidden.start(), visible, starts, ends);
                cursor = hidden.end();
            }
            append(source, cursor, source.length(), visible, starts, ends);
            return new Projection(visible.toString(), starts, ends);
        }

        private static void append(
                String source,
                int start,
                int end,
                StringBuilder visible,
                List<Integer> starts,
                List<Integer> ends) {
            for (int index = start; index < end; index++) {
                visible.append(source.charAt(index));
                starts.add(Integer.valueOf(index));
                ends.add(Integer.valueOf(index + 1));
            }
        }

        String text() {
            return text;
        }

        int rawStart(int visibleIndex) {
            return visibleIndex >= rawStarts.size()
                    ? (rawEnds.isEmpty() ? 0 : rawEnds.get(rawEnds.size() - 1).intValue())
                    : rawStarts.get(visibleIndex).intValue();
        }

        int rawEnd(int visibleEnd) {
            return visibleEnd <= 0 ? 0 : rawEnds.get(visibleEnd - 1).intValue();
        }
    }

    private static final class MutableStyleRange {
        private int markerStart = -1;
        private int contentStart = -1;
        private int contentEnd = -1;
        private int markerEnd = -1;
    }

    private static final class StyleRange {
        private final int markerStart;
        private final int contentStart;
        private final int contentEnd;
        private final int markerEnd;

        private StyleRange(
                int markerStart, int contentStart, int contentEnd, int markerEnd) {
            this.markerStart = markerStart;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
            this.markerEnd = markerEnd;
        }

        int markerStart() { return markerStart; }
        int contentStart() { return contentStart; }
        int contentEnd() { return contentEnd; }
        int markerEnd() { return markerEnd; }

        boolean containsContentPosition(int position) {
            return position >= contentStart && position < contentEnd;
        }

        boolean intersectsContent(int start, int end) {
            return contentStart < end && start < contentEnd;
        }
    }

    private static final class RawRange {
        private final int start;
        private final int end;

        private RawRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int start() { return start; }
        int end() { return end; }
    }
}

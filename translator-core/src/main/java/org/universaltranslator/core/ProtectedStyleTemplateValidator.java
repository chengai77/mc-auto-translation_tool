package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 校验样式占位符结构 */
final class ProtectedStyleTemplateValidator {
    private static final Pattern STYLE_TOKEN_VALUE = Pattern.compile(
            "\\{UT_STYLE_(\\d+)_(START|END)\\}");
    private ProtectedStyleTemplateValidator() {
    }

    static String requireValid(
            ProtectedText protectedText, String translatedTemplate) {
        if (protectedText == null || translatedTemplate == null) {
            throw new IllegalArgumentException(
                    "Protected translation template is required");
        }
        String output = TranslationOutputValidator.canonicalizeProtectedTokens(
                translatedTemplate);
        List<StyleSpan> spans = styleSpans(protectedText.getValues());
        if (spans.isEmpty()) {
            return output;
        }
        requireStyleStructure(protectedText.getTemplate(), spans);
        requireStyleStructure(output, spans);
        return output;
    }

    private static List<StyleSpan> styleSpans(List<String> protectedValues) {
        Map<Integer, int[]> indexes = new TreeMap<Integer, int[]>();
        for (int index = 0; index < protectedValues.size(); index++) {
            Matcher matcher = STYLE_TOKEN_VALUE.matcher(protectedValues.get(index));
            if (!matcher.matches()) {
                continue;
            }
            int id = Integer.parseInt(matcher.group(1));
            if (id < 0 || id >= StyledTranslationTemplate.MAX_SPANS) {
                throw failure("styled text id is out of range");
            }
            int[] pair = indexes.get(Integer.valueOf(id));
            if (pair == null) {
                pair = new int[]{-1, -1};
                indexes.put(Integer.valueOf(id), pair);
            }
            int boundary = "START".equals(matcher.group(2)) ? 0 : 1;
            if (pair[boundary] >= 0) {
                throw failure("styled text marker is duplicated");
            }
            pair[boundary] = index;
        }
        List<StyleSpan> spans = new ArrayList<StyleSpan>(indexes.size());
        for (int[] pair : indexes.values()) {
            if (pair[0] < 0 || pair[1] < 0) {
                throw failure("styled text marker is incomplete");
            }
            spans.add(new StyleSpan(pair[0], pair[1]));
        }
        return spans;
    }

    private static void requireStyleStructure(
            String template, List<StyleSpan> spans) {
        List<StyleRange> ranges = new ArrayList<StyleRange>(spans.size());
        for (StyleSpan span : spans) {
            String startToken = token(span.startTokenIndex());
            String endToken = token(span.endTokenIndex());
            int markerStart = uniqueIndexOf(template, startToken);
            int contentStart = markerStart + startToken.length();
            int markerEnd = uniqueIndexOf(template, endToken);
            if (markerStart < 0 || markerEnd < contentStart) {
                throw failure("styled text markers are out of order");
            }
            ranges.add(new StyleRange(
                    markerStart, markerEnd + endToken.length()));
        }
        for (int first = 0; first < ranges.size(); first++) {
            for (int second = first + 1; second < ranges.size(); second++) {
                if (ranges.get(first).overlaps(ranges.get(second))) {
                    throw failure("styled text ranges overlap");
                }
            }
        }
    }

    private static int uniqueIndexOf(String text, String token) {
        int index = text.indexOf(token);
        if (index < 0 || text.indexOf(token, index + token.length()) >= 0) {
            return -1;
        }
        return index;
    }

    private static String token(int index) {
        return "__UT_" + index + "__";
    }

    private static IllegalArgumentException failure(String detail) {
        return TranslationOutputValidator.protectedTokenFailure(detail);
    }

    private static final class StyleSpan {
        private final int startTokenIndex;
        private final int endTokenIndex;

        private StyleSpan(int startTokenIndex, int endTokenIndex) {
            this.startTokenIndex = startTokenIndex;
            this.endTokenIndex = endTokenIndex;
        }

        int startTokenIndex() { return startTokenIndex; }
        int endTokenIndex() { return endTokenIndex; }
    }

    private static final class StyleRange {
        private final int start;
        private final int end;

        private StyleRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        boolean overlaps(StyleRange other) {
            return start < other.end && other.start < end;
        }
    }
}

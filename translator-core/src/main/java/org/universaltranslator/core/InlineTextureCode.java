package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InlineTextureCode {
    static final String REGEX_SOURCE =
            "\\[(?:[A-Za-z0-9_.-]+:)?[A-Za-z0-9_.-]+/[A-Za-z0-9_./:@+\\-=#%~]+\\]";
    private static final Pattern PATTERN = Pattern.compile(REGEX_SOURCE);
    private static final Pattern ZERO_WIDTH_MARKER = Pattern.compile(
            "\\{UT_STYLE_\\d+_(?:START|END)\\}|\\u00a7[0-9A-FK-ORa-fk-or]");

    private InlineTextureCode() {
    }

    public static Matcher matcher(String text) {
        return PATTERN.matcher(text == null ? "" : text);
    }

    public static boolean isExact(String text) {
        return text != null && PATTERN.matcher(text).matches();
    }

    public static boolean hasSameSequence(String source, String output) {
        Matcher sourceMatcher = matcher(source);
        Matcher outputMatcher = matcher(output);
        while (sourceMatcher.find()) {
            if (!outputMatcher.find() || !sourceMatcher.group().equals(outputMatcher.group())) {
                return false;
            }
        }
        return !outputMatcher.find();
    }

    public static boolean hasSameLineSequence(String source, String output) {
        List<String> sourceLines = VisualTextBoundaries.splitLines(source);
        List<String> outputLines = VisualTextBoundaries.splitLines(output);
        if (sourceLines.size() != outputLines.size()) {
            return false;
        }
        for (int index = 0; index < sourceLines.size(); index++) {
            if (!hasSameSequence(sourceLines.get(index), outputLines.get(index))) {
                return false;
            }
        }
        return true;
    }

    public static String strip(String text) {
        return text == null ? "" : matcher(text).replaceAll("");
    }

    static TranslationPlan prepareForTranslation(String text) {
        String source = text == null ? "" : text;
        if (!matcher(source).find()) {
            return new TranslationPlan(source, source, false);
        }
        return new TranslationPlan(source, strip(source), true);
    }

    public static String reanchor(String source, String output) {
        String styled = reanchorStyled(source, output);
        return styled == null ? reanchorPlain(source, output) : styled;
    }

    private static String reanchorPlain(String source, String output) {
        Matcher sourceMatcher = matcher(source);
        List<Anchor> anchors = new ArrayList<Anchor>();
        int sourceCursor = 0;
        int sourcePoints = 0;
        while (sourceMatcher.find()) {
            sourcePoints += codePoints(source, sourceCursor, sourceMatcher.start());
            anchors.add(new Anchor(sourcePoints, sourceMatcher.group()));
            sourceCursor = sourceMatcher.end();
        }
        if (anchors.isEmpty()) {
            return output;
        }
        sourcePoints += codePoints(source, sourceCursor, source.length());
        VisibleText target = VisibleText.parse(strip(output));
        int targetPoints = target.text().codePointCount(0, target.text().length());
        StringBuilder rebuilt = new StringBuilder(output.length());
        int anchorIndex = 0;
        int markerIndex = 0;
        int charOffset = 0;
        for (int point = 0; point <= targetPoints; point++) {
            while (markerIndex < target.markers().size()
                    && target.markers().get(markerIndex).visiblePoint() == point) {
                rebuilt.append(target.markers().get(markerIndex++).text());
            }
            while (anchorIndex < anchors.size()
                    && targetPoint(anchors.get(anchorIndex).sourcePoint(), sourcePoints, targetPoints) == point) {
                rebuilt.append(anchors.get(anchorIndex++).text());
            }
            if (point < targetPoints) {
                int nextOffset = target.text().offsetByCodePoints(charOffset, 1);
                rebuilt.append(target.text(), charOffset, nextOffset);
                charOffset = nextOffset;
            }
        }
        while (markerIndex < target.markers().size()) {
            rebuilt.append(target.markers().get(markerIndex++).text());
        }
        while (anchorIndex < anchors.size()) {
            rebuilt.append(anchors.get(anchorIndex++).text());
        }
        return rebuilt.toString();
    }

    private static String reanchorStyled(String source, String output) {
        if (!StyledTranslationTemplate.contains(source)
                || !StyledTranslationTemplate.contains(output)) {
            return null;
        }
        List<StyleRange> sourceRanges = styleRanges(source);
        List<StyleRange> outputRanges = styleRanges(output);
        if (sourceRanges == null || outputRanges == null
                || sourceRanges.size() != outputRanges.size()) {
            return null;
        }
        Map<Integer, StyleRange> sourceById = rangesById(sourceRanges);
        Map<Integer, StyleRange> outputById = rangesById(outputRanges);
        if (sourceById == null || outputById == null
                || !sourceById.keySet().equals(outputById.keySet())) {
            return null;
        }
        boolean hasStyledTextures = false;
        for (StyleRange range : sourceRanges) {
            if (matcher(range.content(source)).find()) {
                hasStyledTextures = true;
                break;
            }
        }
        if (!hasStyledTextures) {
            return null;
        }

        String sourceWithoutStyledTextures =
                stripStyledTextures(source, sourceRanges);
        String rebuilt = reanchorPlain(sourceWithoutStyledTextures, strip(output));
        List<StyleRange> rebuiltRanges = styleRanges(rebuilt);
        if (rebuiltRanges == null || rebuiltRanges.size() != sourceRanges.size()) {
            return null;
        }

        StringBuilder restored = new StringBuilder(rebuilt.length() + 32);
        int cursor = 0;
        for (StyleRange targetRange : rebuiltRanges) {
            StyleRange sourceRange = sourceById.get(Integer.valueOf(targetRange.id()));
            if (sourceRange == null) {
                return null;
            }
            restored.append(rebuilt, cursor, targetRange.contentStart());
            String sourceContent = sourceRange.content(source);
            String targetContent = targetRange.content(rebuilt);
            restored.append(matcher(sourceContent).find()
                    ? reanchorPlain(sourceContent, strip(targetContent))
                    : targetContent);
            cursor = targetRange.contentEnd();
        }
        restored.append(rebuilt, cursor, rebuilt.length());
        return restored.toString();
    }

    private static String stripStyledTextures(String text, List<StyleRange> ranges) {
        StringBuilder output = new StringBuilder(text.length());
        int cursor = 0;
        for (StyleRange range : ranges) {
            output.append(text, cursor, range.contentStart());
            output.append(strip(range.content(text)));
            cursor = range.contentEnd();
        }
        output.append(text, cursor, text.length());
        return output.toString();
    }

    private static Map<Integer, StyleRange> rangesById(List<StyleRange> ranges) {
        Map<Integer, StyleRange> output = new HashMap<Integer, StyleRange>();
        for (StyleRange range : ranges) {
            if (output.put(Integer.valueOf(range.id()), range) != null) {
                return null;
            }
        }
        return output;
    }

    private static List<StyleRange> styleRanges(String text) {
        Matcher matcher = StyledTranslationTemplate.matcher(text);
        Map<Integer, MutableStyleRange> pending =
                new HashMap<Integer, MutableStyleRange>();
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            if (id < 0 || id >= StyledTranslationTemplate.MAX_SPANS) {
                return null;
            }
            MutableStyleRange range = pending.get(Integer.valueOf(id));
            if (range == null) {
                range = new MutableStyleRange(id);
                pending.put(Integer.valueOf(id), range);
            }
            if ("START".equals(matcher.group(2))) {
                if (range.contentStart >= 0) {
                    return null;
                }
                range.markerStart = matcher.start();
                range.contentStart = matcher.end();
            } else {
                if (range.contentEnd >= 0) {
                    return null;
                }
                range.contentEnd = matcher.start();
                range.markerEnd = matcher.end();
            }
        }
        if (pending.isEmpty()) {
            return null;
        }
        List<StyleRange> ranges = new ArrayList<StyleRange>(pending.size());
        for (MutableStyleRange range : pending.values()) {
            if (range.markerStart < 0 || range.contentStart < 0
                    || range.contentEnd < range.contentStart || range.markerEnd < 0) {
                return null;
            }
            ranges.add(range.freeze());
        }
        Collections.sort(ranges, new Comparator<StyleRange>() {
            @Override
            public int compare(StyleRange first, StyleRange second) {
                return Integer.compare(first.markerStart(), second.markerStart());
            }
        });
        int cursor = 0;
        for (StyleRange range : ranges) {
            if (range.markerStart() < cursor) {
                return null;
            }
            cursor = range.markerEnd();
        }
        return ranges;
    }

    public static String reanchorByLine(String source, String output) {
        List<String> sourceLines = VisualTextBoundaries.splitLines(source);
        List<String> outputLines = VisualTextBoundaries.splitLines(output);
        if (sourceLines.size() == 1 && outputLines.size() == 1) {
            return reanchor(source, output);
        }
        List<String> alignedOutput = alignOutputLines(sourceLines, outputLines);
        if (alignedOutput == null) {
            return null;
        }
        List<String> rebuilt = new ArrayList<String>(sourceLines.size());
        for (int index = 0; index < sourceLines.size(); index++) {
            rebuilt.add(reanchor(sourceLines.get(index), strip(alignedOutput.get(index))));
        }
        return VisualTextLayout.joinWithNewlines(rebuilt);
    }

    private static List<String> alignOutputLines(
            List<String> sourceLines, List<String> outputLines) {
        if (sourceLines.size() == outputLines.size()) {
            return outputLines;
        }
        List<String> visibleOutput = new ArrayList<String>();
        for (String line : outputLines) {
            if (hasVisibleLineText(line)) {
                visibleOutput.add(line);
            }
        }
        int expected = 0;
        for (String line : sourceLines) {
            if (hasVisibleLineText(line)) {
                expected++;
            }
        }
        if (expected != visibleOutput.size()) {
            return null;
        }
        List<String> aligned = new ArrayList<String>(sourceLines.size());
        int outputIndex = 0;
        for (String line : sourceLines) {
            aligned.add(hasVisibleLineText(line)
                    ? visibleOutput.get(outputIndex++) : "");
        }
        return aligned;
    }

    private static boolean hasVisibleLineText(String text) {
        String withoutTextures = strip(text == null ? "" : text);
        return !ZERO_WIDTH_MARKER.matcher(withoutTextures)
                .replaceAll("").trim().isEmpty();
    }

    private static int codePoints(String text, int start, int end) {
        if (text == null || start >= end) {
            return 0;
        }
        String visible = ZERO_WIDTH_MARKER.matcher(
                text.substring(start, end)).replaceAll("");
        return visible.codePointCount(0, visible.length());
    }

    private static int targetPoint(int sourcePoint, int sourceTotal, int targetTotal) {
        if (sourceTotal <= 0 || sourcePoint <= 0) {
            return 0;
        }
        if (sourcePoint >= sourceTotal) {
            return targetTotal;
        }
        return (int) Math.round((double) sourcePoint * (double) targetTotal / (double) sourceTotal);
    }

    private static final class Anchor {
        private final int sourcePoint;
        private final String text;

        private Anchor(int sourcePoint, String text) {
            this.sourcePoint = sourcePoint;
            this.text = text;
        }

        int sourcePoint() { return sourcePoint; }
        String text() { return text; }
    }

    private static final class MutableStyleRange {
        private final int id;
        private int markerStart = -1;
        private int contentStart = -1;
        private int contentEnd = -1;
        private int markerEnd = -1;

        private MutableStyleRange(int id) {
            this.id = id;
        }

        private StyleRange freeze() {
            return new StyleRange(
                    id, markerStart, contentStart, contentEnd, markerEnd);
        }
    }

    private static final class StyleRange {
        private final int id;
        private final int markerStart;
        private final int contentStart;
        private final int contentEnd;
        private final int markerEnd;

        private StyleRange(
                int id, int markerStart, int contentStart, int contentEnd, int markerEnd) {
            this.id = id;
            this.markerStart = markerStart;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
            this.markerEnd = markerEnd;
        }

        int id() { return id; }
        int markerStart() { return markerStart; }
        int contentStart() { return contentStart; }
        int contentEnd() { return contentEnd; }
        int markerEnd() { return markerEnd; }

        String content(String text) {
            return text.substring(contentStart, contentEnd);
        }
    }

    private static final class VisibleText {
        private final String text;
        private final List<ZeroWidthMarker> markers;

        private VisibleText(String text, List<ZeroWidthMarker> markers) {
            this.text = text;
            this.markers = markers;
        }

        static VisibleText parse(String value) {
            Matcher matcher = ZERO_WIDTH_MARKER.matcher(value);
            StringBuilder visible = new StringBuilder(value.length());
            List<ZeroWidthMarker> markers = new ArrayList<ZeroWidthMarker>();
            int cursor = 0;
            int points = 0;
            while (matcher.find()) {
                String part = value.substring(cursor, matcher.start());
                visible.append(part);
                points += part.codePointCount(0, part.length());
                markers.add(new ZeroWidthMarker(points, matcher.group()));
                cursor = matcher.end();
            }
            visible.append(value, cursor, value.length());
            return new VisibleText(visible.toString(), markers);
        }

        String text() { return text; }
        List<ZeroWidthMarker> markers() { return markers; }
    }

    private static final class ZeroWidthMarker {
        private final int visiblePoint;
        private final String text;

        private ZeroWidthMarker(int visiblePoint, String text) {
            this.visiblePoint = visiblePoint;
            this.text = text;
        }

        int visiblePoint() { return visiblePoint; }
        String text() { return text; }
    }

    static final class TranslationPlan {
        private final String source;
        private final String request;
        private final boolean textured;

        private TranslationPlan(String source, String request, boolean textured) {
            this.source = source;
            this.request = request;
            this.textured = textured;
        }

        String request() {
            return request;
        }

        String restore(String translated) {
            if (translated == null || !textured) {
                return translated;
            }
            return reanchorByLine(source, translated);
        }
    }
}

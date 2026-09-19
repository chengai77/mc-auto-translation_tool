package org.universaltranslator.forge;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.universaltranslator.core.InlineTextureCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

final class InlineTextureText {
    private static final int OBJECT_REPLACEMENT = 0xFFFC;

    private InlineTextureText() {
    }

    static String text(OrderedText text) {
        if (text == null) {
            return "";
        }
        final StringBuilder value = new StringBuilder();
        final int[] markerIndex = new int[1];
        text.accept((index, style, codePoint) -> {
            appendSemanticCodePoint(value, style, codePoint, markerIndex);
            return true;
        });
        return value.toString();
    }

    static Style firstStyle(OrderedText text) {
        final Style[] first = new Style[1];
        text.accept((index, style, codePoint) -> {
            if (first[0] == null) {
                first[0] = style == null ? Style.EMPTY : style;
            }
            return true;
        });
        return first[0] == null ? Style.EMPTY : first[0];
    }

    static String semanticText(StringVisitable text, Style baseStyle) {
        StringBuilder value = new StringBuilder();
        for (StyledPart part : styledParts(text, baseStyle)) {
            value.append(part.text());
        }
        return value.toString();
    }

    static List<StyledPart> styledParts(OrderedText source) {
        if (source == null) {
            return Collections.emptyList();
        }
        final List<StyledPart> parts = new ArrayList<StyledPart>();
        final StringBuilder current = new StringBuilder();
        final Style[] currentStyle = new Style[1];
        final int[] markerIndex = new int[1];
        source.accept((index, style, codePoint) -> {
            Style actualStyle = style == null ? Style.EMPTY : style;
            if (currentStyle[0] != null && !currentStyle[0].equals(actualStyle)) {
                parts.add(new StyledPart(current.toString(), currentStyle[0]));
                current.setLength(0);
            }
            currentStyle[0] = actualStyle;
            appendSemanticCodePoint(current, actualStyle, codePoint, markerIndex);
            return true;
        });
        if (current.length() > 0) {
            parts.add(new StyledPart(current.toString(), currentStyle[0]));
        }
        return parts;
    }

    static Text rebuild(OrderedText source, String original, String translated, Style fallbackStyle) {
        return rebuild(snapshot(source, original, fallbackStyle), translated, fallbackStyle);
    }

    static Text restore(StringVisitable source, Text translated, Style fallbackStyle) {
        return restore(source, semanticText(source, fallbackStyle), translated, fallbackStyle);
    }

    static Text restore(
            StringVisitable source, String original, Text translated, Style fallbackStyle) {
        Snapshot sourceSnapshot = snapshot(source, original, fallbackStyle);
        Snapshot targetSnapshot = snapshot(
                translated, translated.getString(), translated.getStyle());
        return rebuild(sourceSnapshot, targetSnapshot, fallbackStyle);
    }

    static List<StyledPart> styledParts(StringVisitable source, Style baseStyle) {
        if (source == null) {
            return Collections.emptyList();
        }
        final String expected = source.getString();
        final int[] markerIndex = new int[1];
        final List<StyledPart> parts = new ArrayList<StyledPart>();
        source.visit((style, part) -> {
            String normalized = normalizePart(part, style, markerIndex);
            if (!normalized.isEmpty()) {
                parts.add(new StyledPart(normalized, style));
            }
            return Optional.empty();
        }, baseStyle == null ? Style.EMPTY : baseStyle);
        StringBuilder joined = new StringBuilder(expected.length());
        for (StyledPart part : parts) {
            joined.append(part.text());
        }
        if (joined.toString().equals(expected) || markerIndex[0] > 0) {
            return parts;
        }
        if (expected.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new StyledPart(expected, baseStyle));
    }

    static StyledPart styledPart(String text, Style style) {
        return new StyledPart(text, style);
    }

    private static Text rebuild(Snapshot source, String translated, Style fallbackStyle) {
        List<Marker> markers = source.markers();
        if (markers.isEmpty()) {
            return null;
        }
        Style textStyle = RenderedTextBridge.translatedStyle(
                plainTextStyle(source.firstNonMarkerStyle(markers, fallbackStyle)));
        return rebuild(source, Snapshot.plain(translated, textStyle), fallbackStyle);
    }

    private static Text rebuild(Snapshot source, Snapshot target, Style fallbackStyle) {
        List<Marker> sourceMarkers = source.markers();
        List<Marker> targetMarkers = target.markers();
        if (sourceMarkers.isEmpty() || sourceMarkers.size() != targetMarkers.size()
                || !InlineTextureCode.hasSameSequence(source.text(), target.text())) {
            return null;
        }
        List<Style> markerStyles = new ArrayList<Style>(sourceMarkers.size());
        for (Marker marker : sourceMarkers) {
            Style style = renderableMarkerStyle(marker.style());
            if (style == null) {
                style = renderableMarkerStyle(fallbackStyle);
            }
            if (style == null) {
                return null;
            }
            markerStyles.add(style);
        }

        MutableText output = Text.empty();
        int cursor = 0;
        for (int index = 0; index < targetMarkers.size(); index++) {
            Marker targetMarker = targetMarkers.get(index);
            appendRange(output, target, cursor, targetMarker.start());
            appendMarker(output, sourceMarkers.get(index).text(), markerStyles.get(index));
            cursor = targetMarker.end();
        }
        appendRange(output, target, cursor, target.text().length());
        return output;
    }

    private static void appendRange(MutableText output, Snapshot target, int start, int end) {
        int cursor = start;
        for (StyleSpan span : target.spans()) {
            if (span.end() <= start || span.start() >= end) {
                continue;
            }
            int partStart = Math.max(start, span.start());
            int partEnd = Math.min(end, span.end());
            if (partStart > cursor) {
                append(output, target.text().substring(cursor, partStart),
                        plainTextStyle(target.fallbackStyle()));
            }
            append(output, target.text().substring(partStart, partEnd),
                    plainTextStyle(span.style()));
            cursor = partEnd;
        }
        if (cursor < end) {
            append(output, target.text().substring(cursor, end),
                    plainTextStyle(target.fallbackStyle()));
        }
    }

    private static void appendMarker(MutableText output, String markerText, Style style) {
        append(output, Character.toString(OBJECT_REPLACEMENT), style);
    }

    private static void append(MutableText output, String value, Style style) {
        if (value != null && !value.isEmpty()) {
            output.append(Text.literal(value).setStyle(style == null ? Style.EMPTY : style));
        }
    }

    private static Style plainTextStyle(Style style) {
        return style == null ? Style.EMPTY : style.withFont(Style.DEFAULT_FONT_ID);
    }

    private static Style renderableMarkerStyle(Style style) {
        if (style == null || Style.DEFAULT_FONT_ID.equals(style.getFont())) {
            return null;
        }
        return style;
    }

    private static Snapshot snapshot(OrderedText text, String fallbackText, Style fallbackStyle) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        final int[] markerIndex = new int[1];
        text.accept((index, style, codePoint) -> {
            int start = value.length();
            appendSemanticCodePoint(value, style, codePoint, markerIndex);
            spans.add(new StyleSpan(start, value.length(), style));
            return true;
        });
        Snapshot snapshot = new Snapshot(value.toString(), spans, fallbackStyle);
        return snapshot.text().equals(fallbackText)
                ? snapshot : Snapshot.plain(fallbackText, fallbackStyle);
    }

    private static Snapshot snapshot(
            StringVisitable text, String fallbackText, Style fallbackStyle) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        for (StyledPart part : styledParts(text, Style.EMPTY)) {
            int start = value.length();
            value.append(part.text());
            spans.add(new StyleSpan(start, value.length(), part.style()));
        }
        Snapshot snapshot = new Snapshot(value.toString(), spans, fallbackStyle);
        return snapshot.text().equals(fallbackText)
                ? snapshot : Snapshot.plain(fallbackText, fallbackStyle);
    }

    private static void appendSemanticCodePoint(
            StringBuilder output, Style style, int codePoint, int[] markerIndex) {
        if (codePoint == OBJECT_REPLACEMENT) {
            String marker = markerText(style, markerIndex[0]);
            if (marker != null) {
                markerIndex[0]++;
                output.append(marker);
                return;
            }
        }
        output.appendCodePoint(codePoint);
    }

    private static String normalizePart(
            String part,
            Style style,
            int[] markerIndex
    ) {
        if (part == null || part.isEmpty() || part.indexOf(OBJECT_REPLACEMENT) < 0
                || renderableMarkerStyle(style) == null) {
            return part == null ? "" : part;
        }
        StringBuilder output = new StringBuilder(part.length());
        for (int offset = 0; offset < part.length();) {
            int codePoint = part.codePointAt(offset);
            if (codePoint == OBJECT_REPLACEMENT) {
                String marker = markerText(style, markerIndex[0]);
                if (marker != null) {
                    markerIndex[0]++;
                    output.append(marker);
                } else {
                    output.appendCodePoint(codePoint);
                }
            } else {
                output.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return output.toString();
    }

    private static String markerText(Style style, int markerIndex) {
        if (style == null || Style.DEFAULT_FONT_ID.equals(style.getFont())) {
            return null;
        }
        Identifier font = style.getFont();
        return "[ut_object/font_" + markerIndex + "@" + font + "]";
    }

    static final class StyledPart {
        private final String text;
        private final Style style;

        private StyledPart(String text, Style style) {
            this.text = text == null ? "" : text;
            this.style = style == null ? Style.EMPTY : style;
        }

        String text() { return text; }
        Style style() { return style; }
    }

    private static final class Snapshot {
        private final String text;
        private final List<StyleSpan> spans;
        private final Style fallbackStyle;

        private Snapshot(String text, List<StyleSpan> spans, Style fallbackStyle) {
            this.text = text == null ? "" : text;
            this.spans = spans;
            this.fallbackStyle = fallbackStyle == null ? Style.EMPTY : fallbackStyle;
        }

        static Snapshot plain(String text, Style style) {
            List<StyleSpan> spans = new ArrayList<StyleSpan>();
            if (text != null && !text.isEmpty()) {
                spans.add(new StyleSpan(0, text.length(), style));
            }
            return new Snapshot(text, spans, style);
        }

        String text() { return text; }
        List<StyleSpan> spans() { return spans; }
        Style fallbackStyle() { return fallbackStyle; }

        List<Marker> markers() {
            List<Marker> markers = new ArrayList<Marker>();
            Matcher matcher = InlineTextureCode.matcher(text);
            while (matcher.find()) {
                markers.add(new Marker(
                        matcher.start(), matcher.end(), matcher.group(), styleAt(matcher.start())));
            }
            return markers;
        }

        Style firstNonMarkerStyle(List<Marker> markers, Style fallback) {
            for (StyleSpan span : spans) {
                for (int index = span.start(); index < span.end(); index++) {
                    if (!insideMarker(index, markers) && !Character.isWhitespace(text.charAt(index))) {
                        return span.style();
                    }
                }
            }
            return fallback == null ? fallbackStyle : fallback;
        }

        private Style styleAt(int index) {
            for (StyleSpan span : spans) {
                if (index >= span.start() && index < span.end()) {
                    return span.style();
                }
            }
            return fallbackStyle;
        }

        private static boolean insideMarker(int index, List<Marker> markers) {
            for (Marker marker : markers) {
                if (index >= marker.start() && index < marker.end()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class StyleSpan {
        private final int start;
        private final int end;
        private final Style style;

        private StyleSpan(int start, int end, Style style) {
            this.start = start;
            this.end = end;
            this.style = style == null ? Style.EMPTY : style;
        }

        int start() { return start; }
        int end() { return end; }
        Style style() { return style; }
    }

    private static final class Marker {
        private final int start;
        private final int end;
        private final String text;
        private final Style style;

        private Marker(int start, int end, String text, Style style) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.style = style == null ? Style.EMPTY : style;
        }

        int start() { return start; }
        int end() { return end; }
        String text() { return text; }
        Style style() { return style; }
    }
}

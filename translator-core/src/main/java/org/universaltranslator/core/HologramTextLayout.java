package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 保持全息行稳定 */
final class HologramTextLayout {
    private static final int MAX_BLOCKS = 64;
    private static final int MAX_TEMPLATE_LENGTH = 8_192;
    private static final String MARKER_SOURCE =
            "\\{UT_HOLOGRAM_BLOCK_(\\d+)_(START|END)}";
    private static final Pattern MARKER = Pattern.compile(MARKER_SOURCE);
    private static final Pattern LEADING_STYLE_ENDS = Pattern.compile(
            "^(?:\\{UT_STYLE_\\d+_END})+");
    private static final Pattern TRAILING_STYLE_STARTS = Pattern.compile(
            "(?:\\{UT_STYLE_\\d+_START})+$");

    private HologramTextLayout() {
    }

    static Plan prepare(String original) {
        if (original == null || original.isEmpty() || original.length() > MAX_TEMPLATE_LENGTH) {
            return null;
        }
        List<Block> blocks = splitBlocks(original);
        if (blocks == null) {
            return null;
        }
        int translatedBlocks = 0;
        StringBuilder request = new StringBuilder(original.length() + blocks.size() * 64);
        List<Block> planned = new ArrayList<Block>(blocks.size());
        for (Block block : blocks) {
            if (block.literal()) {
                planned.add(block);
                continue;
            }
            String requestText = requestText(block.text());
            if (requestText == null) {
                planned.add(Block.literal(block.text()));
                continue;
            }
            if (translatedBlocks >= MAX_BLOCKS) {
                return null;
            }
            if (request.length() > 0) {
                request.append('\n');
            }
            int id = translatedBlocks++;
            request.append(startMarker(id)).append(requestText).append(endMarker(id));
            planned.add(Block.translatable(block.text(), id));
        }
        if (translatedBlocks == 0 || request.length() > MAX_TEMPLATE_LENGTH * 2) {
            return null;
        }
        return new Plan(original, request.toString(), planned, translatedBlocks);
    }

    static boolean containsTemplate(String text) {
        return text != null && MARKER.matcher(text).find();
    }

    static TranslationBatch translationBatch(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = MARKER.matcher(text);
        List<TranslationRange> ranges = new ArrayList<TranslationRange>();
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            int id = Integer.parseInt(matcher.group(1));
            if (!"START".equals(matcher.group(2)) || id != ranges.size()) {
                return null;
            }
            int contentStart = matcher.end();
            if (!matcher.find() || Integer.parseInt(matcher.group(1)) != id
                    || !"END".equals(matcher.group(2)) || matcher.start() <= contentStart) {
                return null;
            }
            ranges.add(new TranslationRange(contentStart, matcher.start()));
            if (ranges.size() > MAX_BLOCKS) {
                return null;
            }
            searchFrom = matcher.end();
        }
        return ranges.isEmpty() ? null : new TranslationBatch(text, ranges);
    }

    private static List<Block> splitBlocks(String original) {
        List<String> lines = normalizedLines(original);
        normalizeStyleLineBoundaries(lines);
        List<Block> blocks = new ArrayList<Block>();
        List<String> pending = new ArrayList<String>();
        int openStyleSpans = 0;
        for (String normalized : lines) {
            String visible = visibleText(normalized);
            boolean canSplit = openStyleSpans == 0;
            boolean hasStyleMarker = StyledTranslationTemplate.contains(normalized);
            if (visible.isEmpty()) {
                if (canSplit && !hasStyleMarker) {
                    flushPending(blocks, pending);
                    blocks.add(Block.literal(""));
                    continue;
                }
                pending.add(normalized);
                openStyleSpans = updateStyleDepth(normalized, openStyleSpans);
                if (openStyleSpans < 0) {
                    return null;
                }
                continue;
            }
            if (canSplit && !hasStyleMarker
                    && VisualTextBoundaries.isSeparatorLine(visible)) {
                flushPending(blocks, pending);
                blocks.add(Block.literal(normalized));
                continue;
            }
            if (canSplit && startsLogicalRow(normalized)) {
                flushPending(blocks, pending);
            }
            pending.add(normalized);
            openStyleSpans = updateStyleDepth(normalized, openStyleSpans);
            if (openStyleSpans < 0) {
                return null;
            }
        }
        if (openStyleSpans != 0) {
            return null;
        }
        flushPending(blocks, pending);
        return blocks;
    }

    private static List<String> normalizedLines(String original) {
        List<String> output = new ArrayList<String>();
        for (String line : VisualTextBoundaries.splitLines(original)) {
            output.add(line == null ? "" : line.replace('\r', ' ').trim());
        }
        return output;
    }

    private static void normalizeStyleLineBoundaries(List<String> lines) {
        for (int index = 1; index < lines.size(); index++) {
            String previous = lines.get(index - 1);
            String current = lines.get(index);

            Matcher leadingEnds = LEADING_STYLE_ENDS.matcher(current);
            if (leadingEnds.find()) {
                previous += leadingEnds.group();
                current = current.substring(leadingEnds.end()).trim();
            }

            Matcher trailingStarts = TRAILING_STYLE_STARTS.matcher(previous);
            if (trailingStarts.find()) {
                current = trailingStarts.group() + current;
                previous = previous.substring(0, trailingStarts.start()).trim();
            }

            lines.set(index - 1, previous);
            lines.set(index, current);
        }
    }

    private static int updateStyleDepth(String line, int depth) {
        Matcher matcher = StyledTranslationTemplate.matcher(line);
        while (matcher.find()) {
            depth += "START".equals(matcher.group(2)) ? 1 : -1;
            if (depth < 0) {
                return -1;
            }
        }
        return depth;
    }

    private static void flushPending(List<Block> blocks, List<String> pending) {
        if (pending.isEmpty()) {
            return;
        }
        String joined = VisualTextLayout.joinVisualLines(pending);
        if (!joined.isEmpty()) {
            blocks.add(Block.translatable(joined, -1));
        }
        pending.clear();
    }

    private static boolean startsLogicalRow(String line) {
        String visible = visibleText(line);
        Matcher texture = InlineTextureCode.matcher(visible);
        return texture.lookingAt() || startsLabelRow(visible);
    }

    private static String visibleText(String text) {
        return TranslationTextStyling.stripLegacyFormatting(
                StyledTranslationTemplate.strip(text)).trim();
    }

    private static boolean startsLabelRow(String line) {
        int colon = line.indexOf(':');
        int fullWidthColon = line.indexOf('\uff1a');
        if (colon < 0 || (fullWidthColon >= 0 && fullWidthColon < colon)) {
            colon = fullWidthColon;
        }
        if (colon <= 0 || colon > 24) {
            return false;
        }
        boolean hasLetter = false;
        for (int index = 0; index < colon; index++) {
            char value = line.charAt(index);
            if (Character.isLetter(value)) {
                hasLetter = true;
                continue;
            }
            if (!Character.isDigit(value) && !Character.isWhitespace(value)
                    && value != '_' && value != '-') {
                return false;
            }
        }
        return hasLetter;
    }

    private static String startMarker(int id) {
        return "{UT_HOLOGRAM_BLOCK_" + id + "_START}";
    }

    private static String endMarker(int id) {
        return "{UT_HOLOGRAM_BLOCK_" + id + "_END}";
    }

    private static String requestText(String source) {
        String withoutTextures = InlineTextureCode.matcher(source).replaceAll(" ");
        String visible = visibleText(withoutTextures);
        for (int offset = 0; offset < visible.length();) {
            int codePoint = visible.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                return withoutTextures;
            }
            offset += Character.charCount(codePoint);
        }
        return null;
    }

    static final class Plan {
        private final String original;
        private final String request;
        private final List<Block> blocks;
        private final int translatedBlockCount;

        private Plan(
                String original, String request, List<Block> blocks, int translatedBlockCount) {
            this.original = original;
            this.request = request;
            this.blocks = Collections.unmodifiableList(new ArrayList<Block>(blocks));
            this.translatedBlockCount = translatedBlockCount;
        }

        String request() {
            return request;
        }

        String restore(String translatedTemplate) {
            Map<Integer, String> translatedBlocks = parseBlocks(
                    translatedTemplate, translatedBlockCount);
            if (translatedBlocks == null) {
                return null;
            }
            List<String> output = new ArrayList<String>(blocks.size());
            for (Block block : blocks) {
                if (block.literal()) {
                    output.add(block.text());
                    continue;
                }
                String translated = compact(translatedBlocks.get(block.id()));
                if (translated.isEmpty()) {
                    return null;
                }
                output.add(InlineTextureCode.reanchor(
                        block.text(), InlineTextureCode.strip(translated)));
            }
            String restored = VisualTextLayout.joinWithNewlines(output);
            return restored.isEmpty() ? original : restored;
        }
    }

    static final class TranslationBatch {
        private final String source;
        private final List<TranslationRange> ranges;

        private TranslationBatch(String source, List<TranslationRange> ranges) {
            this.source = source;
            this.ranges = Collections.unmodifiableList(
                    new ArrayList<TranslationRange>(ranges));
        }

        List<String> contents() {
            List<String> output = new ArrayList<String>(ranges.size());
            for (TranslationRange range : ranges) {
                output.add(source.substring(range.start(), range.end()));
            }
            return output;
        }

        String restore(List<String> translatedContents) {
            if (translatedContents == null || translatedContents.size() != ranges.size()) {
                return null;
            }
            StringBuilder output = new StringBuilder(source.length() + 32);
            int cursor = 0;
            for (int index = 0; index < ranges.size(); index++) {
                TranslationRange range = ranges.get(index);
                String translated = translatedContents.get(index);
                if (translated == null || translated.trim().isEmpty()) {
                    return null;
                }
                output.append(source, cursor, range.start());
                output.append(translated);
                cursor = range.end();
            }
            output.append(source, cursor, source.length());
            return output.toString();
        }
    }

    private static Map<Integer, String> parseBlocks(String text, int expectedCount) {
        if (text == null || expectedCount <= 0) {
            return null;
        }
        List<Range> ranges = new ArrayList<Range>(expectedCount);
        for (int id = 0; id < expectedCount; id++) {
            String start = startMarker(id);
            String end = endMarker(id);
            int markerStart = uniqueIndexOf(text, start);
            int markerEnd = uniqueIndexOf(text, end);
            int contentStart = markerStart + start.length();
            if (markerStart < 0 || markerEnd < contentStart) {
                return null;
            }
            ranges.add(new Range(id, markerStart, contentStart, markerEnd,
                    markerEnd + end.length()));
        }
        Collections.sort(ranges, new Comparator<Range>() {
            @Override
            public int compare(Range first, Range second) {
                return Integer.compare(first.markerStart(), second.markerStart());
            }
        });
        int cursor = 0;
        Map<Integer, String> output = new HashMap<Integer, String>();
        for (Range range : ranges) {
            if (range.markerStart() < cursor) {
                return null;
            }
            String value = text.substring(range.contentStart(), range.contentEnd()).trim();
            if (value.isEmpty() || containsTemplate(value)) {
                return null;
            }
            output.put(range.id(), value);
            cursor = range.markerEnd();
        }
        return output.size() == expectedCount ? output : null;
    }

    private static int uniqueIndexOf(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0 || text.indexOf(marker, index + marker.length()) >= 0) {
            return -1;
        }
        return index;
    }

    private static String compact(String text) {
        if (text == null) {
            return "";
        }
        return VisualTextLayout.joinVisualLines(VisualTextBoundaries.splitLines(text));
    }

    private static final class Block {
        private final String text;
        private final boolean literal;
        private final int id;

        private Block(String text, boolean literal, int id) {
            this.text = text == null ? "" : text;
            this.literal = literal;
            this.id = id;
        }

        static Block literal(String text) {
            return new Block(text, true, -1);
        }

        static Block translatable(String text, int id) {
            return new Block(text, false, id);
        }

        String text() { return text; }
        boolean literal() { return literal; }
        int id() { return id; }
    }

    private static final class Range {
        private final int id;
        private final int markerStart;
        private final int contentStart;
        private final int contentEnd;
        private final int markerEnd;

        private Range(int id, int markerStart, int contentStart, int contentEnd, int markerEnd) {
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
    }

    private static final class TranslationRange {
        private final int start;
        private final int end;

        private TranslationRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int start() { return start; }
        int end() { return end; }
    }
}

package org.universaltranslator.fabric;

import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.VisualTextBoundaries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SignTranslationContext {
    public interface WidthMeasurer {
        int width(String text);
    }

    private static final ThreadLocal<Deque<Batch>> BATCHES =
            new ThreadLocal<Deque<Batch>>() {
                @Override
                protected Deque<Batch> initialValue() {
                    return new ArrayDeque<Batch>();
                }
            };
    private static final Map<Object, Boolean> SUBMITTED_TEXTS =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    private SignTranslationContext() {
    }

    public static void push(List<String> lines, int maxWidth) {
        push(lines, maxWidth, 0, null);
    }

    public static void push(List<String> lines, int maxWidth, WidthMeasurer measurer) {
        push(lines, maxWidth, 0, measurer);
    }

    public static void push(List<String> lines, int maxWidth, int lineHeight, WidthMeasurer measurer) {
        List<String> originals = sanitize(lines);
        List<String> translated = FabricTranslationRuntime.translateLinesForRender(originals, TextKind.SIGN);
        BATCHES.get().push(new Batch(originals, translated, maxWidth, lineHeight, measurer));
    }

    public static void pop() {
        Deque<Batch> batches = BATCHES.get();
        if (!batches.isEmpty()) {
            batches.pop();
        }
        if (batches.isEmpty()) {
            BATCHES.remove();
        }
    }

    public static String translateLine(String original) {
        return translateLine(original, null);
    }

    public static String translateLine(String original, WidthMeasurer measurer) {
        if (original == null) {
            return null;
        }
        Deque<Batch> batches = BATCHES.get();
        return batches.isEmpty() ? null : batches.peek().translate(original, measurer);
    }

    public static String translateNextLine(String original, WidthMeasurer measurer) {
        String translated = translateLine(original, measurer);
        return translated == null ? original : translated;
    }

    public static <T> T markSubmittedText(T text) {
        if (text != null) {
            SUBMITTED_TEXTS.put(text, Boolean.TRUE);
        }
        return text;
    }

    public static boolean isSubmittedText(Object text) {
        return text != null && SUBMITTED_TEXTS.containsKey(text);
    }

    public static float verticalOffset() {
        Deque<Batch> batches = BATCHES.get();
        return batches.isEmpty() ? 0.0F : batches.peek().verticalOffset();
    }

    public static float horizontalOffset(String original, String translated, WidthMeasurer measurer) {
        if (original == null || translated == null || original.equals(translated)
                || measurer == null) {
            return 0.0F;
        }
        return (measurer.width(original) - measurer.width(translated)) / 2.0F;
    }

    private static List<String> sanitize(List<String> lines) {
        List<String> values = new ArrayList<String>(lines == null ? 0 : lines.size());
        if (lines != null) {
            for (String line : lines) {
                values.add(line == null ? "" : line);
            }
        }
        return values;
    }

    private static final class Batch {
        private final List<String> originals;
        private final List<String> rawTranslated;
        private final boolean translationReady;
        private final int maxWidth;
        private final int lineHeight;
        private final WidthMeasurer defaultMeasurer;
        private List<String> displayLines;
        private int cursor;

        private Batch(
                List<String> originals,
                List<String> translated,
                int maxWidth,
                int lineHeight,
                WidthMeasurer defaultMeasurer
        ) {
            this.originals = originals;
            this.rawTranslated = translated == null ? originals : translated;
            this.translationReady = translated != null && translated != originals;
            this.maxWidth = maxWidth;
            this.lineHeight = lineHeight;
            this.defaultMeasurer = defaultMeasurer;
        }

        private String translate(String original, WidthMeasurer measurer) {
            int size = originals.size();
            if (size == 0) {
                return null;
            }
            for (int offset = 0; offset < size; offset++) {
                int index = (cursor + offset) % size;
                if (original.equals(originals.get(index))) {
                    cursor = (index + 1) % size;
                    List<String> lines = displayLines(measurer);
                    return index < lines.size() ? lines.get(index) : original;
                }
            }
            return null;
        }

        private float verticalOffset() {
            if (!translationReady || lineHeight <= 0) {
                return 0.0F;
            }
            List<String> lines = displayLines(defaultMeasurer);
            int visibleLines = 0;
            for (String line : lines) {
                if (line != null && !line.trim().isEmpty()) {
                    visibleLines++;
                }
            }
            return visibleLines <= 0 ? 0.0F
                    : (originals.size() - visibleLines) * lineHeight / 2.0F;
        }

        private List<String> displayLines(WidthMeasurer measurer) {
            if (!translationReady) {
                return originals;
            }
            if (displayLines != null) {
                return displayLines;
            }
            WidthMeasurer effectiveMeasurer = measurer == null ? defaultMeasurer : measurer;
            List<String> lines = wrapForSign(
                    rawTranslated, originals.size(), maxWidth, effectiveMeasurer);
            if (effectiveMeasurer != null) {
                displayLines = lines;
            }
            return lines;
        }
    }

    private static List<String> wrapForSign(
            List<String> translated,
            int lineCount,
            int maxWidth,
            WidthMeasurer measurer
    ) {
        if (measurer == null || maxWidth <= 0) {
            List<String> result = new ArrayList<String>(lineCount);
            for (int index = 0; index < lineCount; index++) {
                result.add(index < translated.size() ? translated.get(index) : "");
            }
            return result;
        }
        if (VisualTextBoundaries.hasSeparatorLine(translated)) {
            return wrapSectionsForSign(translated, lineCount, maxWidth, measurer);
        }
        List<String> result = new ArrayList<String>(lineCount);
        List<String> wrapped = wrapSignText(
                joinTranslated(translated), lineCount, maxWidth, measurer);
        for (int index = 0; index < lineCount; index++) {
            result.add(index < wrapped.size() ? wrapped.get(index) : "");
        }
        return result;
    }

    private static List<String> wrapSectionsForSign(
            List<String> translated,
            int lineCount,
            int maxWidth,
            WidthMeasurer measurer
    ) {
        List<String> result = new ArrayList<String>(lineCount);
        int index = 0;
        while (index < lineCount) {
            String line = index < translated.size() ? translated.get(index) : "";
            if (VisualTextBoundaries.isSeparatorLine(line)) {
                result.add(line.trim());
                index++;
                continue;
            }
            List<String> group = new ArrayList<String>();
            while (index < lineCount) {
                line = index < translated.size() ? translated.get(index) : "";
                if (VisualTextBoundaries.isSeparatorLine(line)) {
                    break;
                }
                group.add(line);
                index++;
            }
            List<String> wrapped = wrapSignText(
                    joinTranslated(group), group.size(), maxWidth, measurer);
            for (int slot = 0; slot < group.size(); slot++) {
                result.add(slot < wrapped.size() ? wrapped.get(slot) : "");
            }
        }
        return result;
    }

    private static String joinTranslated(List<String> translated) {
        StringBuilder joined = new StringBuilder();
        for (String line : translated) {
            if (line == null) {
                continue;
            }
            String normalized = line.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(normalized);
        }
        return joined.toString();
    }

    private static List<String> wrapSignText(String text, int maxLines, int maxWidth, WidthMeasurer measurer) {
        List<String> segments = splitBracketSegments(text);
        if (segments.size() <= 1) {
            return wrapText(text, maxLines, maxWidth, measurer);
        }
        List<String> lines = new ArrayList<String>(maxLines);
        for (int index = 0; index < segments.size() && lines.size() < maxLines; index++) {
            String value = segments.get(index).trim();
            if (value.isEmpty()) {
                continue;
            }
            int slotsLeft = maxLines - lines.size();
            int remaining = remainingSegments(segments, index + 1);
            if (slotsLeft == 1 && remaining > 0) {
                value = joinSegments(segments, index);
                remaining = 0;
            }
            int slotsForThis = Math.max(1, slotsLeft - remaining);
            lines.addAll(wrapText(value, slotsForThis, maxWidth, measurer));
        }
        return lines;
    }

    private static List<String> splitBracketSegments(String text) {
        List<String> segments = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return segments;
        }
        int cursor = 0;
        int index = 0;
        while (index < text.length()) {
            char close = matchingClose(text.charAt(index));
            if (close == 0) {
                index++;
                continue;
            }
            addSegment(segments, text.substring(cursor, index));
            int end = text.indexOf(close, index + 1);
            if (end < 0) {
                end = text.length() - 1;
            }
            addSegment(segments, text.substring(index, end + 1));
            cursor = end + 1;
            index = cursor;
        }
        addSegment(segments, text.substring(cursor));
        return segments;
    }

    private static void addSegment(List<String> segments, String value) {
        if (value != null && !value.trim().isEmpty()) {
            segments.add(value.trim());
        }
    }

    private static int remainingSegments(List<String> segments, int start) {
        int count = 0;
        for (int index = start; index < segments.size(); index++) {
            if (!segments.get(index).trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static String joinSegments(List<String> segments, int start) {
        StringBuilder joined = new StringBuilder();
        for (int index = start; index < segments.size(); index++) {
            String value = segments.get(index).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(value);
        }
        return joined.toString();
    }

    private static char matchingClose(char open) {
        switch (open) {
            case '\u300a': return '\u300b';
            case '<': return '>';
            case '\u300c': return '\u300d';
            case '\u3010': return '\u3011';
            case '[': return ']';
            case '(': return ')';
            case '\uff08': return '\uff09';
            default: return 0;
        }
    }

    private static List<String> wrapText(String text, int maxLines, int maxWidth, WidthMeasurer measurer) {
        List<String> lines = new ArrayList<String>(maxLines);
        String remaining = text.trim();
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            int limit = fitLength(remaining, maxWidth, measurer);
            if (limit >= remaining.length() || lines.size() == maxLines - 1) {
                lines.add(remaining);
                break;
            }
            int breakAt = findBreak(remaining, limit);
            lines.add(remaining.substring(0, breakAt).trim());
            remaining = remaining.substring(breakAt).trim();
        }
        return lines;
    }

    private static int fitLength(String text, int maxWidth, WidthMeasurer measurer) {
        int low = 1;
        int high = text.length();
        int best = 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (measurer.width(text.substring(0, middle)) <= maxWidth) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private static int findBreak(String text, int limit) {
        for (int index = limit; index > 0; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }
        return Math.max(1, limit);
    }
}

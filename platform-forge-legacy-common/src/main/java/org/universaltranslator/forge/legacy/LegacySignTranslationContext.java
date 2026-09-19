package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.VisualTextBoundaries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 旧版告示牌排版 */
public final class LegacySignTranslationContext {
    private static final int MAX_WIDTH = 90;
    private static final ThreadLocal<Deque<Batch>> BATCHES =
            new ThreadLocal<Deque<Batch>>() {
                @Override
                protected Deque<Batch> initialValue() {
                    return new ArrayDeque<Batch>();
                }
            };
    private static final ThreadLocal<Integer> MEASURE_DEPTH =
            new ThreadLocal<Integer>();

    private LegacySignTranslationContext() {
    }

    public static void push(Object sign) {
        List<String> originals = sanitize(LegacyVersionAccess.signLines(sign));
        List<String> translated = LegacyTranslationRuntime.translateLines(
                originals, TextKind.SIGN);
        BATCHES.get().push(new Batch(
                originals, translated, LegacyVersionAccess.fontRenderer()));
        LegacyRenderContext.pushSign();
    }

    public static void pop() {
        Deque<Batch> batches = BATCHES.get();
        if (!batches.isEmpty()) {
            batches.pop();
        }
        if (batches.isEmpty()) {
            BATCHES.remove();
        }
        LegacyRenderContext.pop();
    }

    static boolean isMeasuring() {
        Integer depth = MEASURE_DEPTH.get();
        return depth != null && depth > 0;
    }

    static String translateLine(String original) {
        if (original == null || isMeasuring()) {
            return null;
        }
        Deque<Batch> batches = BATCHES.get();
        return batches.isEmpty() ? null : batches.peek().translate(original);
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

    private static List<String> layout(
            List<String> originals,
            List<String> translated,
            FontRenderer font
    ) {
        int lineCount = originals.size();
        List<String> raw = new ArrayList<String>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            raw.add(index < translated.size() && translated.get(index) != null
                    ? translated.get(index) : "");
        }
        if (VisualTextBoundaries.hasGraphicLayout(originals)) {
            return raw;
        }
        if (!VisualTextBoundaries.hasSeparatorLine(originals)) {
            return raw;
        }

        List<String> result = new ArrayList<String>(lineCount);
        int index = 0;
        while (index < lineCount) {
            String original = originals.get(index);
            if (VisualTextBoundaries.isSeparatorLine(original)) {
                result.add(original.trim());
                index++;
                continue;
            }
            int start = index;
            List<String> group = new ArrayList<String>();
            while (index < lineCount
                    && !VisualTextBoundaries.isSeparatorLine(originals.get(index))) {
                group.add(VisualTextBoundaries.stripAttachedSeparatorRuns(raw.get(index)));
                index++;
            }
            List<String> wrapped = wrap(
                    join(group), index - start, font);
            for (int slot = 0; slot < index - start; slot++) {
                result.add(slot < wrapped.size() ? wrapped.get(slot) : "");
            }
        }
        return result;
    }

    private static List<String> wrap(String text, int maximumLines, FontRenderer font) {
        List<String> lines = new ArrayList<String>(maximumLines);
        String remaining = normalize(text);
        while (!remaining.isEmpty() && lines.size() < maximumLines) {
            if (lines.size() == maximumLines - 1 || width(font, remaining) <= MAX_WIDTH) {
                lines.add(remaining);
                break;
            }
            int split = fittingEnd(remaining, font);
            int whitespace = lastWhitespace(remaining, split);
            if (whitespace > 0) {
                split = whitespace;
            }
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        return lines;
    }

    private static int fittingEnd(String text, FontRenderer font) {
        int best = 0;
        int end = 0;
        while (end < text.length()) {
            end = nextCharacterEnd(text, end);
            if (width(font, text.substring(0, end)) > MAX_WIDTH) {
                break;
            }
            best = end;
        }
        return best > 0 ? best : nextCharacterEnd(text, 0);
    }

    private static int nextCharacterEnd(String text, int start) {
        int end = Math.min(text.length(), start + 1);
        if (start < text.length() && text.charAt(start) == '\u00a7'
                && end < text.length()) {
            end++;
        } else if (start < text.length() && Character.isHighSurrogate(text.charAt(start))
                && end < text.length()
                && Character.isLowSurrogate(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int lastWhitespace(String text, int end) {
        for (int index = Math.min(end, text.length()) - 1; index > 0; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int width(FontRenderer font, String text) {
        Integer depth = MEASURE_DEPTH.get();
        MEASURE_DEPTH.set(depth == null ? 1 : depth + 1);
        try {
            return font.getStringWidth(text);
        } finally {
            if (depth == null) {
                MEASURE_DEPTH.remove();
            } else {
                MEASURE_DEPTH.set(depth);
            }
        }
    }

    private static String join(List<String> lines) {
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            String value = normalize(line);
            if (value.isEmpty()) {
                continue;
            }
            if (output.length() > 0) {
                output.append(' ');
            }
            output.append(value);
        }
        return output.toString();
    }

    private static String normalize(String text) {
        return text == null ? ""
                : text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class Batch {
        private final List<String> originals;
        private final List<String> displayed;
        private int cursor;

        private Batch(
                List<String> originals,
                List<String> translated,
                FontRenderer font
        ) {
            this.originals = originals;
            List<String> raw = translated == null ? originals : translated;
            this.displayed = originals.equals(raw)
                    ? originals : layout(originals, raw, font);
        }

        private String translate(String original) {
            int size = originals.size();
            for (int offset = 0; offset < size; offset++) {
                int index = (cursor + offset) % size;
                if (original.equals(originals.get(index))) {
                    cursor = (index + 1) % size;
                    return index < displayed.size() ? displayed.get(index) : original;
                }
            }
            return null;
        }
    }
}

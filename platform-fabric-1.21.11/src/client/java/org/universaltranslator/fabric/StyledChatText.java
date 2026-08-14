package org.universaltranslator.fabric;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationTextColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 保留聊天局部颜色 */
final class StyledChatText {
    private static final int MAX_COLOR_RUNS = 8;
    private static final int MAX_RUN_TEXT_LENGTH = 80;

    private StyledChatText() {
    }

    static StringVisitable rebuild(StringVisitable source, String translated, Style fallbackStyle) {
        if (source == null || translated == null
                || FabricTranslationRuntime.translatedTextColor() != TranslationTextColor.ORIGINAL) {
            return null;
        }
        StyledText styled = styledText(source, fallbackStyle);
        List<ColorRun> sourceRuns = styled.colorRuns();
        if (sourceRuns.isEmpty()) {
            return null;
        }
        if (styled.allVisibleTextUses(sourceRuns.get(0).style())) {
            return Text.literal(translated).setStyle(sourceRuns.get(0).style());
        }
        List<TargetRun> targetRuns = targetRuns(styled.text(), translated, sourceRuns);
        if (targetRuns.isEmpty()) {
            return null;
        }
        return buildText(translated, styled.plainStyle(fallbackStyle), targetRuns);
    }

    private static List<TargetRun> targetRuns(
            String original,
            String translated,
            List<ColorRun> sourceRuns
    ) {
        List<TargetRun> targets = new ArrayList<TargetRun>();
        int limit = Math.min(sourceRuns.size(), MAX_COLOR_RUNS);
        for (int index = 0; index < limit; index++) {
            ColorRun run = sourceRuns.get(index);
            String sourceText = run.text().trim();
            if (sourceText.isEmpty() || sourceText.length() > MAX_RUN_TEXT_LENGTH) {
                continue;
            }
            double preferred = original.isEmpty() ? 0.0D : (double) run.start() / (double) original.length();
            String fragment = translatedFragment(sourceText);
            if (!fragment.equals(sourceText)
                    && addTarget(translated, fragment, run.style(), preferred, targets)) {
                continue;
            }
            if (addTarget(translated, sourceText, run.style(), preferred, targets)) {
                continue;
            }
            if (!run.isInteractive()) {
                addPositionalTarget(original, translated, run, targets);
            }
        }
        Collections.sort(targets, new Comparator<TargetRun>() {
            @Override
            public int compare(TargetRun first, TargetRun second) {
                return Integer.compare(first.start(), second.start());
            }
        });
        return targets;
    }

    private static boolean addTarget(
            String translated,
            String needle,
            Style style,
            double preferred,
            List<TargetRun> targets
    ) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        int start = bestIndex(translated, needle, preferred, targets, false);
        if (start < 0) {
            start = bestIndex(translated, needle, preferred, targets, true);
        }
        if (start < 0) {
            return false;
        }
        targets.add(new TargetRun(start, start + needle.length(), style));
        return true;
    }

    private static boolean addPositionalTarget(
            String original,
            String translated,
            ColorRun run,
            List<TargetRun> targets
    ) {
        if (original == null || original.isEmpty() || translated == null || translated.isEmpty()) {
            return false;
        }
        int start;
        int end;
        if (containsWhitespace(translated)) {
            int[] token = nearestToken(translated, midpoint(run.start(), run.end(), original.length()), targets);
            start = token[0];
            end = token[1];
        } else {
            int chars = translated.codePointCount(0, translated.length());
            int wanted = Math.max(1, (int) Math.round(
                    chars * (double) (run.end() - run.start()) / (double) original.length()));
            int center = (int) Math.round(chars * midpoint(run.start(), run.end(), original.length()));
            int first = Math.max(0, Math.min(chars - wanted, center - Math.max(1, wanted / 2)));
            start = translated.offsetByCodePoints(0, first);
            end = translated.offsetByCodePoints(start, Math.min(wanted, chars - first));
        }
        if (start < 0 || end <= start || overlaps(start, end, targets)) {
            return false;
        }
        targets.add(new TargetRun(start, end, run.style()));
        return true;
    }

    private static double midpoint(int start, int end, int length) {
        return length <= 0 ? 0.0D : ((double) start + (double) end) / 2.0D / (double) length;
    }

    private static int[] nearestToken(String translated, double preferred, List<TargetRun> targets) {
        int bestStart = -1;
        int bestEnd = -1;
        double bestDistance = Double.MAX_VALUE;
        int index = 0;
        while (index < translated.length()) {
            while (index < translated.length() && Character.isWhitespace(translated.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < translated.length() && !Character.isWhitespace(translated.charAt(index))) {
                index++;
            }
            int end = index;
            if (end > start && !overlaps(start, end, targets)) {
                double position = (double) (start + end) / 2.0D / (double) translated.length();
                double distance = Math.abs(position - preferred);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestStart = start;
                    bestEnd = end;
                }
            }
        }
        return new int[] { bestStart, bestEnd };
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static String translatedFragment(String sourceText) {
        String translated = FabricTranslationRuntime.translateStyleFragmentForRender(sourceText, TextKind.CHAT);
        return translated == null || translated.trim().isEmpty() ? sourceText : translated.trim();
    }

    private static int bestIndex(
            String translated,
            String needle,
            double preferred,
            List<TargetRun> targets,
            boolean ignoreCase
    ) {
        String haystack = ignoreCase ? translated.toLowerCase(java.util.Locale.ROOT) : translated;
        String search = ignoreCase ? needle.toLowerCase(java.util.Locale.ROOT) : needle;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        int index = haystack.indexOf(search);
        while (index >= 0) {
            int end = index + needle.length();
            if (!overlaps(index, end, targets)) {
                double position = translated.isEmpty() ? 0.0D : (double) index / (double) translated.length();
                double distance = Math.abs(position - preferred);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = index;
                }
            }
            index = haystack.indexOf(search, index + 1);
        }
        return best;
    }

    private static boolean overlaps(int start, int end, List<TargetRun> targets) {
        for (TargetRun target : targets) {
            if (start < target.end() && end > target.start()) {
                return true;
            }
        }
        return false;
    }

    private static MutableText buildText(String translated, Style plainStyle, List<TargetRun> targets) {
        MutableText output = Text.empty();
        int cursor = 0;
        for (TargetRun target : targets) {
            append(output, translated.substring(cursor, target.start()), plainStyle);
            append(output, translated.substring(target.start(), target.end()), target.style());
            cursor = target.end();
        }
        append(output, translated.substring(cursor), plainStyle);
        return output;
    }

    private static void append(MutableText output, String value, Style style) {
        if (value != null && !value.isEmpty()) {
            output.append(Text.literal(value).setStyle(style == null ? Style.EMPTY : style));
        }
    }

    private static StyledText styledText(StringVisitable source, Style fallbackStyle) {
        if (source instanceof Text) {
            return styledText(((Text) source).asOrderedText(), fallbackStyle);
        }
        String text = source.getString();
        List<StyleSpan> spans = new ArrayList<StyleSpan>();
        if (text != null && !text.isEmpty()) {
            spans.add(new StyleSpan(0, text.length(), fallbackStyle));
        }
        return new StyledText(text, spans, fallbackStyle);
    }

    private static StyledText styledText(OrderedText text, Style fallbackStyle) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        text.accept((index, style, codePoint) -> {
            int start = value.length();
            value.appendCodePoint(codePoint);
            spans.add(new StyleSpan(start, value.length(), style));
            return true;
        });
        return new StyledText(value.toString(), spans, fallbackStyle);
    }

    private static final class StyledText {
        private final String text;
        private final List<StyleSpan> spans;
        private final Style fallbackStyle;

        private StyledText(String text, List<StyleSpan> spans, Style fallbackStyle) {
            this.text = text == null ? "" : text;
            this.spans = spans;
            this.fallbackStyle = fallbackStyle == null ? Style.EMPTY : fallbackStyle;
        }

        String text() {
            return text;
        }

        List<ColorRun> colorRuns() {
            List<ColorRun> runs = new ArrayList<ColorRun>();
            ColorRun current = null;
            for (StyleSpan span : spans) {
                if (!isDecorated(span.style()) || text.substring(span.start(), span.end()).trim().isEmpty()) {
                    if (current != null) {
                        runs.add(current);
                        current = null;
                    }
                    continue;
                }
                if (current != null && current.end() == span.start() && current.style().equals(span.style())) {
                    current = current.extend(span.end(), text.substring(span.start(), span.end()));
                } else {
                    if (current != null) {
                        runs.add(current);
                    }
                    current = new ColorRun(span.start(), span.end(), span.style(),
                            text.substring(span.start(), span.end()));
                }
            }
            if (current != null) {
                runs.add(current);
            }
            return runs;
        }

        boolean allVisibleTextUses(Style style) {
            boolean sawText = false;
            for (StyleSpan span : spans) {
                String value = text.substring(span.start(), span.end());
                if (value.trim().isEmpty()) {
                    continue;
                }
                sawText = true;
                if (!span.style().equals(style)) {
                    return false;
                }
            }
            return sawText;
        }

        Style plainStyle(Style fallback) {
            for (StyleSpan span : spans) {
                if (!isDecorated(span.style())
                        && !text.substring(span.start(), span.end()).trim().isEmpty()) {
                    return span.style();
                }
            }
            Style base = fallback == null ? fallbackStyle : fallback;
            return isDecorated(base) ? Style.EMPTY : base;
        }
    }

    private static boolean isDecorated(Style style) {
        return style != null && !style.isEmpty()
                && (style.getColor() != null
                || style.getClickEvent() != null
                || style.getHoverEvent() != null
                || style.getInsertion() != null);
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

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        Style style() {
            return style;
        }
    }

    private static final class ColorRun {
        private final int start;
        private final int end;
        private final Style style;
        private final String text;

        private ColorRun(int start, int end, Style style, String text) {
            this.start = start;
            this.end = end;
            this.style = style == null ? Style.EMPTY : style;
            this.text = text == null ? "" : text;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        Style style() {
            return style;
        }

        String text() {
            return text;
        }

        ColorRun extend(int newEnd, String addition) {
            return new ColorRun(start, newEnd, style, text + (addition == null ? "" : addition));
        }

        boolean isInteractive() {
            return style.getClickEvent() != null
                    || style.getHoverEvent() != null
                    || style.getInsertion() != null;
        }
    }

    private static final class TargetRun {
        private final int start;
        private final int end;
        private final Style style;

        private TargetRun(int start, int end, Style style) {
            this.start = start;
            this.end = end;
            this.style = style == null ? Style.EMPTY : style;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        Style style() {
            return style;
        }
    }
}

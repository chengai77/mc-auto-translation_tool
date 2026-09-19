package org.universaltranslator.fabric;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationTextColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BookTextStyler {
    private static final int MAX_STYLED_RUNS = 32;
    private static final int MAX_RUN_TEXT_LENGTH = 80;
    private static final Pattern BRACKET_TOKEN = Pattern.compile("\\[[^\\]]{1,24}\\]");

    private BookTextStyler() {
    }

    static Text rebuild(
            Text source, String translated, Style fallbackStyle, TextKind kind) {
        if (source == null || translated == null || translated.isEmpty()) {
            return null;
        }
        StyledText styled = styledText(source, fallbackStyle);
        List<StyledRun> sourceRuns = styled.decoratedRuns();
        if (sourceRuns.isEmpty()) {
            return null;
        }
        if (styled.allVisibleTextUses(sourceRuns.get(0).style())) {
            return Text.literal(translated).setStyle(displayStyle(sourceRuns.get(0).style()));
        }
        Style plainStyle = displayStyle(styled.plainStyle(fallbackStyle));
        List<TargetRun> targets = targetRuns(styled.text(), translated, sourceRuns, kind);
        if (targets.isEmpty()) {
            return Text.literal(translated).setStyle(plainStyle);
        }
        return buildText(translated, plainStyle, targets);
    }

    private static List<TargetRun> targetRuns(
            String original,
            String translated,
            List<StyledRun> sourceRuns,
            TextKind kind
    ) {
        List<TargetRun> targets = new ArrayList<TargetRun>();
        int limit = Math.min(sourceRuns.size(), MAX_STYLED_RUNS);
        for (int index = 0; index < limit; index++) {
            StyledRun run = sourceRuns.get(index);
            String sourceText = run.text().trim();
            if (sourceText.isEmpty() || sourceText.length() > MAX_RUN_TEXT_LENGTH) {
                continue;
            }
            double preferred = original.isEmpty() ? 0.0D : (double) run.start() / (double) original.length();
            int line = lineIndex(original, run.start());
            boolean interactive = run.isInteractive();
            boolean globalFallback = !interactive;
            boolean positionalFallback = globalFallback || interactiveRunsOnLine(original, sourceRuns, line) == 1;
            String fragment = kind == TextKind.BOOK ? translatedFragment(sourceText) : sourceText;
            if (!fragment.equals(sourceText)
                    && addCandidateTarget(translated, fragment, run.style(), preferred, targets, line, globalFallback)) {
                continue;
            }
            if (addCandidateTarget(translated, sourceText, run.style(), preferred, targets, line, globalFallback)) {
                continue;
            }
            if (positionalFallback && addPositionalTarget(original, translated, run, targets, line, globalFallback)) {
                continue;
            }
            if (interactive && positionalFallback) {
                addBracketTarget(translated, run.style(), preferred, targets, line);
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

    private static boolean addCandidateTarget(
            String translated,
            String needle,
            Style style,
            double preferred,
            List<TargetRun> targets,
            int line,
            boolean globalFallback
    ) {
        if (needle == null || needle.trim().isEmpty()) {
            return false;
        }
        String cleaned = needle.trim();
        int start = lineIndexOf(translated, cleaned, line, targets, false);
        if (start < 0) {
            start = lineIndexOf(translated, cleaned, line, targets, true);
        }
        if (start < 0 && globalFallback) {
            start = bestIndex(translated, cleaned, preferred, targets, false);
        }
        if (start < 0 && globalFallback) {
            start = bestIndex(translated, cleaned, preferred, targets, true);
        }
        if (start < 0) {
            return false;
        }
        targets.add(new TargetRun(start, start + cleaned.length(), style));
        return true;
    }

    private static boolean addPositionalTarget(
            String original,
            String translated,
            StyledRun run,
            List<TargetRun> targets,
            int line,
            boolean globalFallback
    ) {
        if (original == null || original.isEmpty() || translated == null || translated.isEmpty()) {
            return false;
        }
        int start;
        int end;
        if (containsWhitespace(translated)) {
            int[] range = lineRange(translated, line);
            int[] token = nearestToken(translated, midpoint(run.start(), run.end(), original.length()), targets, range);
            if (token[0] < 0 && globalFallback) {
                token = nearestToken(translated, midpoint(run.start(), run.end(), original.length()), targets, null);
            }
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

    private static int[] nearestToken(
            String translated, double preferred, List<TargetRun> targets, int[] range) {
        int bestStart = -1;
        int bestEnd = -1;
        double bestDistance = Double.MAX_VALUE;
        int index = range == null ? 0 : range[0];
        int limit = range == null ? translated.length() : range[1];
        while (index < limit) {
            while (index < limit && Character.isWhitespace(translated.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < limit && !Character.isWhitespace(translated.charAt(index))) {
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

    private static int lineIndex(String text, int offset) {
        int line = 0;
        int limit = Math.min(offset, text == null ? 0 : text.length());
        for (int index = 0; index < limit; index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int interactiveRunsOnLine(String original, List<StyledRun> runs, int line) {
        int count = 0;
        for (StyledRun run : runs) {
            if (run.isInteractive() && lineIndex(original, run.start()) == line) {
                count++;
            }
        }
        return count;
    }

    private static int[] lineRange(String text, int targetLine) {
        if (text == null || targetLine < 0) {
            return null;
        }
        int line = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                if (line == targetLine) {
                    return new int[] { start, index };
                }
                line++;
                start = index + 1;
            }
        }
        return line == targetLine ? new int[] { start, text.length() } : null;
    }

    private static int lineIndexOf(
            String translated, String needle, int line, List<TargetRun> targets, boolean ignoreCase) {
        int[] range = lineRange(translated, line);
        if (range == null) {
            return -1;
        }
        String slice = translated.substring(range[0], range[1]);
        String haystack = ignoreCase ? slice.toLowerCase(Locale.ROOT) : slice;
        String search = ignoreCase ? needle.toLowerCase(Locale.ROOT) : needle;
        int index = haystack.indexOf(search);
        while (index >= 0) {
            int start = range[0] + index;
            int end = start + needle.length();
            if (!overlaps(start, end, targets)) {
                return start;
            }
            index = haystack.indexOf(search, index + 1);
        }
        return -1;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean addBracketTarget(
            String translated,
            Style style,
            double preferred,
            List<TargetRun> targets,
            int line
    ) {
        Matcher matcher = BRACKET_TOKEN.matcher(translated);
        int[] range = lineRange(translated, line);
        if (range != null) {
            matcher.region(range[0], range[1]);
        }
        int bestStart = -1;
        int bestEnd = -1;
        double bestDistance = Double.MAX_VALUE;
        while (matcher.find()) {
            if (overlaps(matcher.start(), matcher.end(), targets)) {
                continue;
            }
            double position = translated.isEmpty() ? 0.0D : (double) matcher.start() / (double) translated.length();
            double distance = Math.abs(position - preferred);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestStart = matcher.start();
                bestEnd = matcher.end();
            }
        }
        if (bestStart < 0) {
            return false;
        }
        targets.add(new TargetRun(bestStart, bestEnd, style));
        return true;
    }

    private static String translatedFragment(String sourceText) {
        String translated = FabricTranslationRuntime.translateStyleFragmentForRender(sourceText, TextKind.BOOK);
        return translated == null || translated.trim().isEmpty() ? sourceText : translated.trim();
    }

    private static int bestIndex(
            String translated,
            String needle,
            double preferred,
            List<TargetRun> targets,
            boolean ignoreCase
    ) {
        String haystack = ignoreCase ? translated.toLowerCase(Locale.ROOT) : translated;
        String search = ignoreCase ? needle.toLowerCase(Locale.ROOT) : needle;
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

    private static StyledText styledText(Text source, Style fallbackStyle) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        for (InlineTextureText.StyledPart part :
                InlineTextureText.styledParts(source, fallbackStyle)) {
            int start = value.length();
            value.append(part.text());
            spans.add(new StyleSpan(start, value.length(), part.style()));
        }
        return new StyledText(value.toString(), spans, fallbackStyle);
    }

    private static Style displayStyle(Style original) {
        // 翻译文本使用默认字体
        Style style = (original == null ? Style.EMPTY : original)
                .withFont(StyleSpriteSource.DEFAULT);
        TranslationTextColor color = FabricTranslationRuntime.translatedTextColor();
        if (style.getColor() != null || color == null || !color.changesColor()) {
            return style;
        }
        switch (color) {
            case GREEN: return style.withColor(Formatting.GREEN);
            case GOLD: return style.withColor(Formatting.GOLD);
            case LIGHT_PURPLE: return style.withColor(Formatting.LIGHT_PURPLE);
            case YELLOW: return style.withColor(Formatting.YELLOW);
            case WHITE: return style.withColor(Formatting.WHITE);
            case AQUA: return style.withColor(Formatting.AQUA);
            case ORIGINAL:
            default: return style;
        }
    }

    private static boolean isDecorated(Style style) {
        if (style == null || style.isEmpty()) {
            return false;
        }
        return style.getColor() != null
                || style.getClickEvent() != null
                || style.getHoverEvent() != null
                || style.getInsertion() != null
                || style.isBold()
                || style.isItalic()
                || style.isUnderlined()
                || style.isStrikethrough()
                || style.isObfuscated()
                || (style.getFont() != null && !StyleSpriteSource.DEFAULT.equals(style.getFont()));
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

        List<StyledRun> decoratedRuns() {
            List<StyledRun> runs = new ArrayList<StyledRun>();
            StyledRun current = null;
            for (StyleSpan span : spans) {
                String value = text.substring(span.start(), span.end());
                if (!isDecorated(span.style()) || value.trim().isEmpty()) {
                    if (current != null) {
                        runs.add(current);
                        current = null;
                    }
                    continue;
                }
                if (current != null && current.end() == span.start() && current.style().equals(span.style())) {
                    current = current.extend(span.end(), value);
                } else {
                    if (current != null) {
                        runs.add(current);
                    }
                    current = new StyledRun(span.start(), span.end(), span.style(), value);
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

    private record StyleSpan(int start, int end, Style style) {
        private StyleSpan {
            style = style == null ? Style.EMPTY : style;
        }
    }

    private record StyledRun(int start, int end, Style style, String text) {
        private StyledRun {
            style = style == null ? Style.EMPTY : style;
            text = text == null ? "" : text;
        }

        boolean isInteractive() {
            return style.getClickEvent() != null
                    || style.getHoverEvent() != null
                    || style.getInsertion() != null;
        }

        StyledRun extend(int newEnd, String addition) {
            return new StyledRun(start, newEnd, style, text + (addition == null ? "" : addition));
        }
    }

    private record TargetRun(int start, int end, Style style) {
        private TargetRun {
            style = style == null ? Style.EMPTY : style;
        }
    }
}

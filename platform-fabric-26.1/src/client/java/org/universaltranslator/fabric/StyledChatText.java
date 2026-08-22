package org.universaltranslator.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.universaltranslator.core.InlineTextureCode;
import org.universaltranslator.core.StyledTranslationTemplate;
import org.universaltranslator.core.TranslationTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

final class StyledChatText {
    private StyledChatText() {
    }

    static String translationInput(FormattedText source, Style fallbackStyle) {
        if (source == null) {
            return "";
        }
        Plan plan = plan(source, fallbackStyle);
        return plan == null
                ? InlineTextureText.semanticText(source, fallbackStyle) : plan.template();
    }

    static FormattedText rebuild(
            FormattedText source, String translatedTemplate, Style fallbackStyle) {
        if (source == null || translatedTemplate == null) {
            return null;
        }
        Plan plan = plan(source, fallbackStyle);
        if (plan == null) {
            return null;
        }
        StyledTranslationTemplate.Parsed parsed = StyledTranslationTemplate.parse(
                translatedTemplate, plan.runs().size());
        if (parsed == null) {
            return null;
        }
        MutableComponent output = Component.empty();
        int cursor = 0;
        for (StyledTranslationTemplate.Span span : parsed.spans()) {
            append(output, parsed.text().substring(cursor, span.start()), plan.plainStyle());
            append(output, parsed.text().substring(span.start(), span.end()),
                    plan.runs().get(span.id()).style());
            cursor = span.end();
        }
        append(output, parsed.text().substring(cursor), plan.plainStyle());
        return output;
    }

    private static Plan plan(FormattedText source, Style fallbackStyle) {
        StyledText styled = styledText(source, fallbackStyle);
        boolean preserveOriginal =
                FabricTranslationRuntime.translatedTextColor() == TranslationTextColor.ORIGINAL;
        Style uniform = styled.uniformVisibleStyle();
        if (preserveOriginal && uniform != null) {
            return new Plan(styled.text(), normalizeTextStyle(uniform),
                    new ArrayList<StyledRun>());
        }

        Style plainStyle = preserveOriginal
                ? styled.plainStyle(fallbackStyle) : configuredTranslationStyle();
        List<StyledRun> interactions = new ArrayList<StyledRun>();
        List<StyledRun> underlines = new ArrayList<StyledRun>();
        List<StyledRun> decorations = new ArrayList<StyledRun>();
        for (StyledRun run : styled.styleRuns()) {
            if (run.text().trim().isEmpty()) {
                continue;
            }
            if (isInteractive(run.style())) {
                addTextParts(styled.text(), run, interactions);
            } else if (run.style().isUnderlined()) {
                addTextParts(styled.text(), run, underlines);
            } else if (preserveOriginal && isDecorated(run.style())) {
                addTextParts(styled.text(), run, decorations);
            }
        }
        if (interactions.size() > StyledTranslationTemplate.MAX_SPANS) {
            return null;
        }
        List<StyledRun> selected = new ArrayList<StyledRun>(interactions);
        int remaining = StyledTranslationTemplate.MAX_SPANS - selected.size();
        int underlineCount = Math.min(remaining, underlines.size());
        selected.addAll(underlines.subList(0, underlineCount));
        remaining -= underlineCount;
        if (preserveOriginal) {
            selected.addAll(decorations.subList(0, Math.min(remaining, decorations.size())));
        }
        List<StyledTranslationTemplate.Span> spans =
                new ArrayList<StyledTranslationTemplate.Span>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            StyledRun run = selected.get(index);
            spans.add(StyledTranslationTemplate.span(index, run.start(), run.end()));
        }
        try {
            return new Plan(StyledTranslationTemplate.decorate(styled.text(), spans),
                    normalizeTextStyle(plainStyle), selected);
        } catch (IllegalArgumentException invalidTemplate) {
            return null;
        }
    }

    private static void addTextParts(String text, StyledRun run, List<StyledRun> output) {
        Matcher matcher = InlineTextureCode.matcher(text);
        int cursor = run.start();
        while (matcher.find()) {
            if (matcher.end() <= run.start()) {
                continue;
            }
            if (matcher.start() >= run.end()) {
                break;
            }
            addPart(text, cursor, Math.max(cursor, matcher.start()), run.style(), output);
            cursor = Math.max(cursor, Math.min(run.end(), matcher.end()));
        }
        addPart(text, cursor, run.end(), run.style(), output);
    }

    private static void addPart(
            String text, int start, int end, Style style, List<StyledRun> output) {
        if (end > start && !text.substring(start, end).trim().isEmpty()) {
            output.add(new StyledRun(start, end, normalizeTextStyle(style),
                    text.substring(start, end)));
        }
    }

    private static StyledText styledText(FormattedText source, Style fallbackStyle) {
        List<InlineTextureText.StyledPart> parts =
                InlineTextureText.styledParts(source, fallbackStyle);
        if (source instanceof Component) {
            String expected = joinedText(parts);
            parts = preferParts(parts,
                    componentParts((Component) source, fallbackStyle), expected);
            List<InlineTextureText.StyledPart> visualParts =
                    InlineTextureText.styledParts(((Component) source).getVisualOrderText());
            parts = preferParts(parts, visualParts, expected);
        }
        StringBuilder text = new StringBuilder();
        List<StyleSpan> spans = new ArrayList<StyleSpan>();
        for (InlineTextureText.StyledPart part : parts) {
            int start = text.length();
            text.append(part.text());
            spans.add(new StyleSpan(start, text.length(), part.style()));
        }
        return new StyledText(text.toString(), spans, fallbackStyle);
    }

    private static List<InlineTextureText.StyledPart> componentParts(
            Component source, Style fallbackStyle) {
        List<InlineTextureText.StyledPart> parts =
                new ArrayList<InlineTextureText.StyledPart>();
        appendComponentParts(source,
                fallbackStyle == null ? Style.EMPTY : fallbackStyle, parts, 0);
        return parts;
    }

    private static void appendComponentParts(
            Component source,
            Style parentStyle,
            List<InlineTextureText.StyledPart> output,
            int depth
    ) {
        if (source == null || depth > 64 || output.size() >= 1024) {
            return;
        }
        Style style = source.getStyle().applyTo(parentStyle);
        String ownText = source.plainCopy().getString();
        if (!ownText.isEmpty()) {
            output.add(InlineTextureText.styledPart(ownText, style));
        }
        for (Component sibling : source.getSiblings()) {
            appendComponentParts(sibling, style, output, depth + 1);
        }
    }

    private static List<InlineTextureText.StyledPart> preferParts(
            List<InlineTextureText.StyledPart> current,
            List<InlineTextureText.StyledPart> candidate,
            String expected
    ) {
        if (!expected.equals(joinedText(candidate))) {
            return current;
        }
        return styleScore(candidate) > styleScore(current) ? candidate : current;
    }

    private static int styleScore(List<InlineTextureText.StyledPart> parts) {
        int score = 0;
        for (InlineTextureText.StyledPart part : parts) {
            if (part.text().trim().isEmpty()) {
                continue;
            }
            Style style = part.style();
            if (isInteractive(style)) {
                score += 10000;
            }
            if (style.isUnderlined()) {
                score += 1000;
            }
            if (style.getColor() != null) {
                score += 100;
            }
            if (style.isBold() || style.isItalic()
                    || style.isStrikethrough() || style.isObfuscated()) {
                score += 10;
            }
        }
        return score;
    }

    private static String joinedText(List<InlineTextureText.StyledPart> parts) {
        StringBuilder output = new StringBuilder();
        for (InlineTextureText.StyledPart part : parts) {
            output.append(part.text());
        }
        return output.toString();
    }

    private static boolean isDecorated(Style style) {
        return style != null && !style.isEmpty()
                && (style.getColor() != null
                || isInteractive(style)
                || style.isBold()
                || style.isItalic()
                || style.isUnderlined()
                || style.isStrikethrough()
                || style.isObfuscated());
    }

    private static boolean isInteractive(Style style) {
        return style != null && (style.getClickEvent() != null
                || style.getHoverEvent() != null
                || style.getInsertion() != null);
    }

    private static Style normalizeTextStyle(Style style) {
        return (style == null ? Style.EMPTY : style).withFont(FontDescription.DEFAULT);
    }

    private static Style configuredTranslationStyle() {
        TranslationTextColor color = FabricTranslationRuntime.translatedTextColor();
        if (color == null || !color.changesColor()) {
            return Style.EMPTY;
        }
        switch (color) {
            case GREEN: return Style.EMPTY.withColor(ChatFormatting.GREEN);
            case GOLD: return Style.EMPTY.withColor(ChatFormatting.GOLD);
            case LIGHT_PURPLE: return Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
            case YELLOW: return Style.EMPTY.withColor(ChatFormatting.YELLOW);
            case WHITE: return Style.EMPTY.withColor(ChatFormatting.WHITE);
            case AQUA: return Style.EMPTY.withColor(ChatFormatting.AQUA);
            case ORIGINAL:
            default: return Style.EMPTY;
        }
    }

    private static void append(MutableComponent output, String value, Style style) {
        if (value != null && !value.isEmpty()) {
            output.append(Component.literal(value).setStyle(style == null ? Style.EMPTY : style));
        }
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

        String text() { return text; }

        List<StyledRun> styleRuns() {
            List<StyledRun> runs = new ArrayList<StyledRun>();
            StyledRun current = null;
            for (StyleSpan span : spans) {
                String part = text.substring(span.start(), span.end());
                if (current != null && current.end() == span.start()
                        && current.style().equals(span.style())) {
                    current = current.extend(span.end(), part);
                } else {
                    if (current != null) {
                        runs.add(current);
                    }
                    current = new StyledRun(span.start(), span.end(), span.style(), part);
                }
            }
            if (current != null) {
                runs.add(current);
            }
            return runs;
        }

        Style uniformVisibleStyle() {
            Style uniform = null;
            for (StyleSpan span : spans) {
                if (text.substring(span.start(), span.end()).trim().isEmpty()) {
                    continue;
                }
                if (uniform == null) {
                    uniform = span.style();
                } else if (!uniform.equals(span.style())) {
                    return null;
                }
            }
            return uniform;
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

    private static final class StyledRun {
        private final int start;
        private final int end;
        private final Style style;
        private final String text;

        private StyledRun(int start, int end, Style style, String text) {
            this.start = start;
            this.end = end;
            this.style = style == null ? Style.EMPTY : style;
            this.text = text == null ? "" : text;
        }

        int start() { return start; }
        int end() { return end; }
        Style style() { return style; }
        String text() { return text; }

        StyledRun extend(int newEnd, String addition) {
            return new StyledRun(start, newEnd, style, text + (addition == null ? "" : addition));
        }
    }

    private static final class Plan {
        private final String template;
        private final Style plainStyle;
        private final List<StyledRun> runs;

        private Plan(String template, Style plainStyle, List<StyledRun> runs) {
            this.template = template;
            this.plainStyle = plainStyle;
            this.runs = runs;
        }

        String template() { return template; }
        Style plainStyle() { return plainStyle; }
        List<StyledRun> runs() { return runs; }
    }
}

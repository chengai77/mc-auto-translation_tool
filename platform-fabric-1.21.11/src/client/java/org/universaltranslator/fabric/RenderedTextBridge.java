package org.universaltranslator.fabric;

import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Formatting;
import org.universaltranslator.core.LanguageHeuristics;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TranslationTextStyling;
import org.universaltranslator.core.TextKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RenderedTextBridge {
    private static final AtomicBoolean ITEM_TOOLTIP_REACHED = new AtomicBoolean();
    private static final AtomicBoolean ITEM_TOOLTIP_APPLIED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> BOOK_PAGE_PENDING = new ThreadLocal<Boolean>();
    private static final Pattern INLINE_TEXTURE_MARKER =
            Pattern.compile("\\[(?:[A-Za-z0-9_.-]+:)?[A-Za-z0-9_.-]+/[A-Za-z0-9_./:-]+\\]");

    private RenderedTextBridge() {
    }

    public static String translate(String text) {
        return translate(text, null);
    }

    public static String translate(String text, SignTranslationContext.WidthMeasurer measurer) {
        return translate(text, measurer, TranslationRenderContext.current());
    }

    public static String translate(String text, SignTranslationContext.WidthMeasurer measurer, TextKind kind) {
        TextKind currentKind = kind == null ? TranslationRenderContext.current() : kind;
        if (isChatKind(currentKind)) {
            return text;
        }
        String translated = translateRaw(text, measurer, currentKind);
        if (text == null || text.equals(translated)) {
            return text;
        }
        return TranslationTextStyling.applyTranslatedStyle(
                text, translated, FabricTranslationRuntime.translatedTextColor());
    }

    public static Text translate(Text text) {
        if (text == null) {
            return null;
        }
        TextKind currentKind = TranslationRenderContext.current();
        if (isChatKind(currentKind)) {
            return text;
        }
        String original = text.getString();
        String translated = translateRaw(original);
        if (original.equals(translated)) {
            return text;
        }
        StyledTextSnapshot snapshot = styledText(text, original, text.getStyle());
        Text rebuilt = rebuildInlineTextureText(snapshot, translated, text.getStyle());
        if (rebuilt != null) {
            return rebuilt;
        }
        rebuilt = BookTextStyler.rebuild(text, translated, text.getStyle());
        if (rebuilt != null) {
            return rebuilt;
        }
        return Text.literal(translated).setStyle(translatedStyle(text.getStyle()));
    }

    public static Text translateBookPage(Text text) {
        return translateDirectText(text, TextKind.BOOK);
    }

    public static void beginBookPageRender() {
        BOOK_PAGE_PENDING.remove();
    }

    public static boolean consumeBookPagePending() {
        Boolean pending = BOOK_PAGE_PENDING.get();
        BOOK_PAGE_PENDING.remove();
        return pending != null && pending.booleanValue();
    }

    public static Text translateEntityName(Text text) {
        return translateDirectText(text, TextKind.ENTITY_NAME);
    }

    public static Text translateHeldItemName(Text text) {
        return translateDirectText(text, TextKind.ITEM_NAME);
    }

    public static void preloadUrgentHudText(Text text, TextKind kind, boolean overlayTinted) {
        FabricTranslationRuntime.preloadUrgentHudText(text, kind, overlayTinted);
    }

    public static OrderedText translate(OrderedText text) {
        return translate(text, null);
    }

    public static OrderedText translate(
            OrderedText text, SignTranslationContext.WidthMeasurer measurer) {
        return translate(text, measurer, TranslationRenderContext.current());
    }

    public static OrderedText translate(
            OrderedText text, SignTranslationContext.WidthMeasurer measurer, TextKind kind) {
        if (text == null) {
            return null;
        }
        TextKind currentKind = kind == null ? TranslationRenderContext.current() : kind;
        if (currentKind != TextKind.SIGN && SignTranslationContext.isSubmittedText(text)) {
            return text;
        }
        if (currentKind == TextKind.CHAT || currentKind == TextKind.SYSTEM_MESSAGE) {
            return text;
        }
        StyledTextSnapshot snapshot = styledText(text);
        String original = snapshot.text();
        String translated = translateRaw(original, measurer, currentKind);
        if (original.equals(translated)) {
            return text;
        }
        Text rebuilt = rebuildInlineTextureText(snapshot, translated, snapshot.firstStyle());
        if (rebuilt != null) {
            return rebuilt.asOrderedText();
        }
        MutableText replacement = Text.literal(translated).setStyle(translatedStyle(snapshot.firstStyle()));
        return replacement.asOrderedText();
    }

    public static StringVisitable translateChatMessage(StringVisitable text) {
        if (text == null || TranslationRenderContext.isTextInput()
                || TranslationRenderContext.isTranslationSuppressed()) {
            return text;
        }
        String original = text.getString();
        String translated = FabricTranslationRuntime.translateForRender(
                original, TextKind.CHAT);
        if (original.equals(translated)) {
            return text;
        }
        Style style = text instanceof Text ? ((Text) text).getStyle() : Style.EMPTY;
        StringVisitable rebuilt = StyledChatText.rebuild(text, translated, style);
        return rebuilt == null ? StringVisitable.styled(translated, translatedStyle(style)) : rebuilt;
    }

    public static StringVisitable translate(StringVisitable text) {
        if (text == null) {
            return null;
        }
        if (text instanceof Text) {
            return translate((Text) text);
        }
        String original = text.getString();
        String translated = translateRaw(original);
        if (original.equals(translated)) {
            return text;
        }
        return StringVisitable.styled(translated, translatedStyle(Style.EMPTY));
    }

    /** Translates item names and lore before DrawContext creates tooltip components. */
    public static List<Text> translateTooltip(List<Text> lines) {
        return translateTooltip(lines, false);
    }

    /** Canonical Screen.getTooltipFromItem hook for inventories and containers. */
    public static List<Text> translateItemTooltip(List<Text> lines) {
        if (ITEM_TOOLTIP_REACHED.compareAndSet(false, true)) {
            System.out.println("[MC Auto Translation Tool] Item tooltip producer reached");
        }
        return translateTooltip(lines, true);
    }

    private static List<Text> translateTooltip(List<Text> lines, boolean itemTooltip) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        List<String> originals = new ArrayList<String>(lines.size());
        for (Text line : lines) {
            originals.add(line == null ? "" : line.getString());
        }
        TextKind tooltipKind = itemTooltip ? TextKind.ITEM_LORE : TextKind.TOOLTIP;
        List<String> translatedLines = FabricTranslationRuntime.translateIndependentLinesForRender(
                originals, tooltipKind);
        List<Text> replacement = null;
        for (int index = 0; index < lines.size(); index++) {
            Text line = lines.get(index);
            if (line == null) {
                continue;
            }
            String original = originals.get(index);
            String translated = translatedLines.get(index);
            if (!original.equals(translated)) {
                if (replacement == null) {
                    replacement = new ArrayList<Text>(lines);
                }
                Text rebuilt = rebuildInlineTextureText(
                        styledText(line, original, line.getStyle()),
                        translated,
                        line.getStyle());
                replacement.set(index, rebuilt == null
                        ? Text.literal(translated).setStyle(translatedStyle(line.getStyle()))
                        : rebuilt);
            }
        }
        if (itemTooltip && replacement != null
                && ITEM_TOOLTIP_APPLIED.compareAndSet(false, true)) {
            System.out.println("[MC Auto Translation Tool] Item tooltip translation applied");
        }
        return replacement == null ? lines : replacement;
    }

    private static Text translateDirectText(Text text, TextKind kind) {
        if (text == null) {
            return null;
        }
        String original = text.getString();
        String translated = FabricTranslationRuntime.translateForRender(original, kind);
        if (kind == TextKind.BOOK && original.equals(translated)
                && shouldRetryDirectText(original, kind)) {
            BOOK_PAGE_PENDING.set(Boolean.TRUE);
        }
        if (original.equals(translated)) {
            return text;
        }
        StyledTextSnapshot snapshot = styledText(text, original, text.getStyle());
        Text rebuilt = rebuildInlineTextureText(snapshot, translated, text.getStyle());
        if (rebuilt != null) {
            return rebuilt;
        }
        rebuilt = BookTextStyler.rebuild(text, translated, text.getStyle());
        if (rebuilt != null) {
            return rebuilt;
        }
        return Text.literal(translated).setStyle(translatedStyle(text.getStyle()));
    }

    private static boolean shouldRetryDirectText(String original, TextKind kind) {
        FabricConfig config = FabricTranslationRuntime.currentConfig();
        return config != null && config.allows(kind)
                && LanguageHeuristics.shouldTranslate(original, config.targetLanguage);
    }

    private static boolean isChatKind(TextKind kind) {
        return kind == TextKind.CHAT || kind == TextKind.SYSTEM_MESSAGE;
    }

    private static String translateRaw(String text) {
        return translateRaw(text, null);
    }

    private static String translateRaw(String text, SignTranslationContext.WidthMeasurer measurer) {
        return translateRaw(text, measurer, TranslationRenderContext.current());
    }

    private static String translateRaw(
            String text,
            SignTranslationContext.WidthMeasurer measurer,
            TextKind kind
    ) {
        if (TranslationRenderContext.isTextInput()
                || TranslationRenderContext.isTranslationSuppressed()) {
            return text;
        }
        TextKind currentKind = kind == null ? TranslationRenderContext.current() : kind;
        if (currentKind == TextKind.SIGN) {
            String translated = SignTranslationContext.translateLine(text, measurer);
            return translated == null ? text : translated;
        }
        return FabricTranslationRuntime.translateForRender(text, currentKind);
    }

    private static Style translatedStyle(Style original) {
        if (original == null) {
            original = Style.EMPTY;
        }
        TranslationTextColor color = FabricTranslationRuntime.translatedTextColor();
        if (original.getColor() != null || color == null || !color.changesColor()) {
            return original;
        }
        switch (color) {
            case GREEN: return original.withColor(Formatting.GREEN);
            case GOLD: return original.withColor(Formatting.GOLD);
            case LIGHT_PURPLE: return original.withColor(Formatting.LIGHT_PURPLE);
            case YELLOW: return original.withColor(Formatting.YELLOW);
            case WHITE: return original.withColor(Formatting.WHITE);
            case AQUA: return original.withColor(Formatting.AQUA);
            case ORIGINAL:
            default: return original;
        }
    }

    private static StyledTextSnapshot styledText(OrderedText text) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        text.accept((index, style, codePoint) -> {
            int start = value.length();
            value.appendCodePoint(codePoint);
            spans.add(new StyleSpan(start, value.length(), style == null ? Style.EMPTY : style));
            return true;
        });
        return new StyledTextSnapshot(value.toString(), spans, Style.EMPTY);
    }

    private static StyledTextSnapshot styledText(OrderedText text, String fallbackText, Style fallbackStyle) {
        StyledTextSnapshot snapshot = styledText(text);
        if (snapshot.text().equals(fallbackText)) {
            return snapshot;
        }
        return StyledTextSnapshot.plain(fallbackText, fallbackStyle);
    }

    private static StyledTextSnapshot styledText(Text text, String fallbackText, Style fallbackStyle) {
        final StringBuilder value = new StringBuilder();
        final List<StyleSpan> spans = new ArrayList<StyleSpan>();
        text.visit((style, part) -> {
            if (part != null && !part.isEmpty()) {
                int start = value.length();
                value.append(part);
                spans.add(new StyleSpan(start, value.length(), style == null ? Style.EMPTY : style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        StyledTextSnapshot snapshot = new StyledTextSnapshot(value.toString(), spans, fallbackStyle);
        if (snapshot.text().equals(fallbackText)) {
            return snapshot;
        }
        return styledText(text.asOrderedText(), fallbackText, fallbackStyle);
    }

    private static Text rebuildInlineTextureText(
            StyledTextSnapshot snapshot,
            String translated,
            Style fallbackStyle
    ) {
        List<StyledMarker> sourceMarkers = snapshot.inlineTextureMarkers();
        if (sourceMarkers.isEmpty() || !INLINE_TEXTURE_MARKER.matcher(translated).find()) {
            return null;
        }
        Matcher matcher = INLINE_TEXTURE_MARKER.matcher(translated);
        MutableText output = Text.empty();
        Style textStyle = translatedStyle(plainTextStyle(
                snapshot.firstNonMarkerStyle(sourceMarkers, fallbackStyle)));
        int cursor = 0;
        int markerIndex = 0;
        while (matcher.find()) {
            if (markerIndex >= sourceMarkers.size()) {
                return null;
            }
            StyledMarker sourceMarker = sourceMarkers.get(markerIndex++);
            if (!sourceMarker.text().equals(matcher.group())) {
                return null;
            }
            appendStyled(output, translated.substring(cursor, matcher.start()), textStyle);
            Style markerStyle = renderableMarkerStyle(sourceMarker.style());
            if (markerStyle != null) {
                appendStyled(output, matcher.group(), markerStyle);
            }
            cursor = matcher.end();
        }
        if (markerIndex != sourceMarkers.size()) {
            return null;
        }
        appendStyled(output, translated.substring(cursor), textStyle);
        return output;
    }

    private static Style plainTextStyle(Style style) {
        return style == null ? Style.EMPTY : style.withFont(StyleSpriteSource.DEFAULT);
    }

    private static Style renderableMarkerStyle(Style style) {
        if (style == null || StyleSpriteSource.DEFAULT.equals(style.getFont())) {
            return null;
        }
        return style;
    }

    private static void appendStyled(MutableText output, String value, Style style) {
        if (value != null && !value.isEmpty()) {
            output.append(Text.literal(value).setStyle(style == null ? Style.EMPTY : style));
        }
    }

    private static final class StyledTextSnapshot {
        private final String text;
        private final List<StyleSpan> spans;
        private final Style fallbackStyle;

        private StyledTextSnapshot(String text, List<StyleSpan> spans, Style fallbackStyle) {
            this.text = text == null ? "" : text;
            this.spans = spans;
            this.fallbackStyle = fallbackStyle == null ? Style.EMPTY : fallbackStyle;
        }

        static StyledTextSnapshot plain(String text, Style style) {
            List<StyleSpan> spans = new ArrayList<StyleSpan>();
            if (text != null && !text.isEmpty()) {
                spans.add(new StyleSpan(0, text.length(), style == null ? Style.EMPTY : style));
            }
            return new StyledTextSnapshot(text, spans, style);
        }

        String text() {
            return text;
        }

        Style firstStyle() {
            return spans.isEmpty() ? fallbackStyle : spans.get(0).style();
        }

        List<StyledMarker> inlineTextureMarkers() {
            List<StyledMarker> markers = new ArrayList<StyledMarker>();
            Matcher matcher = INLINE_TEXTURE_MARKER.matcher(text);
            while (matcher.find()) {
                markers.add(new StyledMarker(
                        matcher.start(), matcher.end(), matcher.group(), styleAt(matcher.start())));
            }
            return markers;
        }

        Style firstNonMarkerStyle(List<StyledMarker> markers, Style fallback) {
            for (StyleSpan span : spans) {
                if (!insideMarker(span.start(), markers)
                        && !text.substring(span.start(), span.end()).trim().isEmpty()) {
                    return span.style();
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

        private static boolean insideMarker(int index, List<StyledMarker> markers) {
            for (StyledMarker marker : markers) {
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

    private static final class StyledMarker {
        private final int start;
        private final int end;
        private final String text;
        private final Style style;

        private StyledMarker(int start, int end, String text, Style style) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.style = style == null ? Style.EMPTY : style;
        }

        int start() {
            return start;
        }

        int end() {
            return end;
        }

        String text() {
            return text;
        }

        Style style() {
            return style;
        }
    }
}

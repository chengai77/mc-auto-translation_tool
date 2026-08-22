package org.universaltranslator.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.universaltranslator.core.ChatMessageClassifier;
import org.universaltranslator.core.InlineTextureCode;
import org.universaltranslator.core.LanguageHeuristics;
import org.universaltranslator.core.StyledTranslationTemplate;
import org.universaltranslator.core.TranslationTextColor;
import org.universaltranslator.core.TranslationTextStyling;
import org.universaltranslator.core.TextKind;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RenderedTextBridge {
    private static final AtomicBoolean ITEM_TOOLTIP_REACHED = new AtomicBoolean();
    private static final AtomicBoolean ITEM_TOOLTIP_APPLIED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> BOOK_PAGE_PENDING = new ThreadLocal<Boolean>();

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

    public static Component translate(Component text) {
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
        return rebuildStyledText(text, original, translated, currentKind);
    }

    public static Component translateBookPage(Component text) {
        return translateDirectText(text, TextKind.BOOK);
    }

    public static Component translateHologramText(Component text) {
        return translateCompleteText(text, TextKind.HOLOGRAM);
    }

    public static Component translatePlainHologramText(Component text) {
        if (text == null) {
            return null;
        }
        String visibleOriginal = text.getString();
        String original = InlineTextureText.semanticText(text, text.getStyle());
        String translated = FabricTranslationRuntime.translatePlainHologramForRender(
                original, visibleOriginal);
        if (original.equals(translated)) {
            return text;
        }
        return rebuildStyledText(text, original, translated, TextKind.HOLOGRAM);
    }

    public static void beginBookPageRender() {
        BOOK_PAGE_PENDING.remove();
    }

    public static boolean consumeBookPagePending() {
        Boolean pending = BOOK_PAGE_PENDING.get();
        BOOK_PAGE_PENDING.remove();
        return pending != null && pending.booleanValue();
    }

    public static Component translateEntityName(Component text) {
        return translateDirectText(text, TextKind.ENTITY_NAME);
    }

    public static Component translateHeldItemName(Component text) {
        return translateDirectText(text, TextKind.ITEM_NAME);
    }

    public static void preloadUrgentHudText(Component text, TextKind kind, boolean overlayTinted) {
        FabricTranslationRuntime.preloadUrgentHudText(text, kind, overlayTinted);
    }

    public static FormattedCharSequence translate(FormattedCharSequence text) {
        return translate(text, TranslationRenderContext.current());
    }

    public static FormattedCharSequence translate(FormattedCharSequence text, TextKind kind) {
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
        String original = InlineTextureText.text(text);
        Style firstStyle = InlineTextureText.firstStyle(text);
        String translated = translateRaw(original, null, currentKind);
        if (original.equals(translated)) {
            return text;
        }
        Component rebuilt = InlineTextureText.rebuild(
                text, original, translated, firstStyle);
        if (rebuilt != null) {
            return rebuilt.getVisualOrderText();
        }
        if (hasInlineTexture(original)) {
            return text;
        }
        MutableComponent replacement = Component.literal(translated).setStyle(translatedStyle(firstStyle));
        return replacement.getVisualOrderText();
    }

    public static FormattedText translateChatMessage(FormattedText text) {
        return translateChatMessage(text, false);
    }

    public static FormattedText translateChatMessage(
            FormattedText text, boolean playerMessage) {
        if (text == null || TranslationRenderContext.isTextInput()
                || TranslationRenderContext.isTranslationSuppressed()) {
            return text;
        }
        String original = text.getString();
        TextKind kind = playerMessage || ChatMessageClassifier.looksLikePlayerChat(original)
                ? TextKind.CHAT : TextKind.SYSTEM_MESSAGE;
        Style style = text instanceof Component ? ((Component) text).getStyle() : Style.EMPTY;
        String request = StyledChatText.translationInput(text, style);
        String translated = FabricTranslationRuntime.translateCompleteForRender(
                request, original, kind);
        if (request.equals(translated)) {
            return text;
        }
        FormattedText rebuilt = StyledChatText.rebuild(text, translated, style);
        if (!(rebuilt instanceof Component)) {
            return text;
        }
        Component base = (Component) rebuilt;
        Component textured = InlineTextureText.restore(text, base, style);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(request) ? text : base;
    }

    public static FormattedText translate(FormattedText text) {
        if (text == null) {
            return null;
        }
        if (text instanceof Component) {
            return translate((Component) text);
        }
        String original = text.getString();
        String translated = translateRaw(original);
        if (original.equals(translated)) {
            return text;
        }
        Component base = Component.literal(translated).setStyle(translatedStyle(Style.EMPTY));
        Component textured = InlineTextureText.restore(text, original, base, Style.EMPTY);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(original) ? text : base;
    }

    /** 提示框前翻译 */
    public static List<Component> translateTooltip(List<Component> lines) {
        return translateTooltip(lines, false);
    }

    /** 物品提示钩子 */
    public static List<Component> translateItemTooltip(List<Component> lines) {
        if (ITEM_TOOLTIP_REACHED.compareAndSet(false, true)) {
            System.out.println("[MC Auto Translation Tool] Item tooltip producer reached");
        }
        return translateTooltip(lines, true);
    }

    private static List<Component> translateTooltip(List<Component> lines, boolean itemTooltip) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        List<String> originals = new ArrayList<String>(lines.size());
        for (Component line : lines) {
            originals.add(line == null ? "" : line.getString());
        }
        TextKind tooltipKind = itemTooltip ? TextKind.ITEM_LORE : TextKind.TOOLTIP;
        List<String> translatedLines = FabricTranslationRuntime.translateIndependentLinesForRender(
                originals, tooltipKind);
        List<Component> replacement = null;
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            if (line == null) {
                continue;
            }
            String original = originals.get(index);
            String translated = translatedLines.get(index);
            if (!original.equals(translated)) {
                if (replacement == null) {
                    replacement = new ArrayList<Component>(lines);
                }
                replacement.set(index, rebuildStyledText(
                        line, original, translated, tooltipKind));
            }
        }
        if (itemTooltip && replacement != null
                && ITEM_TOOLTIP_APPLIED.compareAndSet(false, true)) {
            System.out.println("[MC Auto Translation Tool] Item tooltip translation applied");
        }
        return replacement == null ? lines : replacement;
    }

    private static Component translateDirectText(Component text, TextKind kind) {
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
        return rebuildStyledText(text, original, translated, kind);
    }

    private static Component translateCompleteText(Component text, TextKind kind) {
        if (text == null) {
            return null;
        }
        String visibleOriginal = text.getString();
        Style style = text.getStyle();
        String original = InlineTextureText.semanticText(text, style);
        String request = StyledChatText.translationInput(text, style);
        String translated = FabricTranslationRuntime.translateCompleteForRender(
                request, visibleOriginal, kind);
        if (request.equals(translated)) {
            return text;
        }
        FormattedText structured = StyledChatText.rebuild(text, translated, style);
        Component base = structured instanceof Component ? (Component) structured : null;
        if (base == null) {
            String plain = StyledTranslationTemplate.strip(translated);
            base = BookTextStyler.rebuild(text, plain, style, kind);
            if (base == null) {
                base = Component.literal(plain).setStyle(translatedStyle(style));
            }
        }
        Component textured = InlineTextureText.restore(text, original, base, style);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(original) ? text : base;
    }

    private static Component rebuildStyledText(
            Component source, String original, String translated, TextKind kind) {
        Component base = BookTextStyler.rebuild(
                source, translated, source.getStyle(), kind);
        if (base == null) {
            base = Component.literal(translated).setStyle(translatedStyle(source.getStyle()));
        }
        Component textured = InlineTextureText.restore(
                source, original, base, source.getStyle());
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(original) ? source : base;
    }

    private static boolean hasInlineTexture(String text) {
        return InlineTextureCode.matcher(text).find();
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

    static Style translatedStyle(Style original) {
        if (original == null) {
            original = Style.EMPTY;
        }
        TranslationTextColor color = FabricTranslationRuntime.translatedTextColor();
        if (original.getColor() != null || color == null || !color.changesColor()) {
            return original;
        }
        switch (color) {
            case GREEN: return original.withColor(ChatFormatting.GREEN);
            case GOLD: return original.withColor(ChatFormatting.GOLD);
            case LIGHT_PURPLE: return original.withColor(ChatFormatting.LIGHT_PURPLE);
            case YELLOW: return original.withColor(ChatFormatting.YELLOW);
            case WHITE: return original.withColor(ChatFormatting.WHITE);
            case AQUA: return original.withColor(ChatFormatting.AQUA);
            case ORIGINAL:
            default: return original;
        }
    }

}

package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.StringVisitable;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
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
import java.util.concurrent.ConcurrentHashMap;

public final class RenderedTextBridge {
    private static final AtomicBoolean ITEM_TOOLTIP_REACHED = new AtomicBoolean();
    private static final AtomicBoolean ITEM_TOOLTIP_APPLIED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> BOOK_PAGE_PENDING = new ThreadLocal<Boolean>();
    private static final ConcurrentHashMap<Integer, EntityNameSnapshot> ENTITY_NAME_SNAPSHOTS =
            new ConcurrentHashMap<Integer, EntityNameSnapshot>();
    private static final int MAX_ENTITY_NAME_SNAPSHOTS = 4_096;

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
        return rebuildStyledText(text, original, translated, currentKind);
    }

    public static Text translateBookPage(Text text) {
        return translateDirectText(text, TextKind.BOOK);
    }

    public static Text translateHologramText(Text text) {
        return translateCompleteText(text, TextKind.HOLOGRAM);
    }

    public static Text translatePlainHologramText(Text text) {
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

    public static Text translateEntityName(Entity entity, Text text) {
        if (text == null || entity == null) {
            return text;
        }
        if (entity == MinecraftClient.getInstance().player
                && MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            return null;
        }
        int entityId = entity.getId();
        String original = text.getString();
        EntityNameSnapshot snapshot = ENTITY_NAME_SNAPSHOTS.get(entityId);
        if (snapshot != null && original.equals(snapshot.translated)) {
            return text;
        }
        String translated = FabricTranslationRuntime.translateForRender(
                original, TextKind.ENTITY_NAME);
        if (original.equals(translated)) {
            if (snapshot != null && original.equals(snapshot.source)
                    && !original.equals(snapshot.translated)) {
                return rebuildStyledText(text, original, snapshot.translated,
                        TextKind.ENTITY_NAME);
            }
            return text;
        }
        ENTITY_NAME_SNAPSHOTS.put(entityId,
                new EntityNameSnapshot(original, translated));
        trimEntityNameSnapshots();
        return rebuildStyledText(text, original, translated, TextKind.ENTITY_NAME);
    }

    private static void trimEntityNameSnapshots() {
        while (ENTITY_NAME_SNAPSHOTS.size() > MAX_ENTITY_NAME_SNAPSHOTS) {
            Integer first = ENTITY_NAME_SNAPSHOTS.keys().nextElement();
            ENTITY_NAME_SNAPSHOTS.remove(first);
        }
    }

    private static final class EntityNameSnapshot {
        private final String source;
        private final String translated;

        private EntityNameSnapshot(String source, String translated) {
            this.source = source;
            this.translated = translated;
        }
    }

    public static Text translateHeldItemName(Text text) {
        return translateDirectText(text, TextKind.ITEM_NAME);
    }

    public static boolean preloadUrgentHudText(Text text, TextKind kind, boolean overlayTinted) {
        return FabricTranslationRuntime.preloadUrgentHudText(text, kind, overlayTinted);
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
        String original = InlineTextureText.text(text);
        Style firstStyle = InlineTextureText.firstStyle(text);
        String translated = translateRaw(original, measurer, currentKind);
        if (original.equals(translated)) {
            return text;
        }
        Text rebuilt = InlineTextureText.rebuild(
                text, original, translated, firstStyle);
        if (rebuilt != null) {
            return rebuilt.asOrderedText();
        }
        if (hasInlineTexture(original)) {
            return text;
        }
        MutableText replacement = Text.literal(translated).setStyle(translatedStyle(firstStyle));
        return replacement.asOrderedText();
    }

    public static StringVisitable translateChatMessage(StringVisitable text) {
        return translateChatMessage(text, false);
    }

    public static StringVisitable translateChatMessage(
            StringVisitable text, boolean playerMessage) {
        if (text == null || TranslationRenderContext.isTextInput()
                || TranslationRenderContext.isTranslationSuppressed()) {
            return text;
        }
        String original = text.getString();
        TextKind kind = playerMessage || ChatMessageClassifier.looksLikePlayerChat(original)
                ? TextKind.CHAT : TextKind.SYSTEM_MESSAGE;
        Style style = text instanceof Text ? ((Text) text).getStyle() : Style.EMPTY;
        String request = StyledChatText.translationInput(text, style);
        String translated = FabricTranslationRuntime.translateCompleteForRender(
                request, original, kind);
        if (request.equals(translated)) {
            return text;
        }
        StringVisitable rebuilt = StyledChatText.rebuild(text, translated, style);
        if (!(rebuilt instanceof Text)) {
            return text;
        }
        Text base = (Text) rebuilt;
        Text textured = InlineTextureText.restore(text, base, style);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(request) ? text : base;
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
        Text base = Text.literal(translated).setStyle(translatedStyle(Style.EMPTY));
        Text textured = InlineTextureText.restore(text, original, base, Style.EMPTY);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(original) ? text : base;
    }

    /** 提示框前翻译 */
    public static List<Text> translateTooltip(List<Text> lines) {
        return translateTooltip(lines, false);
    }

    /** 物品提示钩子 */
    public static List<Text> translateItemTooltip(List<Text> lines) {
        if (ITEM_TOOLTIP_REACHED.compareAndSet(false, true)) {
            System.out.println("[MC Auto Translation Tool] Item tooltip producer reached");
        }
        return translateTooltip(lines, true);
    }

    private static List<Text> translateTooltip(List<Text> lines, boolean itemTooltip) {
        if (lines == null || lines.isEmpty()
                || FabricLocalTextGuard.isTranslatorScreen(MinecraftClient.getInstance())) {
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
        return rebuildStyledText(text, original, translated, kind);
    }

    private static Text translateCompleteText(Text text, TextKind kind) {
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
        StringVisitable structured = StyledChatText.rebuild(text, translated, style);
        Text base = structured instanceof Text ? (Text) structured : null;
        if (base == null) {
            String plain = StyledTranslationTemplate.strip(translated);
            base = BookTextStyler.rebuild(text, plain, style, kind);
            if (base == null) {
                base = Text.literal(plain).setStyle(translatedStyle(style));
            }
        }
        Text textured = InlineTextureText.restore(text, original, base, style);
        if (textured != null) {
            return textured;
        }
        return hasInlineTexture(original) ? text : base;
    }

    private static Text rebuildStyledText(
            Text source, String original, String translated, TextKind kind) {
        Text base = BookTextStyler.rebuild(
                source, translated, source.getStyle(), kind);
        if (base == null) {
            base = Text.literal(translated).setStyle(translatedStyle(source.getStyle()));
        }
        Text textured = InlineTextureText.restore(
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
                || FabricLocalTextGuard.isTranslatorScreen(MinecraftClient.getInstance())
                || TranslationRenderContext.isTranslationSuppressed()) {
            return text;
        }
        TextKind currentKind = kind == null ? TranslationRenderContext.current() : kind;
        if (currentKind == TextKind.SIGN) {
            String translated = SignTranslationContext.translateLine(text, measurer);
            return translated == null ? text : translated;
        }
        if (containsLineBreak(text)) {
            return FabricTranslationRuntime.translateCompleteForRender(text, currentKind);
        }
        return FabricTranslationRuntime.translateForRender(text, currentKind);
    }

    private static boolean containsLineBreak(String text) {
        return text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }

    static Style translatedStyle(Style original) {
        if (original == null) {
            original = Style.EMPTY;
        }
        // 翻译文本使用默认字体
        original = original.withFont(Style.DEFAULT_FONT_ID);
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

}

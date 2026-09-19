package org.universaltranslator.fabric;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public final class StyledChatTextSelfTest {
    private static final Identifier INLINE_FONT =
            Identifier.of("universal_translator", "inline_test");

    private StyledChatTextSelfTest() {
    }

    public static void main(String[] args) {
        preservesUrlAttachment();
        preservesCommandAttachments();
        preservesDecoratedText();
        restoresInlineTextureFonts();
        rejectsBrokenStyleTemplates();
        updatesCompletedTranslationLogEntries();
        System.out.println("StyledChatTextSelfTest: all checks passed");
    }

    private static void preservesUrlAttachment() {
        ClickEvent click = new ClickEvent(
                ClickEvent.Action.OPEN_URL, "https://example.invalid/pack");
        Style linkStyle = Style.EMPTY.withColor(Formatting.YELLOW)
                .withUnderline(true)
                .withClickEvent(click)
                .withInsertion("resource-pack");
        Text source = Text.empty()
                .append(Text.literal("Click ").formatted(Formatting.YELLOW))
                .append(Text.literal("[HERE]").setStyle(linkStyle))
                .append(Text.literal(" to install").formatted(Formatting.YELLOW));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        assertContains(template, "{UT_STYLE_");
        Text rebuilt = requireText(StyledChatText.rebuild(
                source,
                template.replace("Click ", "点击")
                        .replace("[HERE]", "[这里]")
                        .replace(" to install", "安装"),
                Style.EMPTY));
        Style actual = styleFor(rebuilt, "[这里]");

        assertEquals(click, actual.getClickEvent());
        assertEquals("resource-pack", actual.getInsertion());
        assertTrue(actual.isUnderlined());
        assertEquals(linkStyle.getColor(), actual.getColor());
    }

    private static void preservesCommandAttachments() {
        ClickEvent easy = new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/trigger settings set 101");
        ClickEvent hard = new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/trigger settings set 103");
        MutableText source = Text.empty()
                .append(Text.literal("Difficulty: ").formatted(Formatting.AQUA))
                .append(button("Easy", Formatting.GREEN, easy))
                .append(Text.literal(" "))
                .append(button("Hard", Formatting.RED, hard));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        Text rebuilt = requireText(StyledChatText.rebuild(
                source,
                template.replace("Difficulty: ", "难度：")
                        .replace("Easy", "简单")
                        .replace("Hard", "困难"),
                Style.EMPTY));

        assertEquals(easy, styleFor(rebuilt, "简单").getClickEvent());
        assertEquals(hard, styleFor(rebuilt, "困难").getClickEvent());
    }

    private static void preservesDecoratedText() {
        Style valueStyle = Style.EMPTY.withColor(Formatting.GREEN)
                .withBold(true)
                .withInsertion("ready");
        Text source = Text.empty()
                .append(Text.literal("Status: ").formatted(Formatting.AQUA))
                .append(Text.literal("Ready").setStyle(valueStyle));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        Text rebuilt = requireText(StyledChatText.rebuild(
                source,
                template.replace("Status: ", "状态：").replace("Ready", "就绪"),
                Style.EMPTY));
        Style actual = styleFor(rebuilt, "就绪");

        assertEquals(valueStyle.getColor(), actual.getColor());
        assertEquals("ready", actual.getInsertion());
        assertTrue(actual.isBold());
        assertEquals(Style.DEFAULT_FONT_ID, actual.getFont());
    }

    private static void restoresInlineTextureFonts() {
        Style markerStyle = Style.EMPTY.withFont(INLINE_FONT);
        Text source = Text.empty()
                .append(Text.literal("\uFFFC").setStyle(markerStyle))
                .append(Text.literal(" Open "))
                .append(Text.literal("\uFFFC").setStyle(markerStyle));
        String semantic = InlineTextureText.semanticText(source, Style.EMPTY);

        assertContains(semantic, "[ut_object/font_0@universal_translator:inline_test]");
        assertContains(semantic, "[ut_object/font_1@universal_translator:inline_test]");
        Text translated = Text.literal(semantic.replace("Open", "打开"));
        Text restored = InlineTextureText.restore(source, semantic, translated, Style.EMPTY);

        assertTrue(restored != null);
        final int[] markers = new int[1];
        final int[] translatedCharacters = new int[1];
        restored.asOrderedText().accept((index, style, codePoint) -> {
            if (codePoint == 0xFFFC) {
                markers[0]++;
                assertEquals(INLINE_FONT, style.getFont());
            } else if (!Character.isWhitespace(codePoint)) {
                translatedCharacters[0]++;
                assertEquals(Style.DEFAULT_FONT_ID, style.getFont());
            }
            return true;
        });
        assertEquals(2, markers[0]);
        assertTrue(translatedCharacters[0] > 0);
    }

    private static void rejectsBrokenStyleTemplates() {
        Text source = Text.empty()
                .append(Text.literal("Action: "))
                .append(Text.literal("Run").setStyle(
                        Style.EMPTY.withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/trigger test"))));
        String template = StyledChatText.translationInput(source, Style.EMPTY);
        assertContains(template, "{UT_STYLE_");
        assertTrue(StyledChatText.rebuild(
                source, template.replace("_END}", "_BROKEN}"), Style.EMPTY) == null);
    }

    private static void updatesCompletedTranslationLogEntries() {
        TranslationLog.clear();
        TranslationLog.add("Original", "旧译文");
        TranslationLog.add("Original", "新译文");
        List<TranslationLog.Entry> entries = TranslationLog.entries();

        assertEquals(1, entries.size());
        assertEquals("Original", entries.get(0).original);
        assertEquals("新译文", entries.get(0).translated);
    }

    private static Text button(
            String label,
            Formatting color,
            ClickEvent click
    ) {
        return Text.literal(label).setStyle(
                Style.EMPTY.withColor(color)
                        .withUnderline(true)
                        .withClickEvent(click));
    }

    private static Style styleFor(Text text, String expectedText) {
        for (InlineTextureText.StyledPart part :
                InlineTextureText.styledParts(text, Style.EMPTY)) {
            if (part.text().contains(expectedText)) {
                return part.style();
            }
        }
        throw new AssertionError("Missing styled text: " + expectedText);
    }

    private static Text requireText(Object value) {
        if (!(value instanceof Text)) {
            throw new AssertionError("Expected Text but got " + value);
        }
        return (Text) value;
    }

    private static void assertContains(String value, String expected) {
        if (value == null || !value.contains(expected)) {
            throw new AssertionError("Expected <" + value + "> to contain <" + expected + ">");
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but got <" + actual + ">");
        }
    }
}

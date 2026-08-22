package org.universaltranslator.fabric;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.object.PlayerTextObjectContents;
import net.minecraft.text.object.AtlasTextObjectContents;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import org.universaltranslator.core.InlineTextureCode;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationCoordinator;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;
import org.universaltranslator.core.TranslationResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class StyledChatTextSelfTest {
    private StyledChatTextSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        preservesUrlAttachment();
        preservesCommandAttachments();
        translatesPlayerChatWithLowercaseTokens();
        translatesPlayerChatWithInlinePlayerObject();
        prioritizesInteractionOverColorSpans();
        preservesLongInteractiveInlineObjects();
        preventsSpriteFontFromLeakingIntoText();
        DataPackChatTranslationSelfTest.runAll();
        updatesCompletedTranslationLogEntries();
        System.out.println("StyledChatTextSelfTest: all checks passed");
    }

    private static void preservesUrlAttachment() {
        ClickEvent.OpenUrl click = new ClickEvent.OpenUrl(URI.create("https://example.invalid/pack"));
        HoverEvent.ShowText hover = new HoverEvent.ShowText(Text.literal("Resource Pack"));
        Style linkStyle = Style.EMPTY.withColor(Formatting.YELLOW)
                .withUnderline(true)
                .withClickEvent(click)
                .withHoverEvent(hover);
        Text source = Text.empty()
                .append(Text.literal("Click ").formatted(Formatting.YELLOW))
                .append(Text.literal("[HERE]").setStyle(linkStyle))
                .append(Text.literal(" to install the Apiary Resource Pack!")
                        .formatted(Formatting.YELLOW));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        assertContains(template, "{UT_STYLE_");
        String translated = template
                .replace("Click ", "点击")
                .replace("[HERE]", "[这里]")
                .replace(" to install the Apiary Resource Pack!", "安装 Apiary 资源包！");
        Text rebuilt = requireText(StyledChatText.rebuild(source, translated, Style.EMPTY));
        Style actual = styleFor(rebuilt, "[这里]");
        assertEquals(click, actual.getClickEvent());
        assertEquals(hover, actual.getHoverEvent());
        assertTrue(actual.isUnderlined());
        assertEquals(linkStyle.getColor(), actual.getColor());
    }

    private static void preservesCommandAttachments() {
        ClickEvent.RunCommand easy = new ClickEvent.RunCommand("/trigger shanger.settings set 101");
        ClickEvent.RunCommand normal = new ClickEvent.RunCommand("/trigger shanger.settings set 102");
        ClickEvent.RunCommand hard = new ClickEvent.RunCommand("/trigger shanger.settings set 103");
        ClickEvent.RunCommand on = new ClickEvent.RunCommand("/trigger shanger.settings set 104");
        ClickEvent.RunCommand off = new ClickEvent.RunCommand("/trigger shanger.settings set 105");

        MutableText source = Text.empty()
                .append(Text.literal("Difficulty: ").formatted(Formatting.AQUA))
                .append(button("Easy", Formatting.GREEN, easy))
                .append(Text.literal(" "))
                .append(button("Normal", Formatting.YELLOW, normal))
                .append(Text.literal(" "))
                .append(button("Hard", Formatting.RED, hard))
                .append(Text.literal("\nPlayer Glowing: ").formatted(Formatting.AQUA))
                .append(button("ON", Formatting.GREEN, on))
                .append(Text.literal(" "))
                .append(button("OFF", Formatting.RED, off));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        assertContains(template, "{UT_STYLE_");
        String translated = template
                .replace("Difficulty: ", "难度：")
                .replace("Easy", "简单")
                .replace("Normal", "普通")
                .replace("Hard", "困难")
                .replace("Player Glowing: ", "玩家发光：")
                .replace("ON", "开")
                .replace("OFF", "关");
        Text rebuilt = requireText(StyledChatText.rebuild(source, translated, Style.EMPTY));

        assertEquals(easy, styleFor(rebuilt, "简单").getClickEvent());
        assertEquals(normal, styleFor(rebuilt, "普通").getClickEvent());
        assertEquals(hard, styleFor(rebuilt, "困难").getClickEvent());
        assertEquals(on, styleFor(rebuilt, "开").getClickEvent());
        assertEquals(off, styleFor(rebuilt, "关").getClickEvent());
    }

    private static void translatesPlayerChatWithLowercaseTokens() throws Exception {
        String sentence = "When alive, one should not fear; when dying, one should not be timid.";
        ClickEvent.SuggestCommand suggest = new ClickEvent.SuggestCommand("/msg Chengai77a6b ");
        Style playerStyle = Style.EMPTY.withColor(Formatting.WHITE)
                .withClickEvent(suggest)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Chengai77a6b")));
        Text source = Text.empty()
                .append(Text.literal("<"))
                .append(Text.literal("Chengai77a6b").setStyle(playerStyle))
                .append(Text.literal("> " + sentence));
        String template = StyledChatText.translationInput(source, Style.EMPTY);

        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "lowercase-token-chat-component-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                return request.getText()
                        .replace("__UT_", "__ut_")
                        .replace(sentence, "活着时不应恐惧；临死时不应胆怯。");
            }
        };
        String translated;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.CHAT,
                    java.util.Arrays.asList("Chengai77a6b"), true)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            translated = result.getTranslatedText();
        }

        Text rebuilt = requireText(StyledChatText.rebuild(source, translated, Style.EMPTY));
        assertEquals("<Chengai77a6b> 活着时不应恐惧；临死时不应胆怯。", rebuilt.getString());
        assertEquals(suggest, styleFor(rebuilt, "Chengai77a6b").getClickEvent());
    }

    private static void translatesPlayerChatWithInlinePlayerObject() throws Exception {
        String sentence = "Never met before, yet instantly captivated at first sight.";
        String translatedSentence = "素未谋面，却在第一眼便被深深吸引。";
        ClickEvent.SuggestCommand suggest =
                new ClickEvent.SuggestCommand("/msg Chengai77a6b ");
        Style playerStyle = Style.EMPTY.withColor(Formatting.WHITE)
                .withClickEvent(suggest)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Chengai77a6b")));
        Text source = Text.empty()
                .append(playerObject(suggest))
                .append(Text.literal(" <"))
                .append(Text.literal("Chengai77a6b").setStyle(playerStyle))
                .append(Text.literal("> " + sentence));
        String template = StyledChatText.translationInput(source, Style.EMPTY);

        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "inline-player-object-chat-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                assertTrue(!InlineTextureCode.matcher(request.getText()).find());
                return request.getText().replace(sentence, translatedSentence);
            }
        };
        String translated;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.CHAT,
                    java.util.Arrays.asList("Chengai77a6b"), true)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertTrue(!result.isFailure());
            translated = result.getTranslatedText();
        }

        Text styled = requireText(StyledChatText.rebuild(source, translated, Style.EMPTY));
        Text rebuilt = InlineTextureText.restore(source, styled, Style.EMPTY);
        assertTrue(rebuilt != null);
        assertContains(rebuilt.getString(), translatedSentence);
        assertEquals(suggest, styleFor(rebuilt, "Chengai77a6b").getClickEvent());
        assertEquals(StyleSpriteSource.DEFAULT,
                styleFor(rebuilt, translatedSentence).getFont());
        List<ClickEvent> clicks = objectClicks(rebuilt);
        assertEquals(Integer.valueOf(1), Integer.valueOf(clicks.size()));
        assertEquals(suggest, clicks.get(0));
    }

    private static void prioritizesInteractionOverColorSpans() {
        MutableText source = Text.empty();
        for (int index = 0; index < 70; index++) {
            Formatting color = index % 2 == 0 ? Formatting.YELLOW : Formatting.AQUA;
            source.append(Text.literal("part" + index + " ").setStyle(
                    Style.EMPTY.withColor(color).withUnderline(true)));
        }
        ClickEvent.RunCommand click = new ClickEvent.RunCommand("/trigger test set 1");
        source.append(button("CLICK", Formatting.GREEN, click));

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        assertContains(template, "{UT_STYLE_");
        Text rebuilt = requireText(StyledChatText.rebuild(
                source,
                template.replace("CLICK", "点击"),
                Style.EMPTY));
        assertEquals(click, styleFor(rebuilt, "点击").getClickEvent());
    }

    private static void preservesLongInteractiveInlineObjects() throws Exception {
        MutableText source = Text.empty()
                .append(Text.literal("Settings\n\n").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("Difficulty: ").formatted(Formatting.AQUA))
                .append(button("Easy", Formatting.GREEN,
                        new ClickEvent.RunCommand("/trigger settings set 101")))
                .append(Text.literal(" "))
                .append(button("Normal", Formatting.YELLOW,
                        new ClickEvent.RunCommand("/trigger settings set 102")))
                .append(Text.literal(" "))
                .append(button("Hard", Formatting.RED,
                        new ClickEvent.RunCommand("/trigger settings set 103")))
                .append(Text.literal("\n\nPlayer Glowing: ").formatted(Formatting.AQUA))
                .append(button("ON", Formatting.WHITE,
                        new ClickEvent.RunCommand("/trigger settings set 104")))
                .append(Text.literal(" "))
                .append(button("OFF", Formatting.WHITE,
                        new ClickEvent.RunCommand("/trigger settings set 105")))
                .append(Text.literal("\n\n"));

        List<ClickEvent> expectedObjectClicks = new ArrayList<ClickEvent>();
        List<ClickEvent> expectedClearClicks = new ArrayList<ClickEvent>();
        appendObjectRow(source, "Sidebar", 106, expectedObjectClicks, expectedClearClicks);
        appendObjectRow(source, "Below Names", 113, expectedObjectClicks, expectedClearClicks);
        appendObjectRow(source, "Player List", 120, expectedObjectClicks, expectedClearClicks);
        List<Integer> expectedObjectsByLine = objectCountsByLine(source);

        String template = StyledChatText.translationInput(source, Style.EMPTY);
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "styled-chat-component-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                assertTrue(text.contains("__UT_"));
                assertTrue(!InlineTextureCode.matcher(text).find());
                return text.replace("Settings", "设置")
                        .replace("Difficulty", "难度")
                        .replace("Easy", "简单")
                        .replace("Normal", "普通")
                        .replace("Hard", "困难")
                        .replace("Player Glowing", "玩家发光")
                        .replace("ON", "开")
                        .replace("OFF", "关")
                        .replace("Sidebar", "侧边栏")
                        .replace("Below Names", "名字下方")
                        .replace("Player List", "玩家列表");
            }
        };
        String translated;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            translated = result.getTranslatedText();
        }
        assertTrue(!translated.contains("__UT_"));
        Text styled = requireText(StyledChatText.rebuild(source, translated, Style.EMPTY));
        Text rebuilt = InlineTextureText.restore(source, styled, Style.EMPTY);
        assertTrue(rebuilt != null);
        assertContains(rebuilt.getString(), "设置");
        assertContains(rebuilt.getString(), "玩家列表");

        List<ClickEvent> actualObjectClicks = objectClicks(rebuilt);
        assertEquals(Integer.valueOf(expectedObjectClicks.size()),
                Integer.valueOf(actualObjectClicks.size()));
        for (int index = 0; index < expectedObjectClicks.size(); index++) {
            assertEquals(expectedObjectClicks.get(index), actualObjectClicks.get(index));
        }
        for (ClickEvent click : expectedClearClicks) {
            assertTrue(hasTextClick(rebuilt, click));
        }
        assertEquals(expectedObjectsByLine, objectCountsByLine(rebuilt));
        assertEquals(Integer.valueOf(3), Integer.valueOf(playerObjectCount(rebuilt)));
    }

    private static void preventsSpriteFontFromLeakingIntoText() {
        ClickEvent.RunCommand click = new ClickEvent.RunCommand("/trigger texture_font_test");
        Text source = Text.empty()
                .append(inlineObject("item/diamond_pickaxe", "items", click))
                .append(Text.literal("Your attack cooldown does not reset"));
        String original = InlineTextureText.semanticText(source, Style.EMPTY);
        java.util.regex.Matcher marker = InlineTextureCode.matcher(original);
        assertTrue(marker.find());

        Style spriteStyle = null;
        for (InlineTextureText.StyledPart part :
                InlineTextureText.styledParts(source.asOrderedText())) {
            if (InlineTextureCode.matcher(part.text()).find()) {
                spriteStyle = part.style();
                break;
            }
        }
        assertTrue(spriteStyle != null);
        assertTrue(!StyleSpriteSource.DEFAULT.equals(spriteStyle.getFont()));

        Text leaked = Text.literal(marker.group() + "你的攻击冷却不会重置")
                .setStyle(spriteStyle);
        Text rebuilt = InlineTextureText.restore(
                source, original, leaked, Style.EMPTY);
        assertTrue(rebuilt != null);
        assertEquals(StyleSpriteSource.DEFAULT,
                styleFor(rebuilt, "你的攻击冷却不会重置").getFont());
        List<ClickEvent> clicks = objectClicks(rebuilt);
        assertEquals(Integer.valueOf(1), Integer.valueOf(clicks.size()));
        assertEquals(click, clicks.get(0));
    }

    private static void updatesCompletedTranslationLogEntries() {
        TranslationLog.clear();
        TranslationLog.add("First\nSecond", "First Second");
        TranslationLog.add("First\nSecond", "第一行\n第二行");
        List<TranslationLog.Entry> entries = TranslationLog.entries();
        assertEquals(Integer.valueOf(1), Integer.valueOf(entries.size()));
        assertEquals("第一行\n第二行", entries.get(0).translated);

        TranslationLog.add("First\nSecond", "第一行\n第二行");
        assertEquals(Integer.valueOf(1), Integer.valueOf(TranslationLog.entries().size()));
        TranslationLog.clear();
    }

    private static void appendObjectRow(
            MutableText output,
            String label,
            int firstCommand,
            List<ClickEvent> expectedObjectClicks,
            List<ClickEvent> expectedClearClicks
    ) {
        String[] sprites = {
                "hud/heart/full",
                "item/skeleton_skull",
                "item/totem_of_undying",
                "item/diamond_pickaxe",
                "item/iron_sword",
                "block/torch"
        };
        for (int index = 0; index < sprites.length; index++) {
            ClickEvent click = new ClickEvent.RunCommand(
                    "/trigger settings set " + (firstCommand + index));
            expectedObjectClicks.add(click);
            output.append(index == 1
                    ? playerObject(click)
                    : inlineObject(sprites[index], atlasFor(index), click))
                    .append(Text.literal(" "));
        }
        ClickEvent clear = new ClickEvent.RunCommand(
                "/trigger settings set " + (firstCommand + sprites.length));
        expectedClearClicks.add(clear);
        output.append(Text.literal("X").setStyle(Style.EMPTY
                        .withColor(Formatting.GRAY)
                        .withClickEvent(clear)
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Clear " + label)))))
                .append(Text.literal(" - " + label + "\n\n").formatted(Formatting.AQUA));
    }

    private static Text inlineObject(String sprite, String atlas, ClickEvent click) {
        AtlasTextObjectContents contents = new AtlasTextObjectContents(
                Identifier.ofVanilla(atlas), Identifier.ofVanilla(sprite));
        return Text.object(contents).setStyle(Style.EMPTY
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(sprite))));
    }

    private static String atlasFor(int index) {
        if (index == 0) {
            return "gui";
        }
        return index == 5 ? "blocks" : "items";
    }

    private static Text playerObject(ClickEvent click) {
        PlayerTextObjectContents contents = new PlayerTextObjectContents(
                ProfileComponent.ofDynamic(
                        UUID.fromString("a3f427a8-18c5-49c5-a4fb-64c6e0e1e0a8")),
                false);
        return Text.object(contents).setStyle(Style.EMPTY
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Display Deaths"))));
    }

    private static List<ClickEvent> objectClicks(Text text) {
        List<ClickEvent> clicks = new ArrayList<ClickEvent>();
        text.asOrderedText().accept((index, style, codePoint) -> {
            if (codePoint == 0xFFFC) {
                clicks.add(style.getClickEvent());
            }
            return true;
        });
        return clicks;
    }

    private static List<Integer> objectCountsByLine(Text text) {
        List<Integer> counts = new ArrayList<Integer>();
        counts.add(Integer.valueOf(0));
        text.asOrderedText().accept((index, style, codePoint) -> {
            if (codePoint == '\n') {
                counts.add(Integer.valueOf(0));
            } else if (codePoint == 0xFFFC) {
                int last = counts.size() - 1;
                counts.set(last, Integer.valueOf(counts.get(last).intValue() + 1));
            }
            return true;
        });
        return counts;
    }

    private static int playerObjectCount(Text text) {
        final int[] count = new int[1];
        text.asOrderedText().accept((index, style, codePoint) -> {
            if (codePoint == 0xFFFC && style.getFont() instanceof StyleSpriteSource.Player) {
                count[0]++;
            }
            return true;
        });
        return count[0];
    }

    private static boolean hasTextClick(Text text, ClickEvent expected) {
        Optional<Boolean> found = text.visit((style, value) -> {
            if (!value.trim().isEmpty() && expected.equals(style.getClickEvent())) {
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found.orElse(Boolean.FALSE).booleanValue();
    }

    private static Text button(String label, Formatting color, ClickEvent click) {
        return Text.literal(label).setStyle(Style.EMPTY.withColor(color)
                .withUnderline(true)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(label))));
    }

    private static Text requireText(StringVisitable value) {
        if (!(value instanceof Text)) {
            throw new AssertionError("Expected rebuilt Text");
        }
        return (Text) value;
    }

    private static Style styleFor(Text text, String expectedText) {
        final Style[] result = new Style[1];
        text.visit((style, value) -> {
            if (value.contains(expectedText)) {
                result[0] = style;
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (result[0] == null) {
            throw new AssertionError("Missing styled text: " + expectedText + " in " + text.getString());
        }
        return result[0];
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
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }
}

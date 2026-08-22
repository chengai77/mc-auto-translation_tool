package org.universaltranslator.fabric;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.object.AtlasTextObjectContents;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.core.TranslationCache;
import org.universaltranslator.core.TranslationCoordinator;
import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;
import org.universaltranslator.core.TranslationResult;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 数据包聊天自检 */
final class DataPackChatTranslationSelfTest {
    private static final String INFO_BODY =
            "\n\nWelcome to 24-Hour Miracle: Apiary, a CTM Map that was\n"
                    + "built in 24 Hours on March 27, 2026! These events last for 24 hours to\n"
                    + "make for a more time-zone friendly experience and to create a better product!\n\n"
                    + "This map is open world, has 28 objectives, random loot, custom mobs,\n"
                    + "custom recipes, enchantments, currency, and more!";
    private static final String CONTRIBUTOR_ROWS =
            "Gizmo  CooleyBrekka  Sequex  Enigma  Vladomeme\n"
                    + "SlimeKing77777  bigMoma89  bone_ruck  Enderayo  Jakkaboi\n"
                    + "saihoku_hiroppi  Fennekin2305  Torix  Chuck3n  sw3aterCS\n"
                    + "Pizzadog  Gmans97  catgirltris_  Quillver  Everett\n"
                    + "Netheferious  Link496  Sandurs  Pruneau  Caecilleus\n"
                    + "safeliquids  Nebuletta  vertagen  1954PinaColada  Riletty\n"
                    + "Cake  Lylac  TheOnlyMann  DINOCREATOR  TyAngry\n"
                    + "SuperKirbyMaster  GeoCobra  Matt_97  Diabolical_Saint  SealedTugBoat23\n"
                    + "ImSheol  Hectopascal  Suso";

    private DataPackChatTranslationSelfTest() {
    }

    static void runAll() throws Exception {
        translatesSpawnerMilestoneRowsWithInlineObjects();
        translatesLongInfoAsWholeMessage();
        preservesContributorNamesAndLayout();
    }

    private static void translatesSpawnerMilestoneRowsWithInlineObjects() throws Exception {
        Text[] messages = {
                Text.empty()
                        .append(Text.literal(" "))
                        .append(sprite("block/spawner", "blocks"))
                        .append(Text.literal(" Spawner Milestones Unlocked: \n")
                                .formatted(Formatting.YELLOW, Formatting.BOLD)),
                Text.empty()
                        .append(Text.literal("\n "))
                        .append(sprite("item/diamond_pickaxe", "items"))
                        .append(Text.literal(" Total Spawners Mined: ")
                                .formatted(Formatting.AQUA))
                        .append(Text.literal("9").formatted(Formatting.WHITE)),
                Text.empty()
                        .append(Text.literal(" "))
                        .append(sprite("item/copper_pickaxe", "items"))
                        .append(Text.literal(" Next Milestone: ")
                                .formatted(Formatting.GOLD))
                        .append(Text.literal("25").formatted(Formatting.WHITE))
        };
        String[] expected = {
                "刷怪笼里程碑已解锁",
                "总计挖掘刷怪笼",
                "下一个里程碑"
        };
        AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "datapack-inline-object-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                assertTrue(!org.universaltranslator.core.InlineTextureCode
                        .matcher(request.getText()).find());
                return request.getText()
                        .replace("Spawner Milestones Unlocked", "刷怪笼里程碑已解锁")
                        .replace("Total Spawners Mined", "总计挖掘刷怪笼")
                        .replace("Next Milestone", "下一个里程碑");
            }
        };

        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            for (int index = 0; index < messages.length; index++) {
                Text source = messages[index];
                String template = StyledChatText.translationInput(source, Style.EMPTY);
                TranslationResult result = coordinator.translate(
                        template, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                        .get(2, TimeUnit.SECONDS);
                assertTranslated(result);
                assertTrue(!result.isFailure());
                Text styled = requireText(
                        StyledChatText.rebuild(source, result.getTranslatedText(), Style.EMPTY));
                Text rebuilt = InlineTextureText.restore(source, styled, Style.EMPTY);
                assertTrue(rebuilt != null);
                assertContains(rebuilt.getString(), expected[index]);
                assertEquals(Integer.valueOf(1), Integer.valueOf(objectCount(rebuilt)));
            }
        }
        assertEquals(Integer.valueOf(messages.length), Integer.valueOf(calls.get()));
    }

    private static void translatesLongInfoAsWholeMessage() throws Exception {
        MutableText source = Text.empty()
                .append(Text.literal("----------------------").formatted(Formatting.GOLD))
                .append(Text.literal("\nInfo").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(INFO_BODY).formatted(Formatting.WHITE))
                .append(Text.literal("\n\n----------------------").formatted(Formatting.GOLD));
        String template = StyledChatText.translationInput(source, Style.EMPTY);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> sent = new AtomicReference<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "datapack-long-info-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                sent.set(request.getText());
                return request.getText()
                        .replace("Info", "信息")
                        .replace("Welcome to", "欢迎来到")
                        .replace("This map is open world", "这张地图是开放世界")
                        .replace("objectives", "个目标")
                        .replace("random loot", "随机战利品")
                        .replace("custom mobs", "自定义生物")
                        .replace("custom recipes", "自定义配方")
                        .replace("enchantments", "附魔")
                        .replace("currency", "货币")
                        .replace("and more", "以及更多内容")
                        .replace("\r", "")
                        .replace("\n", " ");
            }
        };

        String translated;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(2, TimeUnit.SECONDS);
            assertTranslated(result);
            assertTrue(!result.isFailure());
            translated = result.getTranslatedText();
        }

        assertEquals(Integer.valueOf(1), Integer.valueOf(calls.get()));
        assertContains(sent.get(), "----------------------");
        assertContains(sent.get(), "Welcome to");
        assertContains(sent.get(), "This map is open world");
        assertTrue(!translated.contains("__UT_"));
        Text rebuilt = requireText(
                StyledChatText.rebuild(source, translated, Style.EMPTY));
        String visible = rebuilt.getString();
        assertContains(visible, "信息");
        assertContains(visible, "欢迎来到");
        assertContains(visible, "24");
        assertContains(visible, "27");
        assertContains(visible, "2026");
        assertContains(visible, "28");
        assertTrue(lineCount(visible) < lineCount(source.getString()));
    }

    private static void preservesContributorNamesAndLayout() throws Exception {
        MutableText source = Text.empty()
                .append(Text.literal("----------------------").formatted(Formatting.GOLD))
                .append(Text.literal("\n\nCompilers: KVT & Mowse")
                        .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("\n\nMap Contributors:")
                        .formatted(Formatting.YELLOW, Formatting.UNDERLINE))
                .append(Text.literal("\n\n" + CONTRIBUTOR_ROWS).formatted(Formatting.WHITE))
                .append(Text.literal("\n\n----------------------").formatted(Formatting.GOLD));
        String template = StyledChatText.translationInput(source, Style.EMPTY);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> sent = new AtomicReference<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "datapack-contributors-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                sent.set(request.getText());
                return request.getText()
                        .replace("Compilers", "编译者")
                        .replace("Map Contributors", "地图贡献者");
            }
        };

        String translated;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(2, TimeUnit.SECONDS);
            assertTranslated(result);
            assertTrue(!result.isFailure());
            translated = result.getTranslatedText();
        }

        assertEquals(Integer.valueOf(1), Integer.valueOf(calls.get()));
        assertContains(sent.get(), "----------------------");
        assertTrue(!translated.contains("__UT_"));
        Text rebuilt = requireText(
                StyledChatText.rebuild(source, translated, Style.EMPTY));
        String visible = rebuilt.getString();
        assertContains(visible, "编译者: KVT & Mowse");
        assertContains(visible, "地图贡献者:");
        assertContains(visible, CONTRIBUTOR_ROWS);
        assertEquals(Integer.valueOf(lineCount(source.getString())),
                Integer.valueOf(lineCount(visible)));
    }

    private static int lineCount(String text) {
        int count = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static Text sprite(String sprite, String atlas) {
        return Text.object(new AtlasTextObjectContents(
                Identifier.ofVanilla(atlas), Identifier.ofVanilla(sprite)));
    }

    private static int objectCount(Text text) {
        final int[] count = new int[1];
        text.asOrderedText().accept((index, style, codePoint) -> {
            if (codePoint == 0xFFFC) {
                count[0]++;
            }
            return true;
        });
        return count[0];
    }

    private static Text requireText(Object value) {
        if (!(value instanceof Text)) {
            throw new AssertionError("Expected rebuilt Text");
        }
        return (Text) value;
    }

    private static void assertContains(String value, String expected) {
        if (value == null || !value.contains(expected)) {
            throw new AssertionError(
                    "Expected <" + value + "> to contain <" + expected + ">");
        }
    }

    private static void assertTranslated(TranslationResult result) {
        if (result == null || !result.isTranslated()) {
            throw new AssertionError("Expected translated result: "
                    + (result == null ? "null" : result.getErrorMessage()));
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">");
        }
    }
}

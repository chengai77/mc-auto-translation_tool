package org.universaltranslator.core;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 行内贴图自检 */
final class InlineTextureCodeSelfTest {
    private InlineTextureCodeSelfTest() {
    }

    static void runAll() throws Exception {
        restoresTrimmedBoundaryLines();
        restoresTexturesInsideStyledRanges();
    }

    private static void restoresTrimmedBoundaryLines() throws Exception {
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "inline-texture-boundary-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                return request.getText()
                        .replace("Total Spawners Mined", "总计挖掘刷怪笼")
                        .replace("Spawner Milestones Unlocked", "已解锁刷怪笼里程碑")
                        .trim();
            }
        };

        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            String leading = "\n [item/diamond_pickaxe@items] Total Spawners Mined: 8";
            TranslationResult leadingResult = coordinator.translate(
                    leading, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(2, TimeUnit.SECONDS);
            assertTranslated(leadingResult);
            List<String> leadingLines = VisualTextBoundaries.splitLines(
                    leadingResult.getTranslatedText());
            assertEquals(2, leadingLines.size());
            assertEquals("", leadingLines.get(0));
            assertTrue(leadingLines.get(1).contains(
                    "[item/diamond_pickaxe@items]"));
            assertEquals(1, countOccurrences(
                    leadingResult.getTranslatedText(),
                    "[item/diamond_pickaxe@items]"));

            String trailing =
                    " [block/spawner] Spawner Milestones Unlocked: \n";
            TranslationResult trailingResult = coordinator.translate(
                    trailing, "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(2, TimeUnit.SECONDS);
            assertTranslated(trailingResult);
            List<String> trailingLines = VisualTextBoundaries.splitLines(
                    trailingResult.getTranslatedText());
            assertEquals(2, trailingLines.size());
            assertTrue(trailingLines.get(0).contains("[block/spawner]"));
            assertEquals("", trailingLines.get(1));
            assertEquals(1, countOccurrences(
                    trailingResult.getTranslatedText(), "[block/spawner]"));
        }
    }

    private static void restoresTexturesInsideStyledRanges() {
        String texture = "[item/diamond_pickaxe@items]";
        String objectOnlySource = "{UT_STYLE_0_START}" + texture
                + "{UT_STYLE_0_END}Your attack cooldown does not reset";
        String objectOnlyOutput = "{UT_STYLE_0_START}{UT_STYLE_0_END}"
                + "\u4f60\u7684\u653b\u51fb\u51b7\u5374\u4e0d\u4f1a\u91cd\u7f6e";
        assertEquals("{UT_STYLE_0_START}" + texture + "{UT_STYLE_0_END}"
                        + "\u4f60\u7684\u653b\u51fb\u51b7\u5374\u4e0d\u4f1a\u91cd\u7f6e",
                InlineTextureCode.reanchor(objectOnlySource, objectOnlyOutput));

        String labeledSource = "{UT_STYLE_1_START}" + texture
                + "Attack{UT_STYLE_1_END}";
        String labeledOutput =
                "{UT_STYLE_1_START}\u653b\u51fb{UT_STYLE_1_END}";
        assertEquals("{UT_STYLE_1_START}" + texture
                        + "\u653b\u51fb{UT_STYLE_1_END}",
                InlineTextureCode.reanchor(labeledSource, labeledOutput));
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(value, cursor)) >= 0) {
            count++;
            cursor += value.length();
        }
        return count;
    }

    private static void assertTranslated(TranslationResult result) {
        if (result == null || !result.isTranslated() || result.isFailure()) {
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

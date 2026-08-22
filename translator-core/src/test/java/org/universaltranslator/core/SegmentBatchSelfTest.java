package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 分段批次自检 */
final class SegmentBatchSelfTest {
    private SegmentBatchSelfTest() {
    }

    static void runAll() throws Exception {
        routesNumericStyledMessagesAsStructured();
        acceptsCollapsedBatchLines();
        fallsBackWhenBatchMarkersAreDamaged();
    }

    private static void routesNumericStyledMessagesAsStructured() {
        StringBuilder source = new StringBuilder();
        List<StyledTranslationTemplate.Span> spans =
                new ArrayList<StyledTranslationTemplate.Span>();
        appendSpan(source, spans, "----------------------");
        appendSpan(source, spans, "\nInfo");
        appendSpan(source, spans,
                "\n\nWelcome to 24-Hour Miracle: Apiary, a CTM Map that was\n"
                        + "built in 24 Hours on March 27, 2026! These events last for 24 hours to\n"
                        + "make for a more time-zone friendly experience and to create a better product!\n\n"
                        + "This map is open world, has 28 objectives, random loot, custom mobs,\n"
                        + "custom recipes, enchantments, currency, and more!");
        appendSpan(source, spans, "\n\n----------------------");

        String template = StyledTranslationTemplate.decorate(source.toString(), spans);
        ProtectedText protectedText = ProtectedText.parse(template);
        assertEquals(14, protectedText.getValues().size());
        assertFalse(StructuredTemplateTranslator.requiresLocalChatSegmentation(
                protectedText, TextKind.SYSTEM_MESSAGE));
        assertTrue(StructuredTemplateTranslator.shouldUse(
                protectedText, TextKind.SYSTEM_MESSAGE));
    }

    private static void acceptsCollapsedBatchLines() {
        SegmentBatchProtocol.Batch batch = SegmentBatchProtocol.encode(
                Arrays.asList("First line", "Second line", "Third line"));
        List<String> translated = batch.decode(
                "__ut_0__第一行__ut_1__ __ut_2__第二行__ut_3__"
                        + " __ut_4__第三行__ut_5__",
                "zh-CN");
        assertEquals(Arrays.asList("第一行", "第二行", "第三行"), translated);
    }

    private static void fallsBackWhenBatchMarkersAreDamaged() throws Exception {
        AtomicInteger batchCalls = new AtomicInteger();
        AtomicInteger singleCalls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "segment-batch-marker-fallback-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                if (text.contains("__UT_")) {
                    batchCalls.incrementAndGet();
                    return text.replaceFirst("__UT_\\d+__", "");
                }
                singleCalls.incrementAndGet();
                return text.replace("Alpha", "甲")
                        .replace("Beta", "乙")
                        .replace("Gamma", "丙");
            }
        };

        String translated = SegmentBatchTranslator.translate(
                ProtectedText.parse("Alpha\nBeta\nGamma"),
                "auto", "zh-CN", TextKind.SYSTEM_MESSAGE,
                provider, new TranslationCache(16),
                new RecentTranslationContext(), "segment-batch-test-v1");
        assertEquals("甲\n乙\n丙", translated);
        assertEquals(2, batchCalls.get());
        assertEquals(3, singleCalls.get());
    }

    private static void appendSpan(
            StringBuilder output,
            List<StyledTranslationTemplate.Span> spans,
            String text
    ) {
        int start = output.length();
        output.append(text);
        spans.add(StyledTranslationTemplate.span(
                spans.size(), start, output.length()));
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    "Expected <" + expected + "> but was <" + actual + ">");
        }
    }
}

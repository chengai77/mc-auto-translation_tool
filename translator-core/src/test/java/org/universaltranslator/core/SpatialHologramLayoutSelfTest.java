package org.universaltranslator.core;

import java.util.Arrays;
import java.util.List;

/** 空间全息分行自检 */
final class SpatialHologramLayoutSelfTest {
    private SpatialHologramLayoutSelfTest() {
    }

    static void runAll() {
        groupsSplitSentenceByVisualPosition();
        keepsSingleInteractiveLabelSeparate();
        acceptsNonLatinTranslationSources();
    }

    private static void groupsSplitSentenceByVisualPosition() {
        List<SpatialHologramLayout.Fragment> fragments = Arrays.asList(
                fragment(1, "Or", -1.6875F, -0.8125F, 11),
                fragment(2, "download it", -0.5625F, -0.8125F, 59),
                fragment(3, "by clicking", 1.3125F, -0.8125F, 56));

        List<SpatialHologramLayout.Row> rows = SpatialHologramLayout.rows(fragments);
        assertEquals(1, rows.size());
        SpatialHologramLayout.Row row = rows.get(0);
        assertEquals("Or download it by clicking", row.text());
        assertEquals(2, row.hostId());
        assertEquals(3, row.fragments().size());
        assertTrue(row.spaceBefore(1));
        assertTrue(row.spaceBefore(2));
        assertClose(0.09375F, row.centerX());
    }

    private static void keepsSingleInteractiveLabelSeparate() {
        List<SpatialHologramLayout.Fragment> fragments = Arrays.asList(
                fragment(1, "Or", -1.6875F, -0.8125F, 11),
                fragment(2, "download it", -0.5625F, -0.8125F, 59),
                fragment(3, "by clicking", 1.3125F, -0.8125F, 56),
                fragment(4, "HERE", 0.125F, -2.125F, 27));

        List<SpatialHologramLayout.Row> rows = SpatialHologramLayout.rows(fragments);
        assertEquals(1, rows.size());
        assertEquals("Or download it by clicking", rows.get(0).text());
    }

    private static void acceptsNonLatinTranslationSources() {
        List<SpatialHologramLayout.Row> rows = SpatialHologramLayout.rows(Arrays.asList(
                fragment(1, "点击", -0.5F, 0.0F, 18),
                fragment(2, "这里", 0.5F, 0.0F, 18)));
        assertEquals(1, rows.size());
        assertEquals("点击 这里", rows.get(0).text());
    }

    private static SpatialHologramLayout.Fragment fragment(
            int id, String text, float x, float y, int pixelWidth) {
        return new SpatialHologramLayout.Fragment(
                id, text, x, y, 1.0F, pixelWidth, 0);
    }

    private static void assertClose(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.0001F) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("expected true");
        }
    }
}

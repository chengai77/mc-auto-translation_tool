package org.universaltranslator.core;

/** 全息行宽自检 */
final class HologramLineWidthSelfTest {
    private HologramLineWidthSelfTest() {
    }

    static void runAll() {
        expandsMergedContinuationLine();
        keepsVisibleRowsBounded();
        capsMergedLineExpansion();
        neverShrinksWideLayouts();
    }

    private static void expandsMergedContinuationLine() {
        String source = "[item/diamond_pickaxe@items]Your attack cooldown does not reset\n"
                + "when you miss or switch weapons";
        String translated = "[item/diamond_pickaxe@items]"
                + "你的攻击冷却在你未命中或切换武器时不会重置";

        assertEquals(400, HologramLineWidth.layoutLimit(200, source, translated));
        assertEquals(218, HologramLineWidth.resolve(200, 218, source, translated));
    }

    private static void keepsVisibleRowsBounded() {
        String source = "Settings\n[Easy]\n[Hard]";
        String translated = "设置\n[简单]\n[困难]";

        assertEquals(200, HologramLineWidth.layoutLimit(200, source, translated));
        assertEquals(200, HologramLineWidth.resolve(200, 280, source, translated));
    }

    private static void capsMergedLineExpansion() {
        String source = "First half\nsecond half";
        String translated = "合并后的完整长句";

        assertEquals(512, HologramLineWidth.layoutLimit(350, source, translated));
        assertEquals(512, HologramLineWidth.resolve(350, 900, source, translated));
    }

    private static void neverShrinksWideLayouts() {
        String source = "First half\nsecond half";
        String translated = "合并后的完整长句";

        assertEquals(600, HologramLineWidth.layoutLimit(600, source, translated));
        assertEquals(600, HologramLineWidth.resolve(600, 720, source, translated));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }
}

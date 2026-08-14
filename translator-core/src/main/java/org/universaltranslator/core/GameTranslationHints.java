package org.universaltranslator.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Minecraft domain glossary and prompt hints. */
public final class GameTranslationHints {
    public static final String VERSION = "game-hints-v1";

    private static final Map<String, String> ZH_CN_EXACT = exactZhCn();
    private static final Map<String, String> ZH_TW_EXACT = exactZhTw();

    private static final String ZH_CN_GLOSSARY =
            "Minecraft/game glossary: bullet=\u5b50\u5f39, bullets=\u5b50\u5f39, ammo=\u5f39\u836f, "
                    + "gun=\u67aa\u68b0, guns=\u67aa\u68b0, revolver=\u5de6\u8f6e\u624b\u67aa, rifle=\u6b65\u67aa, "
                    + "shotgun=\u9730\u5f39\u67aa, magazine=\u5f39\u5323, clip=\u5f39\u5939, reload=\u88c5\u586b, "
                    + "scope=\u7784\u51c6\u955c, recoil=\u540e\u5750\u529b, projectile=\u6295\u5c04\u7269, "
                    + "block=\u65b9\u5757, item=\u7269\u54c1, entity=\u5b9e\u4f53, mob=\u751f\u7269, "
                    + "spawn=\u751f\u6210/\u5237\u65b0, chunk=\u533a\u5757, biome=\u751f\u7269\u7fa4\u7cfb, "
                    + "overworld=\u4e3b\u4e16\u754c, nether=\u4e0b\u754c, end=\u672b\u5730, redstone=\u7ea2\u77f3, "
                    + "advancement=\u8fdb\u5ea6, statistics=\u7edf\u8ba1\u4fe1\u606f, sign=\u544a\u793a\u724c, "
                    + "book=\u4e66, chest=\u7bb1\u5b50, barrel=\u6728\u6876, hopper=\u6f0f\u6597, "
                    + "shulker box=\u6f5c\u5f71\u76d2, ender chest=\u672b\u5f71\u7bb1, command=\u6307\u4ee4, "
                    + "permission=\u6743\u9650, quest=\u4efb\u52a1, party=\u961f\u4f0d, guild=\u516c\u4f1a, "
                    + "kit=\u5957\u88c5, crate=\u5b9d\u7bb1, loot=\u6218\u5229\u54c1, warp=\u4f20\u9001\u70b9, "
                    + "lobby=\u5927\u5385, server=\u670d\u52a1\u5668, craft=\u5408\u6210, recipe=\u914d\u65b9, "
                    + "durability=\u8010\u4e45, enchantment=\u9644\u9b54, potion=\u836f\u6c34, "
                    + "buff=\u52a0\u5f3a, nerf=\u524a\u5f31, grind=\u5237, farm=\u5237/\u519c\u573a, "
                    + "drop=\u6389\u843d, gg=\u6253\u5f97\u597d, ez=\u7b80\u5355, afk=\u6682\u79bb, "
                    + "brb=\u9a6c\u4e0a\u56de\u6765, lol=\u54c8\u54c8, noob=\u83dc\u9e1f/\u840c\u65b0.";

    private static final String ZH_TW_GLOSSARY =
            "Minecraft/game glossary: bullet=\u5b50\u5f48, ammo=\u5f48\u85e5, gun=\u69cd\u68b0, "
                    + "revolver=\u5de6\u8f2a\u624b\u69cd, projectile=\u6295\u5c04\u7269, block=\u65b9\u584a, "
                    + "item=\u7269\u54c1, entity=\u5be6\u9ad4, mob=\u751f\u7269, chunk=\u5340\u584a, "
                    + "biome=\u751f\u7269\u7fa4\u7cfb, nether=\u4e0b\u754c, end=\u7d42\u754c, "
                    + "advancement=\u9032\u5ea6, sign=\u544a\u793a\u724c, chest=\u7bb1\u5b50, command=\u6307\u4ee4.";

    private GameTranslationHints() {
    }

    public static String exactTranslation(String source, String targetLanguage) {
        TermParts parts = splitTerm(source);
        if (parts.normalized.isEmpty()) {
            return null;
        }
        String translated = null;
        if (TargetLanguage.isSimplifiedChinese(targetLanguage)) {
            translated = ZH_CN_EXACT.get(parts.normalized);
        } else if (TargetLanguage.isTraditionalChinese(targetLanguage)) {
            translated = ZH_TW_EXACT.get(parts.normalized);
        }
        return translated == null ? null : parts.prefix + translated + parts.suffix;
    }

    public static String glossaryFor(String targetLanguage) {
        if (TargetLanguage.isSimplifiedChinese(targetLanguage)) {
            return ZH_CN_GLOSSARY;
        }
        if (TargetLanguage.isTraditionalChinese(targetLanguage)) {
            return ZH_TW_GLOSSARY;
        }
        return "Minecraft/game glossary: prefer game UI terminology, multiplayer slang, "
                + "and in-game item/block/entity names over software/project meanings.";
    }

    public static String openAiInstruction(TranslationRequest request) {
        StringBuilder output = new StringBuilder(768);
        output.append(" Domain: Minecraft and multiplayer game UI/chat. ")
                .append("Resolve ambiguous words as in-game terms first; for example bullet means ammunition, not a project item. ")
                .append("Use common Minecraft names and server slang naturally.");
        appendIfPresent(output, " Preset glossary: ", request.getGlossaryHint());
        appendIfPresent(output, " Recent context from cached translations: ", request.getContextHint());
        return output.toString();
    }

    public static String tencentField(TranslationRequest request) {
        String glossary = request.getGlossaryHint();
        String field = "Minecraft game UI and multiplayer server chat";
        if (glossary == null || glossary.isEmpty()) {
            return field;
        }
        int limit = Math.min(glossary.length(), 220);
        return field + "; " + glossary.substring(0, limit);
    }

    private static void appendIfPresent(StringBuilder output, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            output.append(label).append(value.trim());
        }
    }

    private static TermParts splitTerm(String source) {
        if (source == null) {
            return new TermParts("", "", "");
        }
        String stripped = TranslationTextStyling.stripLegacyFormatting(source)
                .trim()
                .replace('_', ' ')
                .replace('-', ' ');
        int start = 0;
        int end = stripped.length();
        while (start < end && isWrapper(stripped.charAt(start))) {
            start++;
        }
        while (end > start && isWrapper(stripped.charAt(end - 1))) {
            end--;
        }
        String normalized = stripped.substring(start, end)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return new TermParts(stripped.substring(0, start), normalized, stripped.substring(end));
    }

    private static boolean isWrapper(char value) {
        return value == ':' || value == ';' || value == ',' || value == '.'
                || value == '!' || value == '?' || value == '[' || value == ']'
                || value == '(' || value == ')' || value == '"' || value == '\'';
    }

    private static Map<String, String> exactZhCn() {
        Map<String, String> terms = new HashMap<String, String>();
        put(terms, "bullet", "\u5b50\u5f39");
        put(terms, "bullets", "\u5b50\u5f39");
        put(terms, "ammo", "\u5f39\u836f");
        put(terms, "ammunition", "\u5f39\u836f");
        put(terms, "gun", "\u67aa\u68b0");
        put(terms, "guns", "\u67aa\u68b0");
        put(terms, "revolver", "\u5de6\u8f6e\u624b\u67aa");
        put(terms, "projectile", "\u6295\u5c04\u7269");
        put(terms, "projectiles", "\u6295\u5c04\u7269");
        put(terms, "advancement", "\u8fdb\u5ea6");
        put(terms, "advancements", "\u8fdb\u5ea6");
        put(terms, "statistics", "\u7edf\u8ba1\u4fe1\u606f");
        put(terms, "sign", "\u544a\u793a\u724c");
        put(terms, "book", "\u4e66");
        put(terms, "chest", "\u7bb1\u5b50");
        put(terms, "command", "\u6307\u4ee4");
        put(terms, "block", "\u65b9\u5757");
        put(terms, "item", "\u7269\u54c1");
        put(terms, "entity", "\u5b9e\u4f53");
        put(terms, "mob", "\u751f\u7269");
        put(terms, "chunk", "\u533a\u5757");
        put(terms, "biome", "\u751f\u7269\u7fa4\u7cfb");
        put(terms, "nether", "\u4e0b\u754c");
        put(terms, "end", "\u672b\u5730");
        put(terms, "overworld", "\u4e3b\u4e16\u754c");
        put(terms, "redstone", "\u7ea2\u77f3");
        return Collections.unmodifiableMap(terms);
    }

    private static Map<String, String> exactZhTw() {
        Map<String, String> terms = new HashMap<String, String>();
        put(terms, "bullet", "\u5b50\u5f48");
        put(terms, "bullets", "\u5b50\u5f48");
        put(terms, "ammo", "\u5f48\u85e5");
        put(terms, "gun", "\u69cd\u68b0");
        put(terms, "guns", "\u69cd\u68b0");
        put(terms, "revolver", "\u5de6\u8f2a\u624b\u69cd");
        put(terms, "projectile", "\u6295\u5c04\u7269");
        put(terms, "advancement", "\u9032\u5ea6");
        put(terms, "advancements", "\u9032\u5ea6");
        put(terms, "sign", "\u544a\u793a\u724c");
        put(terms, "chest", "\u7bb1\u5b50");
        put(terms, "command", "\u6307\u4ee4");
        put(terms, "block", "\u65b9\u584a");
        put(terms, "item", "\u7269\u54c1");
        put(terms, "entity", "\u5be6\u9ad4");
        put(terms, "nether", "\u4e0b\u754c");
        put(terms, "end", "\u7d42\u754c");
        return Collections.unmodifiableMap(terms);
    }

    private static void put(Map<String, String> terms, String source, String translated) {
        terms.put(source, translated);
    }

    private static final class TermParts {
        private final String prefix;
        private final String normalized;
        private final String suffix;

        private TermParts(String prefix, String normalized, String suffix) {
            this.prefix = prefix;
            this.normalized = normalized;
            this.suffix = suffix;
        }
    }
}

package org.universaltranslator.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 中文数值语法 */
final class LocalizedNumericGrammar {
    private static final char ANCHOR = '\ue000';
    private static final char BLOCKER = '\ue001';
    private static final Pattern TOKEN = Pattern.compile("__UT_(\\d+)__");
    private static final Pattern HIDDEN_DISPLAY = Pattern.compile(
            "(?:\\{UT_(?:STYLE|HOLOGRAM_BLOCK)_\\d+_(?:START|END)\\}"
                    + "|" + InlineTextureCode.REGEX_SOURCE
                    + "|\\u00a7[0-9A-FK-ORa-fk-or])");
    private static final Pattern BAD_COMPLETION_VERB = Pattern.compile(
            "(?:到达|抵达|达到|达成|实现|完成(?:于|於|在)?"
                    + "|達到|抵達|達成|實現)\\s*$");
    private static final String[] EXISTING_CLASSIFIERS = {
            "分钟", "分鐘", "小时", "小時", "毫米", "厘米", "公里", "千米",
            "美元", "欧元", "歐元", "英镑", "英鎊", "完成度", "进度", "進度",
            "百分比", "百分点", "百分點",
            "个", "個", "位", "名", "人", "只", "条", "條", "件", "张", "張",
            "本", "枚", "颗", "顆", "块", "塊", "种", "種", "次", "组", "組",
            "套", "对", "對", "双", "雙", "家", "队", "隊", "类", "類", "项", "項",
            "关", "關", "层", "層", "章", "节", "節", "页", "頁", "格", "点", "點",
            "分", "秒", "时", "時", "日", "天", "周", "月", "年", "米", "码", "碼",
            "尺", "磅", "吨", "噸", "克", "元", "倍", "成", "折", "级", "級",
            "号", "號", "轮", "輪", "波", "阶", "階", "段", "星", "粒", "箱",
            "瓶", "杯", "份", "支", "把", "艘", "辆", "輛", "架", "座", "门", "門",
            "栋", "棟", "间", "間", "头", "頭", "匹", "峰", "片", "面", "所", "部",
            "台", "%", "％"
    };
    private static final String[] MODIFIERS = {
            "不同的", "不同", "额外的", "額外的", "新的", "隐藏的", "隱藏的",
            "可选的", "可選的", "独特的", "獨特的", "特殊的", "随机的", "隨機的",
            "主要的", "完整的", "剩余的", "剩餘的", "其他的", "更多的"
    };
    private static final String[] PERSON_NOUNS = {
            "玩家", "队员", "隊員", "村民", "敌人", "敵人", "角色", "成员", "成員", "选手", "選手"
    };
    private static final String[] FLAT_NOUNS = {
            "地图", "地圖", "图片", "圖片", "照片", "卡片", "纸", "紙", "票", "卷轴", "卷軸"
    };
    private static final String[] BOOK_NOUNS = {
            "书", "書", "手册", "手冊", "日志", "日誌", "笔记", "筆記"
    };
    private static final String[] EVENT_NOUNS = {
            "攻击", "攻擊", "尝试", "嘗試", "机会", "機會", "重试", "重試",
            "死亡", "胜利", "勝利", "失败", "失敗", "回合"
    };
    private static final String[] POINT_NOUNS = {
            "伤害", "傷害", "生命", "血量", "经验", "經驗", "魔力", "法力",
            "护甲", "護甲", "护盾", "護盾", "攻击力", "攻擊力", "防御力", "防禦力", "耐久"
    };
    private static final String[] ITEM_NOUNS = {
            "物品", "装备", "裝備", "武器", "盔甲", "工具", "材料"
    };
    private static final String[] NON_OBJECT_PERCENTAGES = {
            "完成度", "进度", "進度", "生命", "血量", "概率", "機率", "几率", "效率",
            "伤害", "傷害", "速度", "加成", "上限", "状态", "狀態", "数值", "數值",
            "等级", "等級", "值"
    };

    private LocalizedNumericGrammar() {
    }

    static String normalizeTemplate(
            ProtectedText source, String translatedTemplate, String targetLanguage) {
        if (!isChinese(targetLanguage) || source == null
                || translatedTemplate == null || translatedTemplate.isEmpty()) {
            return translatedTemplate;
        }
        String output = translatedTemplate;
        NumericTranslationContext context = NumericTranslationContext.of(source);
        for (NumericTranslationContext.Entry entry : context.entries()) {
            if (entry.likelyCount()) {
                output = normalizeTemplateCount(
                        source, output, entry, targetLanguage);
            }
            if (entry.completionPercentage()) {
                output = normalizeTemplatePercentage(source, output, entry);
            }
        }
        return output;
    }

    static String normalize(String source, String translated, String targetLanguage) {
        if (!isChinese(targetLanguage) || source == null || translated == null
                || translated.isEmpty()) {
            return translated;
        }
        ProtectedText protectedSource = ProtectedText.parse(source);
        NumericTranslationContext context = NumericTranslationContext.of(protectedSource);
        Map<String, Integer> valueCounts = valueCounts(context.entries());
        String output = translated;
        for (NumericTranslationContext.Entry entry : context.entries()) {
            if (valueCounts.get(entry.value()).intValue() != 1) {
                continue;
            }
            if (entry.likelyCount()) {
                output = normalizeDisplayCount(output, entry.value(), targetLanguage);
            }
            if (entry.completionPercentage()) {
                output = normalizeDisplayPercentage(output, entry.value());
            }
        }
        return output;
    }

    private static String normalizeTemplateCount(
            ProtectedText source,
            String translated,
            NumericTranslationContext.Entry entry,
            String targetLanguage
    ) {
        Projection projection = Projection.template(source, translated, entry.token());
        int anchor = uniqueAnchor(projection.text());
        return anchor < 0 ? translated : normalizeCount(
                translated, projection, anchor, anchor + 1, targetLanguage);
    }

    private static String normalizeTemplatePercentage(
            ProtectedText source,
            String translated,
            NumericTranslationContext.Entry entry
    ) {
        Projection projection = Projection.template(source, translated, entry.token());
        int anchor = uniqueAnchor(projection.text());
        return anchor < 0 ? translated : normalizePercentage(
                translated, projection, anchor, anchor + 1);
    }

    private static String normalizeDisplayCount(
            String translated, String value, String targetLanguage) {
        Projection projection = Projection.display(translated);
        Range anchor = uniqueValue(projection.text(), value);
        return anchor == null ? translated : normalizeCount(
                translated, projection, anchor.start, anchor.end, targetLanguage);
    }

    private static String normalizeDisplayPercentage(String translated, String value) {
        Projection projection = Projection.display(translated);
        Range anchor = uniqueValue(projection.text(), value);
        return anchor == null ? translated : normalizePercentage(
                translated, projection, anchor.start, anchor.end);
    }

    private static String normalizeCount(
            String raw,
            Projection projection,
            int numberStart,
            int numberEnd,
            String targetLanguage
    ) {
        String visible = projection.text();
        int previous = previousContent(visible, numberStart);
        if (previous >= 0 && visible.charAt(previous) == '第') {
            return raw;
        }
        int next = nextContent(visible, numberEnd);
        if (next < 0 || !isHan(visible.charAt(next))) {
            return raw;
        }
        String tail = visible.substring(next, Math.min(visible.length(), next + 32));
        if (startsWithAny(tail, EXISTING_CLASSIFIERS)) {
            return raw;
        }
        String classifier = classifierFor(tail, targetLanguage);
        int rawNumberEnd = projection.rawEnd(numberEnd);
        int rawNext = projection.rawStart(next);
        if (rawNumberEnd > rawNext) {
            return raw;
        }
        String between = removeHorizontalWhitespace(
                raw.substring(rawNumberEnd, rawNext));
        return raw.substring(0, rawNumberEnd)
                + classifier + between + raw.substring(rawNext);
    }

    private static String normalizePercentage(
            String raw,
            Projection projection,
            int numberStart,
            int numberEnd
    ) {
        String visible = projection.text();
        int clauseStart = clauseStart(visible, numberStart);
        Matcher verb = BAD_COMPLETION_VERB.matcher(
                visible.substring(clauseStart, numberStart));
        if (!verb.find()) {
            return raw;
        }
        int objectStart = nextContent(visible, numberEnd);
        if (objectStart < 0) {
            return raw;
        }
        String tail = visible.substring(
                objectStart, Math.min(visible.length(), objectStart + 40));
        if (!looksLikeCompletionObject(tail)) {
            return raw;
        }

        int visibleVerbStart = clauseStart + verb.start();
        int visibleVerbEnd = clauseStart + verb.end();
        int rawVerbStart = projection.rawStart(visibleVerbStart);
        int rawVerbEnd = projection.rawEnd(visibleVerbEnd);
        int rawNumberStart = projection.rawStart(numberStart);
        int rawNumberEnd = projection.rawEnd(numberEnd);
        int rawObjectStart = projection.rawStart(objectStart);
        if (rawVerbStart > rawVerbEnd || rawVerbEnd > rawNumberStart
                || rawNumberStart > rawNumberEnd || rawNumberEnd > rawObjectStart) {
            return raw;
        }

        String beforeNumber = removeHorizontalWhitespace(
                raw.substring(rawVerbEnd, rawNumberStart));
        String afterNumber = removeHorizontalWhitespace(
                raw.substring(rawNumberEnd, rawObjectStart));
        boolean alreadyCompleted = tail.startsWith("完成");
        return raw.substring(0, rawVerbStart)
                + "以" + beforeNumber
                + raw.substring(rawNumberStart, rawNumberEnd)
                + afterNumber + (alreadyCompleted ? "" : "完成")
                + raw.substring(rawObjectStart);
    }

    private static boolean looksLikeCompletionObject(String tail) {
        String value = tail.trim();
        if (value.isEmpty() || value.charAt(0) == BLOCKER
                || startsWithAny(value, NON_OBJECT_PERCENTAGES)) {
            return false;
        }
        char first = value.charAt(0);
        return first == '这' || first == '這' || first == '该' || first == '該'
                || first == '本' || first == '此' || first == '整'
                || value.startsWith("完成") || isHan(first);
    }

    private static String classifierFor(String tail, String targetLanguage) {
        String noun = stripModifiers(tail.trim());
        if (startsWithAny(noun, PERSON_NOUNS)) {
            return "名";
        }
        if (startsWithAny(noun, FLAT_NOUNS)) {
            return TargetLanguage.isTraditionalChinese(targetLanguage) ? "張" : "张";
        }
        if (startsWithAny(noun, BOOK_NOUNS)) {
            return "本";
        }
        if (startsWithAny(noun, EVENT_NOUNS)) {
            return "次";
        }
        if (startsWithAny(noun, POINT_NOUNS)) {
            return TargetLanguage.isTraditionalChinese(targetLanguage) ? "點" : "点";
        }
        if (startsWithAny(noun, ITEM_NOUNS)) {
            return "件";
        }
        return TargetLanguage.isTraditionalChinese(targetLanguage) ? "個" : "个";
    }

    private static String stripModifiers(String value) {
        String output = value;
        for (int attempt = 0; attempt < 3; attempt++) {
            boolean changed = false;
            for (String modifier : MODIFIERS) {
                if (output.startsWith(modifier)) {
                    output = output.substring(modifier.length()).trim();
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                break;
            }
        }
        return output;
    }

    private static Map<String, Integer> valueCounts(
            List<NumericTranslationContext.Entry> entries) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (NumericTranslationContext.Entry entry : entries) {
            Integer count = counts.get(entry.value());
            counts.put(entry.value(), count == null ? 1 : count + 1);
        }
        return counts;
    }

    private static Range uniqueValue(String text, String value) {
        int start = text.indexOf(value);
        if (start < 0 || text.indexOf(value, start + value.length()) >= 0
                || !hasNumericBoundaries(text, start, start + value.length())) {
            return null;
        }
        return new Range(start, start + value.length());
    }

    private static boolean hasNumericBoundaries(String text, int start, int end) {
        if (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            return false;
        }
        return end >= text.length() || !Character.isDigit(text.charAt(end));
    }

    private static int uniqueAnchor(String text) {
        int index = text.indexOf(ANCHOR);
        return index >= 0 && text.indexOf(ANCHOR, index + 1) < 0 ? index : -1;
    }

    private static int nextContent(String text, int start) {
        for (int index = start; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r') {
                return -1;
            }
            if (!Character.isWhitespace(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int previousContent(String text, int end) {
        for (int index = end - 1; index >= 0; index--) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r') {
                return -1;
            }
            if (!Character.isWhitespace(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int clauseStart(String text, int end) {
        for (int index = end - 1; index >= 0; index--) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r' || value == '。' || value == '！'
                    || value == '？' || value == '!' || value == '?' || value == '；'
                    || value == ';') {
                return index + 1;
            }
        }
        return 0;
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHan(char value) {
        return Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN;
    }

    private static boolean isChinese(String targetLanguage) {
        return TargetLanguage.isSimplifiedChinese(targetLanguage)
                || TargetLanguage.isTraditionalChinese(targetLanguage);
    }

    private static String removeHorizontalWhitespace(String text) {
        StringBuilder output = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value != ' ' && value != '\t' && value != '\f') {
                output.append(value);
            }
        }
        return output.toString();
    }

    private static final class Projection {
        private final String text;
        private final java.util.List<Integer> rawStarts;
        private final java.util.List<Integer> rawEnds;

        private Projection(
                String text,
                java.util.List<Integer> rawStarts,
                java.util.List<Integer> rawEnds
        ) {
            this.text = text;
            this.rawStarts = rawStarts;
            this.rawEnds = rawEnds;
        }

        static Projection template(
                ProtectedText source, String raw, String anchorToken) {
            StringBuilder visible = new StringBuilder(raw.length());
            java.util.List<Integer> starts = new java.util.ArrayList<Integer>();
            java.util.List<Integer> ends = new java.util.ArrayList<Integer>();
            Matcher matcher = TOKEN.matcher(raw);
            int cursor = 0;
            while (matcher.find()) {
                append(raw, cursor, matcher.start(), visible, starts, ends);
                if (matcher.group().equals(anchorToken)) {
                    append(ANCHOR, matcher.start(), matcher.end(), visible, starts, ends);
                } else {
                    int index = Integer.parseInt(matcher.group(1));
                    String value = index < source.getValues().size()
                            ? source.getValues().get(index) : "";
                    if (!NumericTranslationContext.isFormattingValue(value)) {
                        append(BLOCKER, matcher.start(), matcher.end(), visible, starts, ends);
                    }
                }
                cursor = matcher.end();
            }
            append(raw, cursor, raw.length(), visible, starts, ends);
            return new Projection(visible.toString(), starts, ends);
        }

        static Projection display(String raw) {
            StringBuilder visible = new StringBuilder(raw.length());
            java.util.List<Integer> starts = new java.util.ArrayList<Integer>();
            java.util.List<Integer> ends = new java.util.ArrayList<Integer>();
            Matcher hidden = HIDDEN_DISPLAY.matcher(raw);
            int cursor = 0;
            while (hidden.find()) {
                append(raw, cursor, hidden.start(), visible, starts, ends);
                cursor = hidden.end();
            }
            append(raw, cursor, raw.length(), visible, starts, ends);
            return new Projection(visible.toString(), starts, ends);
        }

        private static void append(
                String source,
                int start,
                int end,
                StringBuilder visible,
                java.util.List<Integer> starts,
                java.util.List<Integer> ends
        ) {
            for (int index = start; index < end; index++) {
                append(source.charAt(index), index, index + 1, visible, starts, ends);
            }
        }

        private static void append(
                char value,
                int rawStart,
                int rawEnd,
                StringBuilder visible,
                java.util.List<Integer> starts,
                java.util.List<Integer> ends
        ) {
            visible.append(value);
            starts.add(Integer.valueOf(rawStart));
            ends.add(Integer.valueOf(rawEnd));
        }

        String text() {
            return text;
        }

        int rawStart(int visibleIndex) {
            return visibleIndex >= rawStarts.size()
                    ? (rawEnds.isEmpty() ? 0 : rawEnds.get(rawEnds.size() - 1).intValue())
                    : rawStarts.get(visibleIndex).intValue();
        }

        int rawEnd(int visibleEnd) {
            return visibleEnd <= 0 ? 0 : rawEnds.get(visibleEnd - 1).intValue();
        }
    }

    private static final class Range {
        private final int start;
        private final int end;

        private Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}

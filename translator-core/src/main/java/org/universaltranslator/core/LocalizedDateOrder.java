package org.universaltranslator.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 中文日期规范化 */
final class LocalizedDateOrder {
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*[，,]\\s*"
                    + "(\\d{4})(?:\\s*年)?(?!\\d)");

    private LocalizedDateOrder() {
    }

    static String normalize(String text, String targetLanguage) {
        if (text == null || text.isEmpty()
                || (!TargetLanguage.isSimplifiedChinese(targetLanguage)
                && !TargetLanguage.isTraditionalChinese(targetLanguage))) {
            return text;
        }
        Matcher matcher = MONTH_DAY_YEAR.matcher(text);
        StringBuffer output = new StringBuffer(text.length());
        while (matcher.find()) {
            String replacement = matcher.group(3) + "年" + matcher.group(1)
                    + "月" + matcher.group(2) + "日";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}

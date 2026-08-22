package org.universaltranslator.core;

import java.util.regex.Pattern;

/** 补全中文句首连接词 */
final class LocalizedLeadingConnector {
    private static final Pattern LEADING_OR = Pattern.compile(
            "(?i)^\\s*or(?:\\s+|(?=[,.;:!?]))");
    private static final Pattern CHINESE_OR = Pattern.compile(
            "^(?:或(?:者|是)?|也(?:可|可以)|亦可|另外)");
    private static final Pattern INTERNAL_MARKER = Pattern.compile(
            "(?:__UT_\\d+__"
                    + "|\\{UT_(?:STYLE|HOLOGRAM_BLOCK)_\\d+_(?:START|END)}"
                    + "|\\u00a7[0-9A-FK-ORa-fk-or])");

    private LocalizedLeadingConnector() {
    }

    static String normalize(String source, String translated, String targetLanguage) {
        if ((!TargetLanguage.isSimplifiedChinese(targetLanguage)
                && !TargetLanguage.isTraditionalChinese(targetLanguage))
                || source == null || translated == null
                || !LEADING_OR.matcher(visible(source)).find()) {
            return translated;
        }
        String visibleOutput = visible(translated).trim();
        if (visibleOutput.isEmpty() || CHINESE_OR.matcher(visibleOutput).find()) {
            return translated;
        }
        int insertion = 0;
        while (insertion < translated.length()
                && Character.isWhitespace(translated.charAt(insertion))) {
            insertion++;
        }
        return translated.substring(0, insertion) + "或" + translated.substring(insertion);
    }

    private static String visible(String text) {
        String output = INTERNAL_MARKER.matcher(text).replaceAll("");
        output = StyledTranslationTemplate.strip(output);
        return InlineTextureCode.strip(output);
    }
}

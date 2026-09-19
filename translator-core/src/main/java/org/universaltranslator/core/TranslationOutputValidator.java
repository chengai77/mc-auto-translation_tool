package org.universaltranslator.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 拦截异常译文 */
public final class TranslationOutputValidator {
    private static final String PROTECTED_TOKEN_FAILURE =
            "Translation output changed protected tokens";
    private static final Pattern TOKEN = Pattern.compile(
            "(?:__\\s*UT\\s*_\\s*(\\d+)\\s*__"
                    + "|\\[\\[\\s*UTP\\s*_\\s*(\\d+)\\s*\\]\\])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANONICAL_TOKEN = Pattern.compile("__UT_(\\d+)__");
    private static final Pattern INTERNAL_MARKER = Pattern.compile(
            "\\{UT_(?:STYLE|HOLOGRAM_BLOCK)_\\d+_(?:START|END)}");
    private static final Pattern UNEXPECTED_BRACKET_PLACEHOLDER = Pattern.compile(
            "\\[\\[[A-Za-z][A-Za-z0-9_.:-]{0,31}\\]\\]");

    private TranslationOutputValidator() {
    }

    public static String requireValid(String source, String translated) {
        if (source == null || translated == null || translated.trim().isEmpty()) {
            throw invalidOutput("Translation output is empty");
        }
        String output = canonicalizeProtectedTokens(unwrapQuotes(translated.trim()));
        int maximumLength = Math.max(48, source.length() * 3 + 24);
        if (output.length() > maximumLength) {
            throw invalidOutput("Translation output is unexpectedly long");
        }
        if (!source.contains("\n") && (output.contains("\n") || output.contains("\r"))) {
            throw invalidOutput("Translation output unexpectedly contains multiple lines");
        }
        TranslationOutputGuard.requireClean(source, output);
        if (!UNEXPECTED_BRACKET_PLACEHOLDER.matcher(source).find()
                && UNEXPECTED_BRACKET_PLACEHOLDER.matcher(output).find()) {
            throw invalidOutput("Translation output introduced a placeholder-like token");
        }
        requireQuestionShape(source, output);
        Map<String, Integer> sourceTokens = tokenCounts(source);
        Map<String, Integer> outputTokens = tokenCounts(output);
        if (!sourceTokens.equals(outputTokens)) {
            throw new ValidationException(
                    PROTECTED_TOKEN_FAILURE + " (expected=" + tokenTotal(sourceTokens)
                            + ", actual=" + tokenTotal(outputTokens) + ")",
                    true);
        }
        return output;
    }

    public static String requireValid(
            String source, String translated, String targetLanguage) {
        String output = requireValid(source, translated);
        requireTargetLanguageShift(source, output, targetLanguage);
        output = LocalizedLeadingConnector.normalize(source, output, targetLanguage);
        return LocalizedClauseOrder.normalize(source, output, targetLanguage);
    }

    /** 入缓存前校验 */
    public static String requireDisplaySafe(String source, String restored) {
        return requireDisplaySafe(source, restored, true);
    }

    /** 校验已完成贴图恢复的文本 */
    static String requireRestoredDisplaySafe(String source, String restored) {
        return requireDisplaySafe(source, restored, false);
    }

    private static String requireDisplaySafe(
            String source, String restored, boolean restoreTexturePositions) {
        if (source == null || restored == null || restored.trim().isEmpty()) {
            throw invalidOutput("Restored translation output is empty");
        }
        String output = restoreTexturePositions ? restored.trim() : restored;
        int maximumLength = Math.max(48, source.length() * 3 + 24);
        if (output.length() > maximumLength) {
            throw invalidOutput("Restored translation output is unexpectedly long");
        }
        if (!source.contains("\n") && (output.contains("\n") || output.contains("\r"))) {
            throw invalidOutput("Restored translation unexpectedly contains multiple lines");
        }
        if (TOKEN.matcher(output).find()) {
            throw invalidOutput("Restored translation leaked an internal placeholder");
        }
        TranslationOutputGuard.requireClean(source, output);
        if (!InlineTextureCode.hasSameSequence(source, output)) {
            throw invalidOutput("Restored translation changed inline texture codes");
        }
        boolean hasInlineTextures = InlineTextureCode.matcher(source).find();
        if (hasInlineTextures && !HologramTextLayout.containsTemplate(source)) {
            if (restoreTexturePositions) {
                output = InlineTextureCode.reanchorByLine(source, output);
                if (output == null) {
                    throw invalidOutput(
                            "Restored translation moved inline texture codes across lines");
                }
            } else if (!InlineTextureCode.hasSameLineSequence(source, output)) {
                throw invalidOutput(
                        "Restored translation moved inline texture codes across lines");
            }
        }
        requireStyledTemplateShape(source, output);
        return output;
    }

    private static void requireStyledTemplateShape(String source, String output) {
        boolean sourceStyled = StyledTranslationTemplate.contains(source);
        if (!sourceStyled) {
            if (StyledTranslationTemplate.contains(output)) {
                throw invalidOutput(
                        "Restored translation introduced styled text markers");
            }
            return;
        }
        int expectedSpans = styledSpanCount(source);
        StyledTranslationTemplate.Parsed sourceTemplate =
                StyledTranslationTemplate.parse(source, expectedSpans);
        StyledTranslationTemplate.Parsed outputTemplate =
                StyledTranslationTemplate.parse(output, expectedSpans);
        if (sourceTemplate == null || outputTemplate == null) {
            throw invalidOutput(
                    "Restored translation changed styled text boundaries");
        }
    }

    private static int styledSpanCount(String text) {
        int count = 0;
        Matcher matcher = StyledTranslationTemplate.matcher(text);
        while (matcher.find()) {
            if ("START".equals(matcher.group(2))) {
                count++;
            }
        }
        return count;
    }

    static IllegalArgumentException protectedTokenFailure(String detail) {
        return new ValidationException(
                PROTECTED_TOKEN_FAILURE + " (" + detail + ")", true);
    }

    static IllegalArgumentException invalidOutput(String message) {
        return new ValidationException(message, false);
    }

    /** 判断译文校验失败 */
    public static boolean isOutputValidationFailure(Throwable failure) {
        if (failure == null) {
            return false;
        }
        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<Throwable, Boolean>());
        return isOutputValidationFailure(failure, visited, 0);
    }

    /** 防指令递归翻译 */
    public static boolean containsInternalArtifact(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return TOKEN.matcher(text).find()
                || TranslationOutputGuard.containsInstructionArtifact(text);
    }

    /** 检测占位符失败。 */
    static boolean isProtectedTokenFailure(Throwable failure) {
        if (failure == null) {
            return false;
        }
        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<Throwable, Boolean>());
        return isProtectedTokenFailure(failure, visited, 0);
    }

    /** 规范化占位符。 */
    static String canonicalizeProtectedTokens(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = TOKEN.matcher(text);
        StringBuffer output = new StringBuffer(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    canonicalToken(tokenIndex(matcher))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /** 切换备用占位符。 */
    static String alternateProtectedTokens(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = CANONICAL_TOKEN.matcher(text);
        StringBuffer output = new StringBuffer(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    "[[UTP_" + normalizeTokenIndex(matcher.group(1)) + "]]"));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static Map<String, Integer> tokenCounts(String text) {
        Map<String, Integer> tokens = new TreeMap<String, Integer>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = canonicalToken(tokenIndex(matcher));
            Integer count = tokens.get(token);
            tokens.put(token, count == null ? 1 : count + 1);
        }
        return tokens;
    }

    private static int tokenTotal(Map<String, Integer> tokens) {
        int total = 0;
        for (Integer count : tokens.values()) {
            total += count == null ? 0 : count.intValue();
        }
        return total;
    }

    private static String tokenIndex(Matcher matcher) {
        String index = matcher.group(1);
        return normalizeTokenIndex(index == null ? matcher.group(2) : index);
    }

    private static String normalizeTokenIndex(String index) {
        int cursor = 0;
        while (cursor + 1 < index.length() && index.charAt(cursor) == '0') {
            cursor++;
        }
        return index.substring(cursor);
    }

    private static String canonicalToken(String index) {
        return "__UT_" + index + "__";
    }

    private static boolean isProtectedTokenFailure(
            Throwable failure, Set<Throwable> visited, int depth) {
        if (failure == null || depth > 12 || !visited.add(failure)) {
            return false;
        }
        if (failure instanceof ValidationException
                && ((ValidationException) failure).protectedTokenFailure) {
            return true;
        }
        String message = failure.getMessage();
        if (message != null && message.startsWith(PROTECTED_TOKEN_FAILURE)) {
            return true;
        }
        if (isProtectedTokenFailure(failure.getCause(), visited, depth + 1)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (isProtectedTokenFailure(suppressed, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOutputValidationFailure(
            Throwable failure, Set<Throwable> visited, int depth) {
        if (failure == null || depth > 12 || !visited.add(failure)) {
            return false;
        }
        if (failure instanceof ValidationException) {
            return true;
        }
        if (isOutputValidationFailure(failure.getCause(), visited, depth + 1)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (isOutputValidationFailure(suppressed, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void requireQuestionShape(String source, String output) {
        int sourceQuestions = countQuestionMarks(source);
        if (sourceQuestions > 0 && countQuestionMarks(output) < sourceQuestions) {
            throw invalidOutput("Translation output changed a question into an answer");
        }
    }

    private static int countQuestionMarks(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '?' || value == '\uff1f') {
                count++;
            }
        }
        return count;
    }

    private static void requireTargetLanguageShift(
            String source, String output, String targetLanguage) {
        if (!TargetLanguage.isSimplifiedChinese(targetLanguage)
                && !TargetLanguage.isTraditionalChinese(targetLanguage)) {
            return;
        }
        String sourceVisible = languageCheckText(source);
        if (!containsLatinWord(sourceVisible)) {
            return;
        }
        String outputVisible = languageCheckText(output);
        if (countHanCharacters(outputVisible) == 0) {
            throw invalidOutput(
                    "Translation output did not use the target language");
        }
    }

    private static String languageCheckText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String visible = TOKEN.matcher(text).replaceAll(" ");
        visible = INTERNAL_MARKER.matcher(visible).replaceAll(" ");
        visible = StyledTranslationTemplate.strip(visible);
        return InlineTextureCode.strip(visible);
    }

    private static boolean containsLatinWord(String text) {
        int run = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) {
                run++;
                if (run >= 2) {
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }

    private static int countHanCharacters(String text) {
        int count = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                count++;
            }
            offset += Character.charCount(codePoint);
        }
        return count;
    }

    private static String unwrapQuotes(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '\'' && last == '\'')
                    || (first == '\u201c' && last == '\u201d')) {
                return text.substring(1, text.length() - 1).trim();
            }
        }
        return text;
    }

    private static final class ValidationException extends IllegalArgumentException {
        private final boolean protectedTokenFailure;

        private ValidationException(String message, boolean protectedTokenFailure) {
            super(message);
            this.protectedTokenFailure = protectedTokenFailure;
        }
    }

}

package org.universaltranslator.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 占位符贴标点 */
final class StructuredPunctuation {
    private static final Pattern TOKEN = Pattern.compile("__UT_\\d+__");

    private StructuredPunctuation() {
    }

    static String restoreTokenAdjacent(String sourceTemplate, String translatedTemplate) {
        if (sourceTemplate == null || translatedTemplate == null
                || sourceTemplate.isEmpty() || translatedTemplate.isEmpty()) {
            return translatedTemplate;
        }
        String output = translatedTemplate;
        Matcher matcher = TOKEN.matcher(sourceTemplate);
        while (matcher.find()) {
            String token = matcher.group();
            String before = punctuationBefore(sourceTemplate, matcher.start());
            if (!before.isEmpty()) {
                output = ensureBeforeToken(output, token, before);
            }
            String after = punctuationAfter(sourceTemplate, matcher.end());
            if (!after.isEmpty()) {
                output = ensureAfterToken(output, token, after,
                        hasWhitespaceAfter(sourceTemplate, matcher.end() + after.length()));
            }
        }
        return output;
    }

    private static String ensureBeforeToken(String text, String token, String punctuation) {
        int tokenStart = text.indexOf(token);
        if (tokenStart < 0 || hasPunctuationBefore(text, tokenStart, punctuation)) {
            return text;
        }
        int insertAt = tokenStart;
        while (insertAt > 0 && Character.isWhitespace(text.charAt(insertAt - 1))) {
            insertAt--;
        }
        return text.substring(0, insertAt) + punctuation + text.substring(insertAt);
    }

    private static String ensureAfterToken(
            String text, String token, String punctuation, boolean keepSpaceAfter) {
        int tokenStart = text.indexOf(token);
        if (tokenStart < 0) {
            return text;
        }
        int tokenEnd = tokenStart + token.length();
        int existingEnd = punctuationEndAfter(text, tokenEnd, punctuation);
        if (existingEnd >= 0) {
            return keepSpaceAfter ? ensureSpaceAfter(text, existingEnd) : text;
        }
        String suffix = keepSpaceAfter && needsSpaceAfter(text, tokenEnd) ? " " : "";
        return text.substring(0, tokenEnd) + punctuation + suffix + text.substring(tokenEnd);
    }

    private static boolean hasPunctuationBefore(String text, int tokenStart, String punctuation) {
        int index = tokenStart;
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        if (punctuation.length() == 1 && index > 0
                && samePunctuation(punctuation.charAt(0), text.charAt(index - 1))) {
            return true;
        }
        return index >= punctuation.length()
                && text.substring(index - punctuation.length(), index).equals(punctuation);
    }

    private static int punctuationEndAfter(String text, int tokenEnd, String punctuation) {
        int index = tokenEnd;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        if (punctuation.length() == 1 && index < text.length()
                && samePunctuation(punctuation.charAt(0), text.charAt(index))) {
            return index + 1;
        }
        return index + punctuation.length() <= text.length()
                && text.substring(index, index + punctuation.length()).equals(punctuation)
                ? index + punctuation.length() : -1;
    }

    private static String punctuationBefore(String text, int tokenStart) {
        int index = tokenStart;
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        int end = index;
        while (index > 0 && isStructuredPunctuation(text.charAt(index - 1))) {
            index--;
        }
        return text.substring(index, end);
    }

    private static String punctuationAfter(String text, int tokenEnd) {
        int index = tokenEnd;
        int start = index;
        while (index < text.length() && isStructuredPunctuation(text.charAt(index))) {
            index++;
        }
        return text.substring(start, index);
    }

    private static boolean hasWhitespaceAfter(String text, int index) {
        return index < text.length() && Character.isWhitespace(text.charAt(index));
    }

    private static boolean needsSpaceAfter(String text, int tokenEnd) {
        return tokenEnd < text.length() && !Character.isWhitespace(text.charAt(tokenEnd))
                && !isStructuredPunctuation(text.charAt(tokenEnd));
    }

    private static String ensureSpaceAfter(String text, int punctuationEnd) {
        if (punctuationEnd >= text.length()
                || Character.isWhitespace(text.charAt(punctuationEnd))
                || isStructuredPunctuation(text.charAt(punctuationEnd))) {
            return text;
        }
        return text.substring(0, punctuationEnd) + " " + text.substring(punctuationEnd);
    }

    private static boolean samePunctuation(char expected, char actual) {
        return normalizedPunctuation(expected) == normalizedPunctuation(actual);
    }

    private static char normalizedPunctuation(char value) {
        switch (value) {
            case '\uff1a': return ':';
            case '\uff1b': return ';';
            case '\uff0c': return ',';
            case '\u3002': return '.';
            case '\uff01': return '!';
            case '\uff1f': return '?';
            default: return value;
        }
    }

    private static boolean isStructuredPunctuation(char value) {
        switch (value) {
            case ':':
            case '\uff1a':
            case ';':
            case '\uff1b':
            case ',':
            case '\uff0c':
            case '.':
            case '\u3002':
            case '!':
            case '\uff01':
            case '?':
            case '\uff1f':
                return true;
            default:
                return false;
        }
    }
}

package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;

/** 视觉边界规则 */
public final class VisualTextBoundaries {
    private static final int MIN_SEPARATOR_RUN = 3;

    private VisualTextBoundaries() {
    }

    public static boolean hasSeparatorLine(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (isSeparatorLine(line)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSeparatorLine(String text) {
        return hasSeparatorLine(splitLines(text));
    }

    public static boolean isSeparatorLine(String line) {
        if (line == null) {
            return false;
        }
        int count = 0;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (Character.isWhitespace(value)) {
                continue;
            }
            if (!isSeparatorChar(value)) {
                return false;
            }
            count++;
        }
        return count >= MIN_SEPARATOR_RUN;
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            lines.add("");
            return lines;
        }
        String[] split = text.split("\\R", -1);
        for (String line : split) {
            lines.add(line);
        }
        return lines;
    }

    public static List<String> splitBracketSegments(String text) {
        List<String> segments = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return segments;
        }
        int cursor = 0;
        int index = 0;
        while (index < text.length()) {
            char close = matchingClose(text.charAt(index));
            if (close == 0) {
                index++;
                continue;
            }
            int end = text.indexOf(close, index + 1);
            if (end < 0) {
                end = text.length() - 1;
            }
            if (InlineTextureCode.isExact(text.substring(index, end + 1))) {
                index = end + 1;
                continue;
            }
            addSegment(segments, text.substring(cursor, index));
            addSegment(segments, text.substring(index, end + 1));
            cursor = end + 1;
            index = cursor;
        }
        addSegment(segments, text.substring(cursor));
        return segments;
    }

    private static void addSegment(List<String> segments, String value) {
        if (value != null && !value.trim().isEmpty()) {
            segments.add(value.trim());
        }
    }

    private static char matchingClose(char open) {
        switch (open) {
            case '\u300a': return '\u300b';
            case '<': return '>';
            case '\u300c': return '\u300d';
            case '\u3010': return '\u3011';
            case '[': return ']';
            case '(': return ')';
            case '\uff08': return '\uff09';
            default: return 0;
        }
    }

    public static boolean isSeparatorChar(char value) {
        switch (value) {
            case '-':
            case '_':
            case '\u2010':
            case '\u2011':
            case '\u2012':
            case '\u2013':
            case '\u2014':
            case '\u2015':
            case '\u2212':
            case '\u2500':
            case '\u2501':
                return true;
            default:
                return false;
        }
    }
}

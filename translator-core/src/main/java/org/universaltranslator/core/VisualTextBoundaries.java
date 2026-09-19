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

    /** 是否包含纵向图形行 */
    public static boolean hasGraphicLayout(List<String> lines) {
        if (lines == null) {
            return false;
        }
        int graphicLines = 0;
        for (String line : lines) {
            if (isGraphicLine(line)) {
                graphicLines++;
            }
            if (containsDirectionalArrow(line) || containsDecorativeFormat(line)) {
                return true;
            }
        }
        return graphicLines >= 2;
    }

    /** 是否包含需要固定原始槽位的装饰符号布局 */
    public static boolean hasDecorativeLayout(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (containsDecorativeFormat(line)) {
                return true;
            }
        }
        return false;
    }

    /** 保留箭头和图形槽位 */
    public static boolean isGraphicLine(String line) {
        if (line == null) {
            return false;
        }
        String value = stripFormatting(line).trim();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 3) {
            return false;
        }
        boolean marker = false;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (!isGraphicCodePoint(codePoint)) {
                return false;
            }
            marker |= codePoint != 'I' && codePoint != 'V' && codePoint != 'X';
            index += Character.charCount(codePoint);
        }
        return marker || value.length() == 1;
    }

    public static boolean hasSeparatorLine(String text) {
        return hasSeparatorLine(splitLines(text));
    }

    public static boolean isSeparatorLine(String line) {
        if (line == null) {
            return false;
        }
        String value = stripFormatting(line).trim();
        int count = 0;
        boolean onlySeparators = true;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (!isSeparatorChar(current)) {
                onlySeparators = false;
                break;
            }
            count++;
        }
        if (onlySeparators && count >= MIN_SEPARATOR_RUN) {
            return true;
        }
        return isFramedSeparatorLine(value);
    }

    /**
     * Recognizes a decorative separator with markers on the sides, such as
     * {@code x-------x} or the single-sided {@code ---------->}. The markers are
     * layout anchors, not sentence content, so text on neighboring lines must
     * remain in its own block.
     */
    private static boolean isFramedSeparatorLine(String value) {
        StringBuilder compact = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!Character.isWhitespace(current)) {
                compact.append(current);
            }
        }
        if (compact.length() < MIN_SEPARATOR_RUN + 2) {
            return false;
        }
        int runStart = -1;
        int runEnd = -1;
        for (int index = 0; index < compact.length(); index++) {
            if (!isSeparatorChar(compact.charAt(index))) {
                if (runStart >= 0) {
                    runEnd = index;
                    break;
                }
                continue;
            }
            if (runStart < 0) {
                runStart = index;
            }
        }
        if (runStart < 0) {
            return false;
        }
        if (runEnd < 0) {
            runEnd = compact.length();
        }
        if (runEnd - runStart < MIN_SEPARATOR_RUN) {
            return false;
        }
        int leading = compact.codePointCount(0, runStart);
        int trailing = compact.codePointCount(runEnd, compact.length());
        return leading <= 1 && trailing <= 1 && leading + trailing >= 1;
    }

    public static String stripAttachedSeparatorRuns(String line) {
        String value = line == null ? "" : line.trim();
        int start = separatorRunEnd(value, 0, 1);
        if (start > 0) {
            value = value.substring(start).trim();
        }
        int end = separatorRunEnd(value, value.length() - 1, -1);
        return end < value.length() - 1
                ? value.substring(0, end + 1).trim() : value;
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

    private static String stripFormatting(String text) {
        StringBuilder value = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u00a7' && index + 1 < text.length()) {
                index++;
                continue;
            }
            value.append(current);
        }
        return value.toString();
    }

    private static boolean isGraphicCodePoint(int codePoint) {
        switch (codePoint) {
            case 'I':
            case 'V':
            case 'X':
            case '|':
            case '/':
            case '\\':
            case '^':
            case 'v':
            case '<':
            case '>':
            case '\u2191':
            case '\u2193':
            case '\u2190':
            case '\u2192':
            case '\u2195':
            case '\u2194':
            case '\u2196':
            case '\u2197':
            case '\u2198':
            case '\u2199':
                return true;
            default:
                return false;
        }
    }

    private static boolean containsDirectionalArrow(String line) {
        if (line == null) {
            return false;
        }
        for (int index = 0; index < line.length(); ) {
            int codePoint = line.codePointAt(index);
            if (codePoint >= '\u2190' && codePoint <= '\u21ff') {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsDecorativeFormat(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        return line.indexOf("<<") >= 0
                || line.indexOf(">>") >= 0
                || line.indexOf('\u300a') >= 0
                || line.indexOf('\u300b') >= 0
                || line.indexOf('\u3008') >= 0
                || line.indexOf('\u3009') >= 0
                || line.indexOf('\u300c') >= 0
                || line.indexOf('\u300d') >= 0
                || line.indexOf('\u3010') >= 0
                || line.indexOf('\u3011') >= 0
                || line.indexOf('\uff1c') >= 0
                || line.indexOf('\uff1e') >= 0;
    }

    public static List<String> splitBracketSegments(String text) {
        List<String> segments = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) {
            return segments;
        }
        int cursor = 0;
        int index = 0;
        while (index < text.length()) {
            String open = delimiterAt(text, index);
            if (open == null) {
                index++;
                continue;
            }
            String close = matchingClose(open);
            int end = close == null
                    ? -1 : text.indexOf(close, index + open.length());
            if (end < 0) {
                addSegment(segments, text.substring(cursor, index));
                addSegment(segments, text.substring(index));
                cursor = text.length();
                break;
            }
            int segmentEnd = end + close.length();
            if (InlineTextureCode.isExact(text.substring(index, segmentEnd))) {
                index = segmentEnd;
                continue;
            }
            addSegment(segments, text.substring(cursor, index));
            addSegment(segments, text.substring(index, segmentEnd));
            cursor = segmentEnd;
            index = cursor;
        }
        if (cursor < text.length()) {
            addSegment(segments, text.substring(cursor));
        }
        return segments;
    }

    private static void addSegment(List<String> segments, String value) {
        if (value != null && !value.trim().isEmpty()) {
            segments.add(value.trim());
        }
    }

    private static String delimiterAt(String text, int index) {
        if (text.startsWith("<<", index) || text.startsWith(">>", index)) {
            return text.substring(index, index + 2);
        }
        switch (text.charAt(index)) {
            case '\u300a':
            case '\u300b':
            case '\u3008':
            case '\u3009':
            case '\u300c':
            case '\u300d':
            case '\u3010':
            case '\u3011':
            case '<':
            case '>':
            case '[':
            case '(':
            case '\uff08':
                return text.substring(index, index + 1);
            default:
                return null;
        }
    }

    private static String matchingClose(String open) {
        if ("<<".equals(open)) {
            return ">>";
        }
        if (">>".equals(open)) {
            return "<<";
        }
        switch (open.charAt(0)) {
            case '\u300a': return "\u300b";
            case '\u300b': return "\u300a";
            case '\u3008': return "\u3009";
            case '\u3009': return "\u3008";
            case '\u300c': return "\u300d";
            case '\u300d': return "\u300c";
            case '\u3010': return "\u3011";
            case '\u3011': return "\u3010";
            case '<': return ">";
            case '>': return "<";
            case '[': return "]";
            case '(': return ")";
            case '\uff08': return "\uff09";
            default: return null;
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
            case '=':
            case '\uff1d':
                return true;
            default:
                return false;
        }
    }

    private static int separatorRunEnd(String value, int start, int direction) {
        int index = start;
        int count = 0;
        while (index >= 0 && index < value.length()) {
            char current = value.charAt(index);
            if (isSeparatorChar(current)) {
                count++;
            } else if (!Character.isWhitespace(current)) {
                break;
            }
            index += direction;
        }
        return count >= MIN_SEPARATOR_RUN ? index : start;
    }
}

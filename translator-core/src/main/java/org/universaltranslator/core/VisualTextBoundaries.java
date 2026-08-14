package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;

/** Visual-only text boundary rules. */
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
            if (!isSeparator(value)) {
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

    private static boolean isSeparator(char value) {
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

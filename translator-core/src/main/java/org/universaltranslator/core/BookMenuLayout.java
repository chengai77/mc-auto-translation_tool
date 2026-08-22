package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;

/** 书本菜单行 */
final class BookMenuLayout {
    interface LineTranslator {
        String translate(String text);
    }

    interface BlockTranslator {
        List<String> translate(List<String> lines);
    }

    private static final int MIN_LEADER_RUN = 3;
    private static final int MIN_PRICE_LEADER_RUN = 2;
    private static final int MAX_VALUE_LENGTH = 16;

    private BookMenuLayout() {
    }

    static boolean hasMenuRows(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (MenuRow.parse(line) != null) {
                return true;
            }
        }
        return false;
    }

    static List<String> translate(
            List<String> originals,
            LineTranslator lineTranslator,
            BlockTranslator blockTranslator
    ) {
        List<String> result = new ArrayList<String>(originals.size());
        int index = 0;
        while (index < originals.size()) {
            String line = originals.get(index);
            MenuRow row = MenuRow.parse(line);
            if (row != null) {
                result.add(row.rebuild(lineTranslator.translate(row.label)));
                index++;
                continue;
            }
            if (line == null || line.trim().isEmpty()
                    || VisualTextBoundaries.isSeparatorLine(line)) {
                result.add(line);
                index++;
                continue;
            }
            int start = index;
            while (index < originals.size()
                    && shouldJoinWithTextBlock(originals.get(index))) {
                index++;
            }
            result.addAll(blockTranslator.translate(
                    new ArrayList<String>(originals.subList(start, index))));
        }
        return result;
    }

    private static boolean shouldJoinWithTextBlock(String line) {
        return line != null && !line.trim().isEmpty()
                && !VisualTextBoundaries.isSeparatorLine(line)
                && MenuRow.parse(line) == null;
    }

    private static final class MenuRow {
        private final String original;
        private final String prefix;
        private final String label;
        private final String gap;
        private final String separator;
        private final String value;

        private MenuRow(
                String original,
                String prefix,
                String label,
                String gap,
                String separator,
                String value
        ) {
            this.original = original;
            this.prefix = prefix;
            this.label = label;
            this.gap = gap;
            this.separator = separator;
            this.value = value;
        }

        static MenuRow parse(String line) {
            if (line == null || VisualTextBoundaries.isSeparatorLine(line)) {
                return null;
            }
            int[] run = bestSeparatorRun(line);
            if (run == null) {
                return null;
            }
            String left = line.substring(0, run[0]);
            String right = line.substring(run[1]);
            int labelStart = firstText(left);
            int labelEnd = lastTextEnd(left);
            if (labelStart < 0 || labelEnd <= labelStart) {
                return null;
            }
            return new MenuRow(
                    line,
                    left.substring(0, labelStart),
                    left.substring(labelStart, labelEnd),
                    left.substring(labelEnd),
                    line.substring(run[0], run[1]),
                    right);
        }

        String rebuild(String translatedLabel) {
            String labelText = translatedLabel == null || translatedLabel.trim().isEmpty()
                    ? label : translatedLabel.trim();
            String rebuilt = prefix + labelText + gap
                    + repeat(separator.charAt(0), separatorCount(labelText)) + value;
            return rebuilt.equals(original) ? original : rebuilt;
        }

        private int separatorCount(String translatedLabel) {
            int desired = original.length() - prefix.length() - translatedLabel.length()
                    - gap.length() - value.length();
            int minimum = Math.min(separator.length(), MIN_PRICE_LEADER_RUN);
            return Math.max(minimum, desired);
        }

        private static int[] bestSeparatorRun(String line) {
            int bestStart = -1;
            int bestEnd = -1;
            int index = 0;
            while (index < line.length()) {
                if (!VisualTextBoundaries.isSeparatorChar(line.charAt(index))) {
                    index++;
                    continue;
                }
                int start = index;
                while (index < line.length()
                        && VisualTextBoundaries.isSeparatorChar(line.charAt(index))) {
                    index++;
                }
                if (isMenuRun(line, start, index)
                        && index - start > bestEnd - bestStart) {
                    bestStart = start;
                    bestEnd = index;
                }
            }
            return bestStart < 0 ? null : new int[] { bestStart, bestEnd };
        }

        private static boolean isMenuRun(String line, int start, int end) {
            String left = line.substring(0, start).trim();
            String right = line.substring(end).trim();
            int length = end - start;
            if (left.isEmpty() || right.isEmpty()) {
                return false;
            }
            return length >= MIN_LEADER_RUN
                    || (length >= MIN_PRICE_LEADER_RUN && looksLikeValue(right));
        }

        private static boolean looksLikeValue(String value) {
            if (value.length() > MAX_VALUE_LENGTH) {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                char ch = value.charAt(index);
                if (Character.isDigit(ch) || ch == '$' || ch == '\uffe5'
                        || ch == '\u00a5' || ch == '\u20ac' || ch == '\u00a3') {
                    return true;
                }
            }
            return false;
        }

        private static int firstText(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (!Character.isWhitespace(value.charAt(index))) {
                    return index;
                }
            }
            return -1;
        }

        private static int lastTextEnd(String value) {
            int index = value.length();
            while (index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
                index--;
            }
            return index;
        }

        private static String repeat(char value, int count) {
            StringBuilder output = new StringBuilder(count);
            for (int index = 0; index < count; index++) {
                output.append(value);
            }
            return output.toString();
        }
    }
}

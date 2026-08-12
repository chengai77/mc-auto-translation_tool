package org.universaltranslator.fabric;

import java.util.ArrayList;
import java.util.List;

final class TranslationLogText {
    private TranslationLogText() {
    }

    static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        int limit = Math.max(8, maxChars);
        String[] rawLines = text.split("\\n", -1);
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            while (line.length() > limit) {
                int split = findSplit(line, limit);
                lines.add(line.substring(0, split).trim());
                line = line.substring(split).trim();
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static int findSplit(String line, int limit) {
        int split = -1;
        for (int index = Math.min(limit, line.length() - 1); index > limit / 2; index--) {
            if (Character.isWhitespace(line.charAt(index))) {
                split = index;
                break;
            }
        }
        return split > 0 ? split : Math.min(limit, line.length());
    }
}

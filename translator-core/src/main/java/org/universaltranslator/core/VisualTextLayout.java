package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.List;

/** 视觉文本布局 */
final class VisualTextLayout {
    private VisualTextLayout() {
    }

    static String joinVisualLines(List<String> lines) {
        return joinVisualLines(lines, false);
    }

    private static String joinVisualLines(List<String> lines, boolean preserveLineBoundaries) {
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String normalized = line.replace('\n', ' ').replace('\r', ' ').trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                if (preserveLineBoundaries) {
                    joined.append('\n');
                } else if (needsSpace(joined, normalized)) {
                    joined.append(' ');
                }
            }
            joined.append(normalized);
        }
        return joined.toString();
    }

    static List<String> distributeLineBounded(List<String> originals, String translatedText) {
        List<String> translatedLines = normalizeTranslatedLines(translatedText);
        if (translatedLines.size() == originals.size()) {
            return translatedLines;
        }
        String compact = translatedText == null
                ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
        String[] translatedWords = compact.isEmpty() ? new String[0] : compact.split("\\s+");
        List<String> result = new ArrayList<String>(originals.size());
        int wordOffset = 0;
        int remainingLines = visibleLineCount(originals);
        for (int index = 0; index < originals.size(); index++) {
            String original = originals.get(index);
            if (original == null || original.trim().isEmpty()) {
                result.add(original);
                continue;
            }
            int remainingWords = translatedWords.length - wordOffset;
            int words = remainingLines <= 1
                    ? remainingWords
                    : Math.max(1, (remainingWords + remainingLines - 1) / remainingLines);
            int end = Math.min(translatedWords.length, wordOffset + words);
            remainingLines--;
            if (end > wordOffset) {
                result.add(joinWords(translatedWords, wordOffset, end));
                wordOffset = end;
            } else if (index == originals.size() - 1 && wordOffset < translatedWords.length) {
                result.add(joinWords(translatedWords, wordOffset, translatedWords.length));
                wordOffset = translatedWords.length;
            } else {
                result.add("");
            }
        }
        return result;
    }

    static String joinWithNewlines(List<String> lines) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                joined.append('\n');
            }
            String line = lines.get(index);
            if (line != null) {
                joined.append(line);
            }
        }
        return joined.toString();
    }

    static List<String> distributeVisualLines(List<String> originals, String translatedText) {
        List<String> normalizedTranslated = normalizeTranslatedLines(translatedText);
        if (normalizedTranslated.size() == originals.size()) {
            return normalizedTranslated;
        }
        String compact = translatedText == null
                ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
        String[] words = compact.isEmpty() ? new String[0] : compact.split("\\s+");
        List<String> replacement = new ArrayList<String>(originals.size());
        int offset = 0;
        int remainingLines = visibleLineCount(originals);
        for (String original : originals) {
            if (original == null || original.trim().isEmpty()) {
                replacement.add(original);
                continue;
            }
            int remainingWords = words.length - offset;
            int count = remainingLines <= 1
                    ? remainingWords : Math.max(1, (remainingWords + remainingLines - 1) / remainingLines);
            int end = Math.min(words.length, offset + count);
            replacement.add(joinWords(words, offset, end));
            offset = end;
            remainingLines--;
        }
        return replacement;
    }

    static List<String> distributeHologramBlocks(List<String> originals, String translatedText) {
        List<String> normalizedTranslated = normalizeTranslatedLines(translatedText);
        if (normalizedTranslated.size() == originals.size()) {
            return padTranslatedLines(originals, normalizedTranslated);
        }
        String compact = translatedText == null
                ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.isEmpty()) {
            return new ArrayList<String>(originals);
        }
        List<String> bracketSegments = VisualTextBoundaries.splitBracketSegments(compact);
        if (bracketSegments.size() > 1) {
            return distributeHologramSegments(originals, bracketSegments);
        }
        if (containsWhitespace(compact)) {
            return padTranslatedLines(originals, distributeVisualLines(originals, compact));
        }
        return distributeHologramCharacters(originals, compact);
    }

    static boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
    }

    private static List<String> distributeHologramSegments(
            List<String> originals, List<String> segments) {
        List<String> replacement = emptyHologramSlots(originals);
        List<Integer> slots = visibleSlots(originals);
        if (slots.isEmpty()) {
            return replacement;
        }
        int segment = 0;
        for (int index = 0; index < slots.size() && segment < segments.size(); index++) {
            if (slots.size() - index == 1) {
                replacement.set(slots.get(index), joinSegments(segments, segment));
                break;
            }
            replacement.set(slots.get(index), segments.get(segment++));
        }
        return replacement;
    }

    private static List<String> distributeHologramCharacters(
            List<String> originals, String translatedText) {
        List<String> replacement = new ArrayList<String>(originals.size());
        int remainingLines = visibleLineCount(originals);
        int charOffset = 0;
        int remainingChars = translatedText.codePointCount(0, translatedText.length());
        for (String original : originals) {
            if (original == null || original.trim().isEmpty()) {
                replacement.add(original);
                continue;
            }
            if (remainingChars <= 0) {
                replacement.add("");
                remainingLines--;
                continue;
            }
            int count = remainingLines <= 1
                    ? remainingChars
                    : Math.max(1, (remainingChars + remainingLines - 1) / remainingLines);
            int endOffset = translatedText.offsetByCodePoints(charOffset, count);
            replacement.add(translatedText.substring(charOffset, endOffset));
            charOffset = endOffset;
            remainingChars -= count;
            remainingLines--;
        }
        return replacement;
    }

    private static int visibleLineCount(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static List<String> padTranslatedLines(List<String> originals, List<String> translatedLines) {
        List<String> replacement = new ArrayList<String>(originals.size());
        for (int index = 0; index < originals.size(); index++) {
            String original = originals.get(index);
            String translated = index < translatedLines.size() ? translatedLines.get(index) : null;
            replacement.add(original == null ? null : translated == null ? "" : translated);
        }
        return replacement;
    }

    private static List<String> emptyHologramSlots(List<String> originals) {
        List<String> replacement = new ArrayList<String>(originals.size());
        for (String original : originals) {
            replacement.add(original == null ? null : "");
        }
        return replacement;
    }

    private static List<Integer> visibleSlots(List<String> originals) {
        List<Integer> slots = new ArrayList<Integer>();
        for (int index = 0; index < originals.size(); index++) {
            String original = originals.get(index);
            if (original != null && !original.trim().isEmpty()) {
                slots.add(index);
            }
        }
        return slots;
    }

    private static String joinWords(String[] words, int start, int end) {
        StringBuilder joined = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(words[index]);
        }
        return joined.toString();
    }

    private static String joinSegments(List<String> segments, int start) {
        StringBuilder joined = new StringBuilder();
        for (int index = start; index < segments.size(); index++) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(segments.get(index));
        }
        return joined.toString();
    }

    private static List<String> normalizeTranslatedLines(String translatedText) {
        List<String> lines = new ArrayList<String>();
        if (translatedText == null) {
            lines.add("");
            return lines;
        }
        String[] split = translatedText.split("\\R", -1);
        for (String line : split) {
            lines.add(line.trim());
        }
        return lines;
    }

    private static boolean needsSpace(char before, char after) {
        if (Character.isWhitespace(before) || Character.isWhitespace(after)) {
            return false;
        }
        return (isAsciiWord(before) && isAsciiWord(after))
                || (isSentenceJoinPunctuation(before) && isAsciiWord(after));
    }

    private static boolean needsSpace(StringBuilder before, String after) {
        String visibleBefore = visibleBoundaryText(before.toString());
        String visibleAfter = visibleBoundaryText(after);
        return !visibleBefore.isEmpty() && !visibleAfter.isEmpty()
                && needsSpace(
                visibleBefore.charAt(visibleBefore.length() - 1), visibleAfter.charAt(0));
    }

    private static String visibleBoundaryText(String text) {
        return TranslationTextStyling.stripLegacyFormatting(
                StyledTranslationTemplate.strip(text)).trim();
    }

    private static boolean isAsciiWord(char value) {
        return value < 128 && Character.isLetterOrDigit(value);
    }

    private static boolean isSentenceJoinPunctuation(char value) {
        switch (value) {
            case '.':
            case ',':
            case ';':
            case ':':
            case '!':
            case '?':
                return true;
            default:
                return false;
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}

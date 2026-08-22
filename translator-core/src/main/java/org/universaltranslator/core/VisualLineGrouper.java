package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** 跨帧聚合视觉文本 */
final class VisualLineGrouper {
    private static final long GROUP_WINDOW_MILLIS = 80L;
    private static final long HOLOGRAM_WINDOW_MILLIS = 500L;
    private static final int MAX_LINES = 8;
    private static final int MAX_TEXT_LENGTH = 420;
    private static final int MAX_HOLOGRAM_LINES = 24;
    private static final int MAX_HOLOGRAM_TEXT_LENGTH = 1_200;

    private final LinkedHashMap<TextKind, Bucket> buckets =
            new LinkedHashMap<TextKind, Bucket>();

    synchronized Group group(String original, TextKind kind) {
        if (!shouldGroup(kind, original)) {
            buckets.remove(kind);
            return null;
        }
        if (kind == TextKind.HOLOGRAM) {
            return groupHologram(original, kind);
        }
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.get(kind);
        if (bucket == null || now - bucket.updatedAt > GROUP_WINDOW_MILLIS
                || bucket.lines.size() >= MAX_LINES) {
            bucket = new Bucket();
            buckets.put(kind, bucket);
        }
        int replayIndex = bucket.replayPreviousIndex(original, now);
        if (replayIndex >= 0) {
            return new Group(bucket.previousText(), original, replayIndex, bucket.previousLines());
        }
        bucket.add(original, now);
        if (bucket.lines.size() < 2) {
            return null;
        }
        String joined = VisualTextLayout.joinVisualLines(bucket.lines);
        if (joined.length() > MAX_TEXT_LENGTH) {
            bucket.reset(original, now);
            return null;
        }
        bucket.rememberJoined(joined);
        return new Group(joined, original, bucket.lineIndex(original), bucket.linesSnapshot());
    }

    synchronized void clear() {
        buckets.clear();
    }

    private Group groupHologram(String original, TextKind kind) {
        if (VisualTextBoundaries.isSeparatorLine(original)) {
            return Group.collecting(original);
        }
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.get(kind);
        if (bucket == null || now - bucket.updatedAt > HOLOGRAM_WINDOW_MILLIS) {
            bucket = new Bucket();
            buckets.put(kind, bucket);
        }
        if (bucket.startsNextCycle(original)) {
            String joined = VisualTextLayout.joinVisualLines(bucket.lines);
            if (joined.length() <= MAX_HOLOGRAM_TEXT_LENGTH) {
                bucket.beginReplay(joined, now);
            } else {
                bucket.reset(original, now);
                return Group.collecting(original);
            }
        }
        int replayIndex = bucket.replayPreviousIndex(original, now);
        if (replayIndex >= 0) {
            return new Group(bucket.previousText(), original, replayIndex, bucket.previousLines());
        }
        if (bucket.lines.size() >= MAX_HOLOGRAM_LINES) {
            bucket.reset(original, now);
        } else {
            bucket.add(original, now);
        }
        return Group.collecting(original);
    }

    private static boolean shouldGroup(TextKind kind, String original) {
        if (original == null || original.indexOf('\n') >= 0 || original.indexOf('\r') >= 0) {
            return false;
        }
        String trimmed = original.trim();
        if (trimmed.isEmpty() || trimmed.length() > 140
                || (kind != TextKind.HOLOGRAM && trimmed.length() < 2)) {
            return false;
        }
        switch (kind) {
            case TITLE:
            case SUBTITLE:
            case ACTION_BAR:
            case CHAT:
            case SYSTEM_MESSAGE:
            case PLAYER_LIST_HEADER:
            case PLAYER_LIST_FOOTER:
            case BOSS_BAR:
            case SIGN:
            case BOOK:
            case HOLOGRAM:
            case OTHER:
                return true;
            default:
                return false;
        }
    }

    static final class Group {
        final String text;
        final List<String> lines;
        private final String currentLine;
        private final int currentIndex;

        private Group(
                String text, String currentLine, int currentIndex, List<String> lines) {
            this.text = text;
            this.currentLine = currentLine;
            this.currentIndex = currentIndex;
            this.lines = lines == null ? Collections.<String>emptyList() : lines;
        }

        boolean collecting() {
            return text == null;
        }

        String lineResult(List<String> translatedLines) {
            if (translatedLines == null || currentIndex < 0 || currentIndex >= translatedLines.size()) {
                return currentLine;
            }
            String line = translatedLines.get(currentIndex);
            return line == null ? currentLine : line;
        }

        String lineResult(String translatedText) {
            String compact = translatedText == null
                    ? "" : translatedText.replace('\n', ' ').replace('\r', ' ').trim();
            if (compact.isEmpty()) {
                return currentLine;
            }
            return currentIndex == 0 ? compact : "";
        }

        private static Group collecting(String currentLine) {
            return new Group(null, currentLine, -1, Collections.<String>emptyList());
        }
    }

    private static final class Bucket {
        private final List<String> lines = new ArrayList<String>();
        private List<String> previousLines = Collections.emptyList();
        private String previousText = "";
        private boolean replayingPrevious;
        private long updatedAt;

        private void add(String original, long now) {
            replayingPrevious = false;
            if (!lines.isEmpty() && lines.get(lines.size() - 1).equals(original)) {
                updatedAt = now;
                return;
            }
            if (lines.contains(original)) {
                reset(original, now);
                return;
            }
            lines.add(original);
            updatedAt = now;
        }

        private void reset(String original, long now) {
            lines.clear();
            lines.add(original);
            replayingPrevious = false;
            updatedAt = now;
        }

        private boolean startsNextCycle(String original) {
            return !replayingPrevious && !lines.isEmpty() && lines.get(0).equals(original);
        }

        private void beginReplay(String text, long now) {
            rememberJoined(text);
            lines.clear();
            replayingPrevious = true;
            updatedAt = now;
        }

        private int replayPreviousIndex(String original, long now) {
            if (previousLines.isEmpty() || previousText.isEmpty()) {
                return -1;
            }
            if (lines.size() == previousLines.size() && previousLines.get(0).equals(original)) {
                lines.clear();
                replayingPrevious = true;
            }
            if (!replayingPrevious) {
                return -1;
            }
            if (!lines.isEmpty() && lines.get(lines.size() - 1).equals(original)) {
                updatedAt = now;
                return lines.size() - 1;
            }
            int index = lines.size();
            if (index >= previousLines.size() || !previousLines.get(index).equals(original)) {
                replayingPrevious = false;
                return -1;
            }
            lines.add(original);
            updatedAt = now;
            return index;
        }

        private void rememberJoined(String text) {
            previousLines = new ArrayList<String>(lines);
            previousText = text;
        }

        private String previousText() {
            return previousText;
        }

        private List<String> previousLines() {
            return new ArrayList<String>(previousLines);
        }

        private List<String> linesSnapshot() {
            return new ArrayList<String>(lines);
        }

        private int lineIndex(String original) {
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index).equals(original)) {
                    return index;
                }
            }
            return 0;
        }
    }
}

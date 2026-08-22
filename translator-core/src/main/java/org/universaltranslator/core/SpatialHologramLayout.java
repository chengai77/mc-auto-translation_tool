package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 合并同一空间行的全息片段。 */
public final class SpatialHologramLayout {
    private static final float LINE_TOLERANCE = 0.045F;
    private static final float MAX_HORIZONTAL_GAP = 1.0F;
    private static final float PIXEL_SCALE = 0.025F;
    private static final int MAX_ROW_FRAGMENTS = 12;
    private static final int MAX_ROW_TEXT = 320;

    private SpatialHologramLayout() {
    }

    public static List<Row> rows(List<Fragment> source) {
        if (source == null || source.size() < 2) {
            return Collections.emptyList();
        }
        List<Fragment> fragments = new ArrayList<Fragment>();
        for (Fragment fragment : source) {
            if (eligible(fragment)) {
                fragments.add(fragment);
            }
        }
        if (fragments.size() < 2) {
            return Collections.emptyList();
        }
        Collections.sort(fragments, new Comparator<Fragment>() {
            @Override
            public int compare(Fragment first, Fragment second) {
                int line = Float.compare(second.y(), first.y());
                return line != 0 ? line : Float.compare(first.left(), second.left());
            }
        });

        List<Row> rows = new ArrayList<Row>();
        int start = 0;
        while (start < fragments.size()) {
            int end = start + 1;
            float lineY = fragments.get(start).y();
            while (end < fragments.size()
                    && Math.abs(fragments.get(end).y() - lineY) <= LINE_TOLERANCE) {
                end++;
            }
            addLineRows(fragments.subList(start, end), rows);
            start = end;
        }
        return rows;
    }

    private static void addLineRows(List<Fragment> line, List<Row> output) {
        List<Fragment> ordered = new ArrayList<Fragment>(line);
        Collections.sort(ordered, new Comparator<Fragment>() {
            @Override
            public int compare(Fragment first, Fragment second) {
                return Float.compare(first.left(), second.left());
            }
        });
        int start = 0;
        for (int index = 1; index <= ordered.size(); index++) {
            boolean boundary = index == ordered.size()
                    || ordered.get(index).left() - ordered.get(index - 1).right()
                    > MAX_HORIZONTAL_GAP;
            if (boundary) {
                addRow(ordered.subList(start, index), output);
                start = index;
            }
        }
    }

    private static void addRow(List<Fragment> source, List<Row> output) {
        if (source.size() < 2 || source.size() > MAX_ROW_FRAGMENTS) {
            return;
        }
        List<Fragment> fragments = new ArrayList<Fragment>(source);
        List<Boolean> spaces = new ArrayList<Boolean>(fragments.size());
        StringBuilder text = new StringBuilder();
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (int index = 0; index < fragments.size(); index++) {
            Fragment fragment = fragments.get(index);
            boolean space = index > 0 && needsSpace(
                    fragments.get(index - 1), fragment);
            spaces.add(Boolean.valueOf(space));
            if (space) {
                text.append(' ');
            }
            text.append(fragment.text().trim());
            left = Math.min(left, fragment.left());
            right = Math.max(right, fragment.right());
        }
        if (text.length() == 0 || text.length() > MAX_ROW_TEXT
                || !containsLetter(text)) {
            return;
        }
        float center = (left + right) * 0.5F;
        Fragment host = fragments.get(0);
        float bestDistance = Math.abs(host.x() - center);
        for (int index = 1; index < fragments.size(); index++) {
            Fragment candidate = fragments.get(index);
            float distance = Math.abs(candidate.x() - center);
            if (distance < bestDistance) {
                host = candidate;
                bestDistance = distance;
            }
        }
        output.add(new Row(fragments, spaces, host.id(), center, text.toString()));
    }

    private static boolean containsLetter(CharSequence text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = Character.codePointAt(text, offset);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean eligible(Fragment fragment) {
        if (fragment == null || fragment.text() == null) {
            return false;
        }
        String text = fragment.text().trim();
        if (text.isEmpty() || text.length() > 160
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                || InlineTextureCode.matcher(text).find()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.getType(codePoint) == Character.PRIVATE_USE) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean needsSpace(Fragment previous, Fragment current) {
        String left = previous.text().trim();
        String right = current.text().trim();
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        float gap = current.left() - previous.right();
        if (gap <= 0.04F) {
            return false;
        }
        char before = left.charAt(left.length() - 1);
        char after = right.charAt(0);
        if (",.;:!?)]}".indexOf(after) >= 0 || "([{\"'".indexOf(before) >= 0) {
            return false;
        }
        return Character.isLetterOrDigit(before) || Character.isLetterOrDigit(after)
                || before == '>' || after == '<' || after == '-';
    }

    public static final class Fragment {
        private final int id;
        private final String text;
        private final float x;
        private final float y;
        private final float scaleX;
        private final int pixelWidth;
        private final int alignment;

        public Fragment(
                int id, String text, float x, float y,
                float scaleX, int pixelWidth, int alignment) {
            this.id = id;
            this.text = text == null ? "" : text;
            this.x = x;
            this.y = y;
            this.scaleX = scaleX;
            this.pixelWidth = Math.max(0, pixelWidth);
            this.alignment = alignment;
        }

        public int id() { return id; }
        public String text() { return text; }
        public float x() { return x; }
        public float y() { return y; }

        float left() {
            float width = width();
            return alignment < 0 ? x : alignment > 0 ? x - width : x - width * 0.5F;
        }

        float right() {
            float width = width();
            return alignment < 0 ? x + width : alignment > 0 ? x : x + width * 0.5F;
        }

        private float width() {
            return Math.max(0.05F, pixelWidth * PIXEL_SCALE * Math.abs(scaleX));
        }
    }

    public static final class Row {
        private final List<Fragment> fragments;
        private final List<Boolean> spaces;
        private final int hostId;
        private final float centerX;
        private final String text;

        private Row(
                List<Fragment> fragments,
                List<Boolean> spaces,
                int hostId,
                float centerX,
                String text) {
            this.fragments = Collections.unmodifiableList(
                    new ArrayList<Fragment>(fragments));
            this.spaces = Collections.unmodifiableList(
                    new ArrayList<Boolean>(spaces));
            this.hostId = hostId;
            this.centerX = centerX;
            this.text = text;
        }

        public List<Fragment> fragments() { return fragments; }
        public boolean spaceBefore(int index) {
            return index >= 0 && index < spaces.size() && spaces.get(index).booleanValue();
        }
        public int hostId() { return hostId; }
        public float centerX() { return centerX; }
        public String text() { return text; }
    }
}

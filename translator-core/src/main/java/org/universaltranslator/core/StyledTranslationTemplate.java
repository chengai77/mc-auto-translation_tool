package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StyledTranslationTemplate {
    public static final int MAX_SPANS = 64;
    private static final String MARKER_SOURCE = "\\{UT_STYLE_(\\d+)_(START|END)\\}";
    private static final Pattern MARKER = Pattern.compile(MARKER_SOURCE);

    private StyledTranslationTemplate() {
    }

    public static String decorate(String text, List<Span> spans) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (spans == null || spans.isEmpty()) {
            return text;
        }
        if (spans.size() > MAX_SPANS || contains(text)) {
            throw new IllegalArgumentException("Styled translation template is too complex");
        }
        List<Span> ordered = new ArrayList<Span>(spans);
        Collections.sort(ordered, new Comparator<Span>() {
            @Override
            public int compare(Span first, Span second) {
                return Integer.compare(first.start(), second.start());
            }
        });
        boolean[] identifiers = new boolean[ordered.size()];
        int cursor = 0;
        StringBuilder output = new StringBuilder(text.length() + ordered.size() * 40);
        for (Span span : ordered) {
            if (span.id() < 0 || span.id() >= identifiers.length || identifiers[span.id()]
                    || span.start() < cursor || span.end() <= span.start() || span.end() > text.length()) {
                throw new IllegalArgumentException("Styled translation spans are invalid");
            }
            identifiers[span.id()] = true;
            output.append(text, cursor, span.start());
            output.append(startMarker(span.id()));
            output.append(text, span.start(), span.end());
            output.append(endMarker(span.id()));
            cursor = span.end();
        }
        output.append(text, cursor, text.length());
        return output.toString();
    }

    public static Parsed parse(String translatedTemplate, int expectedSpanCount) {
        if (translatedTemplate == null || expectedSpanCount < 0 || expectedSpanCount > MAX_SPANS) {
            return null;
        }
        if (expectedSpanCount == 0) {
            return contains(translatedTemplate)
                    ? null : new Parsed(translatedTemplate, Collections.<Span>emptyList());
        }
        List<RawRange> ranges = new ArrayList<RawRange>(expectedSpanCount);
        for (int id = 0; id < expectedSpanCount; id++) {
            String startMarker = startMarker(id);
            String endMarker = endMarker(id);
            int markerStart = uniqueIndexOf(translatedTemplate, startMarker);
            int markerEnd = uniqueIndexOf(translatedTemplate, endMarker);
            int contentStart = markerStart + startMarker.length();
            if (markerStart < 0 || markerEnd <= contentStart
                    || translatedTemplate.substring(contentStart, markerEnd).trim().isEmpty()) {
                return null;
            }
            ranges.add(new RawRange(id, markerStart, contentStart,
                    markerEnd, markerEnd + endMarker.length()));
        }
        Collections.sort(ranges, new Comparator<RawRange>() {
            @Override
            public int compare(RawRange first, RawRange second) {
                return Integer.compare(first.markerStart(), second.markerStart());
            }
        });

        StringBuilder text = new StringBuilder(translatedTemplate.length());
        List<Span> spans = new ArrayList<Span>(expectedSpanCount);
        int cursor = 0;
        for (RawRange range : ranges) {
            if (range.markerStart() < cursor) {
                return null;
            }
            text.append(translatedTemplate, cursor, range.markerStart());
            int start = text.length();
            text.append(translatedTemplate, range.contentStart(), range.contentEnd());
            spans.add(new Span(range.id(), start, text.length()));
            cursor = range.markerEnd();
        }
        text.append(translatedTemplate, cursor, translatedTemplate.length());
        if (contains(text.toString())) {
            return null;
        }
        Collections.sort(spans, new Comparator<Span>() {
            @Override
            public int compare(Span first, Span second) {
                return Integer.compare(first.start(), second.start());
            }
        });
        return new Parsed(text.toString(), Collections.unmodifiableList(spans));
    }

    public static boolean contains(String text) {
        return text != null && MARKER.matcher(text).find();
    }

    public static String strip(String text) {
        return text == null ? "" : MARKER.matcher(text).replaceAll("");
    }

    static Matcher matcher(String text) {
        return MARKER.matcher(text == null ? "" : text);
    }

    private static int uniqueIndexOf(String text, String marker) {
        int index = text.indexOf(marker);
        if (index < 0 || text.indexOf(marker, index + marker.length()) >= 0) {
            return -1;
        }
        return index;
    }

    private static String startMarker(int id) {
        return "{UT_STYLE_" + id + "_START}";
    }

    private static String endMarker(int id) {
        return "{UT_STYLE_" + id + "_END}";
    }

    public static Span span(int id, int start, int end) {
        return new Span(id, start, end);
    }

    public static final class Span {
        private final int id;
        private final int start;
        private final int end;

        private Span(int id, int start, int end) {
            this.id = id;
            this.start = start;
            this.end = end;
        }

        public int id() { return id; }
        public int start() { return start; }
        public int end() { return end; }
    }

    public static final class Parsed {
        private final String text;
        private final List<Span> spans;

        private Parsed(String text, List<Span> spans) {
            this.text = text;
            this.spans = spans;
        }

        public String text() { return text; }
        public List<Span> spans() { return spans; }
    }

    private static final class RawRange {
        private final int id;
        private final int markerStart;
        private final int contentStart;
        private final int contentEnd;
        private final int markerEnd;

        private RawRange(int id, int markerStart, int contentStart, int contentEnd, int markerEnd) {
            this.id = id;
            this.markerStart = markerStart;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
            this.markerEnd = markerEnd;
        }

        int id() { return id; }
        int markerStart() { return markerStart; }
        int contentStart() { return contentStart; }
        int contentEnd() { return contentEnd; }
        int markerEnd() { return markerEnd; }
    }
}

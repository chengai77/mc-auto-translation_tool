package org.universaltranslator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 批次边界协议 */
final class SegmentBatchProtocol {
    private SegmentBatchProtocol() {
    }

    static Batch encode(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Batch sources are required");
        }
        StringBuilder request = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            if (source == null || source.trim().isEmpty() || containsToken(source)) {
                throw new IllegalArgumentException("Batch source contains reserved tokens");
            }
            if (index > 0) {
                request.append('\n');
            }
            request.append(token(index * 2))
                    .append(source)
                    .append(token(index * 2 + 1));
        }
        return new Batch(request.toString(), sources);
    }

    private static boolean containsToken(String source) {
        String upper = source.toUpperCase(Locale.ROOT);
        return upper.contains("__UT_") || upper.contains("[[UTP_");
    }

    private static String token(int index) {
        return "__UT_" + index + "__";
    }

    static final class Batch {
        private final String request;
        private final List<String> sources;

        private Batch(String request, List<String> sources) {
            this.request = request;
            this.sources = Collections.unmodifiableList(new ArrayList<String>(sources));
        }

        String request() {
            return request;
        }

        List<String> decode(String translated, String targetLanguage) {
            String normalized = TranslationOutputValidator.requireValid(
                    request, translated, targetLanguage);
            List<String> output = new ArrayList<String>(sources.size());
            int cursor = 0;
            for (int index = 0; index < sources.size(); index++) {
                String startToken = token(index * 2);
                String endToken = token(index * 2 + 1);
                int markerStart = uniqueIndexOf(normalized, startToken);
                int contentStart = markerStart + startToken.length();
                int contentEnd = uniqueIndexOf(normalized, endToken);
                if (markerStart < cursor || contentEnd < contentStart
                        || !normalized.substring(cursor, markerStart).trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Translation output changed batch boundaries");
                }
                String content = normalized.substring(contentStart, contentEnd);
                output.add(TranslationOutputValidator.requireValid(
                        sources.get(index), content, targetLanguage));
                cursor = contentEnd + endToken.length();
            }
            if (!normalized.substring(cursor).trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Translation output added text outside batch boundaries");
            }
            return output;
        }

        private static int uniqueIndexOf(String text, String token) {
            int index = text.indexOf(token);
            if (index < 0 || text.indexOf(token, index + token.length()) >= 0) {
                return -1;
            }
            return index;
        }
    }
}

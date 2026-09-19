package org.universaltranslator.forge;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

/** 标记已处理文本 */
public final class TranslationBypassText {
    private TranslationBypassText() {
    }

    public static FormattedCharSequence wrap(FormattedCharSequence text) {
        if (text == null || text instanceof BypassFormattedCharSequence) {
            return text;
        }
        return new BypassFormattedCharSequence(text);
    }

    public static boolean isWrapped(FormattedCharSequence text) {
        return text instanceof BypassFormattedCharSequence;
    }

    private static final class BypassFormattedCharSequence implements FormattedCharSequence {
        private final FormattedCharSequence delegate;

        private BypassFormattedCharSequence(FormattedCharSequence delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return delegate.accept(sink);
        }
    }
}


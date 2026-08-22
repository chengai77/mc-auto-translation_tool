package org.universaltranslator.fabric;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.OrderedText;

/** 标记已处理文本 */
public final class TranslationBypassText {
    private TranslationBypassText() {
    }

    public static OrderedText wrap(OrderedText text) {
        if (text == null || text instanceof BypassOrderedText) {
            return text;
        }
        return new BypassOrderedText(text);
    }

    public static boolean isWrapped(OrderedText text) {
        return text instanceof BypassOrderedText;
    }

    private static final class BypassOrderedText implements OrderedText {
        private final OrderedText delegate;

        private BypassOrderedText(OrderedText delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accept(CharacterVisitor visitor) {
            return delegate.accept(visitor);
        }
    }
}

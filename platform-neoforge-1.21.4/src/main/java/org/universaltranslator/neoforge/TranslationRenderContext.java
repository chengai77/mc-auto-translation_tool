package org.universaltranslator.neoforge;

import org.universaltranslator.core.TextKind;

import java.util.ArrayDeque;
import java.util.Deque;

/** 线程局部界面类 */
public final class TranslationRenderContext {
    private static final ThreadLocal<Deque<TextKind>> KINDS =
            new ThreadLocal<Deque<TextKind>>() {
                @Override
                protected Deque<TextKind> initialValue() {
                    return new ArrayDeque<TextKind>();
                }
            };
    private static final ThreadLocal<Integer> TEXT_INPUT_DEPTH =
            new ThreadLocal<Integer>();
    private static final ThreadLocal<Integer> SUPPRESS_TRANSLATION_DEPTH =
            new ThreadLocal<Integer>();

    private TranslationRenderContext() {
    }

    public static void push(TextKind kind) {
        KINDS.get().push(kind);
    }

    public static void pop() {
        Deque<TextKind> kinds = KINDS.get();
        if (!kinds.isEmpty()) {
            kinds.pop();
        }
        if (kinds.isEmpty()) {
            KINDS.remove();
        }
    }

    public static TextKind current() {
        Deque<TextKind> kinds = KINDS.get();
        return kinds.isEmpty() ? TextKind.OTHER : kinds.peek();
    }

    public static TextKind currentOr(TextKind fallback) {
        Deque<TextKind> kinds = KINDS.get();
        return kinds.isEmpty() ? fallback : kinds.peek();
    }

    /** 屏蔽本地输入 */
    public static void pushTextInput() {
        Integer depth = TEXT_INPUT_DEPTH.get();
        TEXT_INPUT_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void popTextInput() {
        Integer depth = TEXT_INPUT_DEPTH.get();
        if (depth == null || depth <= 1) {
            TEXT_INPUT_DEPTH.remove();
        } else {
            TEXT_INPUT_DEPTH.set(depth - 1);
        }
    }

    public static boolean isTextInput() {
        Integer depth = TEXT_INPUT_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void pushSuppressTranslation() {
        Integer depth = SUPPRESS_TRANSLATION_DEPTH.get();
        SUPPRESS_TRANSLATION_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void popSuppressTranslation() {
        Integer depth = SUPPRESS_TRANSLATION_DEPTH.get();
        if (depth == null || depth <= 1) {
            SUPPRESS_TRANSLATION_DEPTH.remove();
        } else {
            SUPPRESS_TRANSLATION_DEPTH.set(depth - 1);
        }
    }

    public static boolean isTranslationSuppressed() {
        Integer depth = SUPPRESS_TRANSLATION_DEPTH.get();
        return depth != null && depth > 0;
    }
}


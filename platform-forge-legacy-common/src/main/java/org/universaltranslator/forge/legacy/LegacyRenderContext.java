package org.universaltranslator.forge.legacy;

import org.universaltranslator.core.TextKind;

import java.util.ArrayDeque;
import java.util.Deque;

/** 旧版渲染类别 */
public final class LegacyRenderContext {
    private static final ThreadLocal<Deque<TextKind>> KINDS =
            new ThreadLocal<Deque<TextKind>>() {
                @Override
                protected Deque<TextKind> initialValue() {
                    return new ArrayDeque<TextKind>();
                }
            };
    private static final ThreadLocal<Integer> TEXT_INPUT_DEPTH =
            new ThreadLocal<Integer>();

    private LegacyRenderContext() {
    }

    public static void pushChat() {
        KINDS.get().push(TextKind.CHAT);
    }

    public static void pushTooltip() {
        KINDS.get().push(TextKind.TOOLTIP);
    }

    public static void pushSign() {
        KINDS.get().push(TextKind.SIGN);
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

    static boolean isTextInput() {
        Integer depth = TEXT_INPUT_DEPTH.get();
        return depth != null && depth > 0;
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

    static TextKind current() {
        Deque<TextKind> kinds = KINDS.get();
        return kinds.isEmpty() ? TextKind.OTHER : kinds.peek();
    }
}

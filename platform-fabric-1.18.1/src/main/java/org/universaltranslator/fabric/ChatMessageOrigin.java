package org.universaltranslator.fabric;

/** 标记玩家聊天来源 */
public final class ChatMessageOrigin {
    private static final ThreadLocal<Boolean> PLAYER_MESSAGE = new ThreadLocal<Boolean>();

    private ChatMessageOrigin() {
    }

    public static void begin(boolean playerMessage) {
        PLAYER_MESSAGE.set(Boolean.valueOf(playerMessage));
    }

    public static boolean consumePlayerMessage() {
        Boolean value = PLAYER_MESSAGE.get();
        PLAYER_MESSAGE.remove();
        return value != null && value.booleanValue();
    }

    public static void clear() {
        PLAYER_MESSAGE.remove();
    }
}

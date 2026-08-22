package org.universaltranslator.core;

import java.util.regex.Pattern;

/** 聊天来源兜底 */
public final class ChatMessageClassifier {
    private static final Pattern VANILLA_PLAYER_CHAT = Pattern.compile(
            "^(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)*<[^<>\\r\\n]{1,64}>\\s+\\S");

    private ChatMessageClassifier() {
    }

    public static boolean looksLikePlayerChat(String text) {
        return text != null && VANILLA_PLAYER_CHAT.matcher(text.trim()).find();
    }
}

package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 聊天滚动状态 */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<?> universalTranslator$getVisibleMessages();

    @Accessor("scrolledLines")
    int universalTranslator$getScrolledLines();

    @Accessor("scrolledLines")
    void universalTranslator$setScrolledLines(int value);

    @Accessor("hasUnreadNewMessages")
    boolean universalTranslator$getUnread();

    @Accessor("hasUnreadNewMessages")
    void universalTranslator$setUnread(boolean value);
}


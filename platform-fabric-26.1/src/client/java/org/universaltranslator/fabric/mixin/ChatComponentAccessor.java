package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 聊天滚动状态 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<?> universalTranslator$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int universalTranslator$getScroll();

    @Accessor("chatScrollbarPos")
    void universalTranslator$setScroll(int value);

    @Accessor("newMessageSinceScroll")
    boolean universalTranslator$getUnread();

    @Accessor("newMessageSinceScroll")
    void universalTranslator$setUnread(boolean value);
}

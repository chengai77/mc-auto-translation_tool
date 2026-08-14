package org.universaltranslator.fabric.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.universaltranslator.fabric.RenderedTextBridge;

import java.util.List;

@Mixin(ChatHudLine.class)
abstract class ChatHudLineMixin {
    @ModifyArg(
            method = "breakLines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/ChatMessages;breakRenderedChatMessageLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/client/font/TextRenderer;)Ljava/util/List;"),
            index = 0)
    private StringVisitable universalTranslator$translateWholeMessage(StringVisitable message) {
        return RenderedTextBridge.translateChatMessage(message);
    }
}

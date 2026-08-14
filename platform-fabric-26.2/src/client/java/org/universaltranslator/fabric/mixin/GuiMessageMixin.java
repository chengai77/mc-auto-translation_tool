package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.universaltranslator.fabric.RenderedTextBridge;

@Mixin(GuiMessage.class)
abstract class GuiMessageMixin {
    @ModifyArg(
            method = "splitLines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
            index = 0)
    private FormattedText universalTranslator$translateWholeMessage(FormattedText message) {
        return RenderedTextBridge.translateChatMessage(message);
    }
}

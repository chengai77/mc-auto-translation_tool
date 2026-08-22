package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiMessage.class)
abstract class GuiMessageMixin {
    @ModifyArg(
            method = "splitLines",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
            index = 0)
    private FormattedText universalTranslator$translateWholeMessage(FormattedText message) {
        GuiMessage line = (GuiMessage) (Object) this;
        return RenderedTextBridge.translateChatMessage(
                message, line.source() == GuiMessageSource.PLAYER);
    }

    @Inject(method = "splitLines", at = @At("RETURN"), cancellable = true)
    private void universalTranslator$markTranslatedLines(
            Font font,
            int width,
            CallbackInfoReturnable<List<FormattedCharSequence>> callback
    ) {
        List<FormattedCharSequence> lines = callback.getReturnValue();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> wrapped =
                new ArrayList<FormattedCharSequence>(lines.size());
        for (FormattedCharSequence line : lines) {
            wrapped.add(TranslationBypassText.wrap(line));
        }
        callback.setReturnValue(wrapped);
    }
}

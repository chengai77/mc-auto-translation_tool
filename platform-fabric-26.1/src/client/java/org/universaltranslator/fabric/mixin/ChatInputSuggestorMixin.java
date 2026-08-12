package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.TranslationRenderContext;

@Mixin(CommandSuggestions.class)
abstract class ChatInputSuggestorMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

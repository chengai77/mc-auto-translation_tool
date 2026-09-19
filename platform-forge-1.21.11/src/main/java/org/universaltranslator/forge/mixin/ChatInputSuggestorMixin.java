package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.forge.TranslationRenderContext;

@Mixin(ChatInputSuggestor.class)
abstract class ChatInputSuggestorMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}


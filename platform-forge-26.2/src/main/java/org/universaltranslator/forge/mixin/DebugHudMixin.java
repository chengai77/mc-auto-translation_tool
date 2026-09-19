package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.forge.TranslationRenderContext;

@Mixin(DebugScreenOverlay.class)
abstract class DebugHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}


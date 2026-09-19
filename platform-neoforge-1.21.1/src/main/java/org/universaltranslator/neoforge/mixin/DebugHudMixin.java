package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.neoforge.TranslationRenderContext;

@Mixin(DebugHud.class)
abstract class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(DrawContext context, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(DrawContext context, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

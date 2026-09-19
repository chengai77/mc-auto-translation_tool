package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.forge.TranslationRenderContext;

@Mixin(ForgeGui.class)
abstract class ForgeGuiDebugTextMixin {
    @Inject(method = "renderHUDText", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(
            int width, int height, DrawContext context, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "renderHUDText", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(
            int width, int height, DrawContext context, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

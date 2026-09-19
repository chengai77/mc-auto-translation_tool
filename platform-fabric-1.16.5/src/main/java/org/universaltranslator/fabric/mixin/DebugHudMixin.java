package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.TranslationRenderContext;

/** F3界面不翻译 */
@Mixin(DebugHud.class)
abstract class DebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(MatrixStack matrices, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(MatrixStack matrices, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

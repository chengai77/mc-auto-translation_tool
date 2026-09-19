package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.screen.CommandSuggestor;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.TranslationRenderContext;

/** 命令补全本地化 */
@Mixin(CommandSuggestor.class)
abstract class CommandSuggestorMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$pushSuppressTranslation(
            MatrixStack matrices, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$popSuppressTranslation(
            MatrixStack matrices, int mouseX, int mouseY, CallbackInfo ci) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.TranslationRenderContext;

/** 书籍编辑区不翻译 */
@Mixin(BookEditScreen.class)
abstract class BookEditScreenMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
            at = @At("HEAD"))
    private void universalTranslator$pushBookInput(
            MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.pushTextInput();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
            at = @At("RETURN"))
    private void universalTranslator$popBookInput(
            MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.popTextInput();
    }
}

package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.TranslationRenderContext;

/** 输入框本地化 */
@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetMixin {
    @Inject(
            method = "renderButton(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
            at = @At("HEAD"))
    private void universalTranslator$pushTextInput(
            MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.pushTextInput();
    }

    @Inject(
            method = "renderButton(Lnet/minecraft/client/util/math/MatrixStack;IIF)V",
            at = @At("RETURN"))
    private void universalTranslator$popTextInput(
            MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.popTextInput();
    }
}

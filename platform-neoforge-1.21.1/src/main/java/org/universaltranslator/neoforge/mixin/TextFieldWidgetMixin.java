package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.neoforge.TranslationRenderContext;

/** 输入框本地化 */
@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetMixin {
    @Inject(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"))
    private void universalTranslator$pushTextInput(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.pushTextInput();
    }

    @Inject(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("RETURN"))
    private void universalTranslator$popTextInput(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        TranslationRenderContext.popTextInput();
    }
}

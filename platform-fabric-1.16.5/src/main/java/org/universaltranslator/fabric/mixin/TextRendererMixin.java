package org.universaltranslator.fabric.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;
import org.universaltranslator.fabric.TranslationRenderContext;

/** 捕获世界文本 */
@Mixin(TextRenderer.class)
abstract class TextRendererMixin {
    @Shadow
    public abstract int getWidth(String text);

    @ModifyVariable(
            method = "drawInternal(Ljava/lang/String;FFIZLnet/minecraft/util/math/Matrix4f;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;ZIIZ)I",
            at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translatePreparedString(String text) {
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(TextKind.OTHER));
    }

    @ModifyVariable(
            method = "drawInternal(Lnet/minecraft/text/OrderedText;FFIZ"
                    + "Lnet/minecraft/util/math/Matrix4f;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;ZII)I",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText universalTranslator$translatePreparedOrderedText(OrderedText text) {
        if (TranslationBypassText.isWrapped(text)) {
            return text;
        }
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(TextKind.OTHER));
    }
}

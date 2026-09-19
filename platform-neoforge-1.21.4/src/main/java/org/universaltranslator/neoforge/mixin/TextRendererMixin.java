package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.neoforge.RenderedTextBridge;
import org.universaltranslator.neoforge.TranslationBypassText;
import org.universaltranslator.neoforge.TranslationRenderContext;

/** 捕获世界文本 */
@Mixin(TextRenderer.class)
abstract class TextRendererMixin {
    @Shadow
    public abstract int getWidth(String text);

    @ModifyVariable(
            method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/client/font/TextRenderer$TextLayerType;IIZ)I",
            at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translatePreparedString(String text) {
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(defaultKind()));
    }

    @ModifyVariable(
            method = "drawInternal(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/client/font/TextRenderer$TextLayerType;IIZ)I",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText universalTranslator$translatePreparedOrderedText(OrderedText text) {
        if (TranslationBypassText.isWrapped(text)) {
            return text;
        }
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(defaultKind()));
    }

    private static TextKind defaultKind() {
        return TextKind.OTHER;
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translateMeasuredString(String text) {
        return text;
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/StringVisitable;)I",
            at = @At("HEAD"), argsOnly = true)
    private StringVisitable universalTranslator$translateMeasuredText(StringVisitable text) {
        return text;
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText universalTranslator$translateMeasuredOrderedText(OrderedText text) {
        return text;
    }
}


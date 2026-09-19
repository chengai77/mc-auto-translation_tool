package org.universaltranslator.forge.mixin;

import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.forge.SignTranslationContext;
import org.universaltranslator.forge.TranslationRenderContext;

import java.util.ArrayList;
import java.util.List;

@Mixin(SignBlockEntityRenderer.class)
public abstract class SignRendererContextMixin {
    @Inject(method = "renderText", at = @At("HEAD"))
    private void universalTranslator$pushSignContext(
            BlockPos pos,
            SignText signText,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int lineHeight,
            int maxTextWidth,
            boolean front,
            CallbackInfo ci
    ) {
        SignTranslationContext.push(
                universalTranslator$lines(signText), maxTextWidth, lineHeight,
                text -> MinecraftClient.getInstance().textRenderer.getWidth(text));
        TranslationRenderContext.push(TextKind.SIGN);
    }

    @Redirect(
            method = "renderText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"))
    private int universalTranslator$drawTranslatedText(
            TextRenderer renderer,
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextRenderer.TextLayerType layerType,
            int backgroundColor,
            int light
    ) {
        String original = universalTranslator$text(text);
        SignTranslationContext.WidthMeasurer measurer = value -> MinecraftClient
                .getInstance().textRenderer.getWidth(value);
        String translated = SignTranslationContext.translateNextLine(original, measurer);
        return renderer.draw(
                SignTranslationContext.markSubmittedText(Text.literal(translated).asOrderedText()),
                x + SignTranslationContext.horizontalOffset(original, translated, measurer),
                y + SignTranslationContext.verticalOffset(),
                color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
    }

    @Redirect(
            method = "renderText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void universalTranslator$drawTranslatedOutline(
            TextRenderer renderer,
            OrderedText text,
            float x,
            float y,
            int color,
            int outlineColor,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        String original = universalTranslator$text(text);
        SignTranslationContext.WidthMeasurer measurer = value -> MinecraftClient
                .getInstance().textRenderer.getWidth(value);
        String translated = SignTranslationContext.translateNextLine(original, measurer);
        renderer.drawWithOutline(
                SignTranslationContext.markSubmittedText(Text.literal(translated).asOrderedText()),
                x + SignTranslationContext.horizontalOffset(original, translated, measurer),
                y + SignTranslationContext.verticalOffset(),
                color, outlineColor, matrix, vertexConsumers, light);
    }

    @Inject(method = "renderText", at = @At("RETURN"))
    private void universalTranslator$popSignContext(
            BlockPos pos,
            SignText signText,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int lineHeight,
            int maxTextWidth,
            boolean front,
            CallbackInfo ci
    ) {
        TranslationRenderContext.pop();
        SignTranslationContext.pop();
    }

    private static List<String> universalTranslator$lines(SignText text) {
        List<String> lines = new ArrayList<String>(4);
        if (text == null) {
            return lines;
        }
        Text[] messages = text.getMessages(MinecraftClient.getInstance().shouldFilterText());
        for (Text message : messages) {
            lines.add(message == null ? "" : message.getString());
        }
        return lines;
    }

    private static String universalTranslator$text(OrderedText text) {
        StringBuilder value = new StringBuilder();
        if (text != null) {
            text.accept((index, style, codePoint) -> {
                value.appendCodePoint(codePoint);
                return true;
            });
        }
        return value.toString();
    }

}

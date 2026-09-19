package org.universaltranslator.fabric.mixin;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.SignTranslationContext;
import org.universaltranslator.fabric.TranslationRenderContext;

import java.util.ArrayList;
import java.util.List;

/** 告示牌整块翻译 */
@Mixin(SignBlockEntityRenderer.class)
public abstract class SignRendererContextMixin {
    private static final String RENDER_SIGN =
            "render(Lnet/minecraft/block/entity/SignBlockEntity;F"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;II)V";
    // 与原版一致的排版参数
    private static final int SIGN_LINE_HEIGHT = 10;
    private static final int SIGN_MAX_TEXT_WIDTH = 90;

    @Inject(method = RENDER_SIGN, at = @At("HEAD"))
    private void universalTranslator$pushSignContext(
            SignBlockEntity sign,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            CallbackInfo ci
    ) {
        SignTranslationContext.push(
                universalTranslator$lines(sign), SIGN_MAX_TEXT_WIDTH, SIGN_LINE_HEIGHT,
                text -> MinecraftClient.getInstance().textRenderer.getWidth(text));
        TranslationRenderContext.push(TextKind.SIGN);
    }

    @Redirect(
            method = RENDER_SIGN,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;draw("
                            + "Lnet/minecraft/text/OrderedText;FFIZ"
                            + "Lnet/minecraft/util/math/Matrix4f;"
                            + "Lnet/minecraft/client/render/VertexConsumerProvider;ZII)I"))
    private int universalTranslator$drawTranslatedLine(
            TextRenderer renderer,
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            boolean seeThrough,
            int backgroundColor,
            int light
    ) {
        String original = universalTranslator$text(text);
        SignTranslationContext.WidthMeasurer measurer = value -> MinecraftClient
                .getInstance().textRenderer.getWidth(value);
        String translated = SignTranslationContext.translateNextLine(original, measurer);
        OrderedText submitted = SignTranslationContext.markSubmittedText(
                new LiteralText(translated).asOrderedText());
        // 原版按宽度居中，译文重新居中
        float centeredX = -renderer.getWidth(submitted) / 2.0F;
        return renderer.draw(submitted,
                centeredX, y + SignTranslationContext.verticalOffset(),
                color, shadow, matrix, vertexConsumers, seeThrough, backgroundColor, light);
    }

    /** 发光告示牌走描边绘制入口 */
    @Redirect(
            method = RENDER_SIGN,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;drawWithOutline("
                            + "Lnet/minecraft/text/OrderedText;FFII"
                            + "Lnet/minecraft/util/math/Matrix4f;"
                            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),
            require = 0)
    private void universalTranslator$drawTranslatedGlowLine(
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
        OrderedText submitted = SignTranslationContext.markSubmittedText(
                new LiteralText(translated).asOrderedText());
        // 原版按宽度居中，译文重新居中
        float centeredX = -renderer.getWidth(submitted) / 2.0F;
        renderer.drawWithOutline(submitted,
                centeredX, y + SignTranslationContext.verticalOffset(),
                color, outlineColor, matrix, vertexConsumers, light);
    }

    @Inject(method = RENDER_SIGN, at = @At("RETURN"))
    private void universalTranslator$popSignContext(
            SignBlockEntity sign,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            CallbackInfo ci
    ) {
        TranslationRenderContext.pop();
        SignTranslationContext.pop();
    }

    private static List<String> universalTranslator$lines(SignBlockEntity sign) {
        List<String> lines = new ArrayList<String>(4);
        for (int row = 0; row < 4; row++) {
            Text text = sign.getTextOnRow(row, false);
            lines.add(text == null ? "" : text.getString());
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

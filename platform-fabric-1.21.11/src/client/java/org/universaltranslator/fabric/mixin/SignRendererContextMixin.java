package org.universaltranslator.fabric.mixin;

import net.minecraft.block.entity.SignText;
import net.minecraft.client.render.block.entity.AbstractSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.SignTranslationContext;
import org.universaltranslator.fabric.TranslationRenderContext;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractSignBlockEntityRenderer.class)
public abstract class SignRendererContextMixin {
    @Inject(method = "renderText", at = @At("HEAD"))
    private void universalTranslator$pushSignContext(
            SignBlockEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            boolean front,
            CallbackInfo ci
    ) {
        SignTranslationContext.push(
                universalTranslator$lines(state, front), state.maxTextWidth,
                state.textLineHeight,
                text -> net.minecraft.client.MinecraftClient.getInstance()
                        .textRenderer.getWidth(text));
        TranslationRenderContext.push(TextKind.SIGN);
    }

    @ModifyArgs(
            method = "renderText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitText(Lnet/minecraft/client/util/math/MatrixStack;FFLnet/minecraft/text/OrderedText;ZLnet/minecraft/client/font/TextRenderer$TextLayerType;IIII)V"))
    private void universalTranslator$translateQueuedSignText(Args args) {
        net.minecraft.text.OrderedText originalText = (net.minecraft.text.OrderedText) args.get(3);
        String original = universalTranslator$text(originalText);
        SignTranslationContext.WidthMeasurer measurer = text -> net.minecraft.client.MinecraftClient
                .getInstance().textRenderer.getWidth(text);
        String translated = SignTranslationContext.translateNextLine(original, measurer);
        args.set(3, SignTranslationContext.markSubmittedText(
                net.minecraft.text.Text.literal(translated).asOrderedText()));
        args.set(1, ((Float) args.get(1))
                + SignTranslationContext.horizontalOffset(original, translated, measurer));
        args.set(2, ((Float) args.get(2)) + SignTranslationContext.verticalOffset());
    }

    @Inject(method = "renderText", at = @At("RETURN"))
    private void universalTranslator$popSignContext(
            SignBlockEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            boolean front,
            CallbackInfo ci
    ) {
        TranslationRenderContext.pop();
        SignTranslationContext.pop();
    }

    private static List<String> universalTranslator$lines(SignBlockEntityRenderState state, boolean front) {
        List<String> lines = new ArrayList<String>(4);
        SignText text = front ? state.frontText : state.backText;
        if (text == null) {
            return lines;
        }
        Text[] messages = text.getMessages(state.filterText);
        for (Text message : messages) {
            lines.add(message == null ? "" : message.getString());
        }
        return lines;
    }

    private static String universalTranslator$text(net.minecraft.text.OrderedText text) {
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

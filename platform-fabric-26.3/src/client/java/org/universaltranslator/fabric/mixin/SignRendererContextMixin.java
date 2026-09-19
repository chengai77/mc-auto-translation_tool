package org.universaltranslator.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
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

@Mixin(AbstractSignRenderer.class)
public abstract class SignRendererContextMixin {
    @Inject(method = "submitSignText", at = @At("HEAD"))
    private void universalTranslator$pushSignContext(
            SignRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            SignText signText,
            CallbackInfo ci
    ) {
        SignTranslationContext.push(
                universalTranslator$lines(signText), state.maxTextLineWidth,
                state.textLineHeight,
                text -> net.minecraft.client.Minecraft.getInstance().font.width(text));
        TranslationRenderContext.push(TextKind.SIGN);
    }

    @ModifyArgs(
            method = "submitSignText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"))
    private void universalTranslator$translateQueuedSignText(Args args) {
        net.minecraft.util.FormattedCharSequence originalText =
                (net.minecraft.util.FormattedCharSequence) args.get(3);
        String original = universalTranslator$text(originalText);
        SignTranslationContext.WidthMeasurer measurer = text -> net.minecraft.client.Minecraft
                .getInstance().font.width(text);
        String translated = SignTranslationContext.translateNextLine(original, measurer);
        args.set(3, SignTranslationContext.markSubmittedText(
                net.minecraft.network.chat.Component.literal(translated).getVisualOrderText()));
        args.set(1, ((Float) args.get(1))
                + SignTranslationContext.horizontalOffset(original, translated, measurer));
        args.set(2, ((Float) args.get(2)) + SignTranslationContext.verticalOffset());
    }

    @Inject(method = "submitSignText", at = @At("RETURN"))
    private void universalTranslator$popSignContext(
            SignRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            SignText signText,
            CallbackInfo ci
    ) {
        TranslationRenderContext.pop();
        SignTranslationContext.pop();
    }

    private static List<String> universalTranslator$lines(SignText signText) {
        List<String> lines = new ArrayList<String>(4);
        if (signText == null) {
            return lines;
        }
        List<Component> messages = signText.getMessages(false);
        for (Component message : messages) {
            lines.add(message == null ? "" : message.getString());
        }
        return lines;
    }

    private static String universalTranslator$text(net.minecraft.util.FormattedCharSequence text) {
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

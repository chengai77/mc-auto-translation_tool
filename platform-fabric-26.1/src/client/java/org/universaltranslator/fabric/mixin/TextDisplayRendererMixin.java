package org.universaltranslator.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.HologramLineWidth;
import org.universaltranslator.core.HologramTopAnchor;
import org.universaltranslator.fabric.HologramTextDisplayGroups;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TextDisplayAnchorState;
import org.universaltranslator.fabric.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

/** 完整翻译全息文本 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
abstract class TextDisplayRendererMixin {
    @Shadow
    private Display.TextDisplay.CachedInfo splitLines(Component text, int lineWidth) {
        throw new AssertionError();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;"
                    + "Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("RETURN"))
    private void universalTranslator$translateTextDisplay(
            Display.TextDisplay entity,
            TextDisplayEntityRenderState state,
            float tickProgress,
            CallbackInfo callback) {
        TextDisplayAnchorState anchorState = (TextDisplayAnchorState) state;
        anchorState.universalTranslator$setTopAnchorOffset(0.0F);
        anchorState.universalTranslator$setHorizontalOffset(0.0F);
        Display.TextDisplay.TextRenderState source = state.textRenderState;
        if (source == null || source.text() == null) {
            return;
        }
        int originalLines = lineCount(state.cachedInfo);
        Transformation transformation = state.renderState == null
                ? Transformation.IDENTITY
                : state.renderState.transformation().get(state.interpolationProgress);
        Vector3fc localTranslation = transformation.translation();
        Vector3fc scale = transformation.scale();
        HologramTextDisplayGroups.Result result = HologramTextDisplayGroups.translate(
                System.identityHashCode(entity.level()),
                entity.getId(), entity.getX(), entity.getY(), entity.getZ(),
                state.entityYRot, state.entityXRot, orientationHash(transformation),
                source.text(), localTranslation.x(), localTranslation.y(), scale.x(),
                state.cachedInfo == null ? 0 : state.cachedInfo.width(),
                alignment(source.flags()));
        Component translated = result.text();
        if (translated != source.text()) {
            byte flags = result.centered() ? centeredFlags(source.flags()) : source.flags();
            String originalText = source.text().getString();
            String translatedText = translated.getString();
            int layoutLimit = HologramLineWidth.layoutLimit(
                    source.lineWidth(), originalText, translatedText);
            Display.TextDisplay.CachedInfo translatedLines =
                    splitLines(translated, layoutLimit);
            int translatedLineWidth = HologramLineWidth.resolve(
                    source.lineWidth(), translatedLines.width(), originalText, translatedText);
            state.textRenderState = new Display.TextDisplay.TextRenderState(
                    translated,
                    translatedLineWidth,
                    source.textOpacity(),
                    source.backgroundColor(),
                    flags);
            state.cachedInfo = translatedLines;
            if (!result.hidden()) {
                anchorState.universalTranslator$setTopAnchorOffset(
                        HologramTopAnchor.offset(originalLines, lineCount(state.cachedInfo)));
                anchorState.universalTranslator$setHorizontalOffset(
                        result.horizontalOffset());
            }
        }
        state.cachedInfo = bypassLines(state.cachedInfo);
    }

    @Inject(
            method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
            at = @At("HEAD"))
    private void universalTranslator$alignTranslatedTop(
            TextDisplayEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            float tickProgress,
            CallbackInfo callback
    ) {
        TextDisplayAnchorState anchor = (TextDisplayAnchorState) state;
        float horizontal = anchor.universalTranslator$getHorizontalOffset();
        float vertical = anchor.universalTranslator$getTopAnchorOffset();
        if (horizontal != 0.0F || vertical != 0.0F) {
            poseStack.translate(horizontal, vertical, 0.0F);
        }
    }

    private static int alignment(byte flags) {
        if ((flags & Display.TextDisplay.FLAG_ALIGN_LEFT) != 0) {
            return -1;
        }
        return (flags & Display.TextDisplay.FLAG_ALIGN_RIGHT) != 0 ? 1 : 0;
    }

    private static byte centeredFlags(byte flags) {
        return (byte) (flags
                & ~Display.TextDisplay.FLAG_ALIGN_LEFT
                & ~Display.TextDisplay.FLAG_ALIGN_RIGHT);
    }

    private static int orientationHash(Transformation transformation) {
        int result = transformation.leftRotation().hashCode();
        result = 31 * result + transformation.rightRotation().hashCode();
        Vector3fc scale = transformation.scale();
        result = 31 * result + Float.floatToIntBits(scale.x());
        result = 31 * result + Float.floatToIntBits(scale.y());
        return 31 * result + Float.floatToIntBits(scale.z());
    }

    private static int lineCount(Display.TextDisplay.CachedInfo lines) {
        return lines == null ? 0 : lines.lines().size();
    }

    private static Display.TextDisplay.CachedInfo bypassLines(
            Display.TextDisplay.CachedInfo lines) {
        if (lines == null || lines.lines().isEmpty()) {
            return lines;
        }
        List<Display.TextDisplay.CachedLine> wrapped =
                new ArrayList<Display.TextDisplay.CachedLine>(lines.lines().size());
        for (Display.TextDisplay.CachedLine line : lines.lines()) {
            wrapped.add(new Display.TextDisplay.CachedLine(
                    TranslationBypassText.wrap(line.contents()), line.width()));
        }
        return new Display.TextDisplay.CachedInfo(wrapped, lines.width());
    }
}

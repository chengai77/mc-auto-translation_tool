package org.universaltranslator.fabric.mixin;

import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.HologramLineWidth;
import org.universaltranslator.core.HologramTopAnchor;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.HologramTextDisplayGroups;
import org.universaltranslator.fabric.TextDisplayAnchorState;
import org.universaltranslator.fabric.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

/** 完整翻译全息文本 */
@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
abstract class TextDisplayRendererMixin {
    @Shadow
    private DisplayEntity.TextDisplayEntity.TextLines getLines(Text text, int lineWidth) {
        throw new AssertionError();
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;"
                    + "Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("RETURN"))
    private void universalTranslator$translateTextDisplay(
            DisplayEntity.TextDisplayEntity entity,
            TextDisplayEntityRenderState state,
            float tickProgress,
            CallbackInfo callback) {
        TextDisplayAnchorState anchorState = (TextDisplayAnchorState) state;
        anchorState.universalTranslator$setTopAnchorOffset(0.0F);
        anchorState.universalTranslator$setHorizontalOffset(0.0F);
        DisplayEntity.TextDisplayEntity.Data source = state.data;
        if (source == null || source.text() == null) {
            return;
        }
        int originalLines = lineCount(state.textLines);
        AffineTransformation transformation = state.displayRenderState == null
                ? AffineTransformation.identity()
                : state.displayRenderState.transformation().interpolate(state.lerpProgress);
        Vector3fc localTranslation = transformation.getTranslation();
        Vector3fc scale = transformation.getScale();
        HologramTextDisplayGroups.Result result = HologramTextDisplayGroups.translate(
                System.identityHashCode(entity.getEntityWorld()),
                entity.getId(), entity.getX(), entity.getY(), entity.getZ(),
                state.yaw, state.pitch, orientationHash(transformation),
                source.text(), localTranslation.x(), localTranslation.y(), scale.x(),
                state.textLines == null ? 0 : state.textLines.width(),
                alignment(source.flags()));
        Text translated = result.text();
        if (translated != source.text()) {
            byte flags = result.centered() ? centeredFlags(source.flags()) : source.flags();
            String originalText = source.text().getString();
            String translatedText = translated.getString();
            int layoutLimit = HologramLineWidth.layoutLimit(
                    source.lineWidth(), originalText, translatedText);
            DisplayEntity.TextDisplayEntity.TextLines translatedLines =
                    getLines(translated, layoutLimit);
            int translatedLineWidth = HologramLineWidth.resolve(
                    source.lineWidth(), translatedLines.width(), originalText, translatedText);
            state.data = new DisplayEntity.TextDisplayEntity.Data(
                    translated,
                    translatedLineWidth,
                    source.textOpacity(),
                    source.backgroundColor(),
                    flags);
            state.textLines = translatedLines;
            if (!result.hidden()) {
                anchorState.universalTranslator$setTopAnchorOffset(
                        HologramTopAnchor.offset(originalLines, lineCount(state.textLines)));
                anchorState.universalTranslator$setHorizontalOffset(
                        result.horizontalOffset());
            }
        }
        state.textLines = bypassLines(state.textLines);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IF)V",
            at = @At("HEAD"))
    private void universalTranslator$alignTranslatedTop(
            TextDisplayEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            float tickProgress,
            CallbackInfo callback
    ) {
        TextDisplayAnchorState anchor = (TextDisplayAnchorState) state;
        float horizontal = anchor.universalTranslator$getHorizontalOffset();
        float vertical = anchor.universalTranslator$getTopAnchorOffset();
        if (horizontal != 0.0F || vertical != 0.0F) {
            matrices.translate(horizontal, vertical, 0.0F);
        }
    }

    private static int alignment(byte flags) {
        if ((flags & DisplayEntity.TextDisplayEntity.LEFT_ALIGNMENT_FLAG) != 0) {
            return -1;
        }
        return (flags & DisplayEntity.TextDisplayEntity.RIGHT_ALIGNMENT_FLAG) != 0 ? 1 : 0;
    }

    private static byte centeredFlags(byte flags) {
        return (byte) (flags
                & ~DisplayEntity.TextDisplayEntity.LEFT_ALIGNMENT_FLAG
                & ~DisplayEntity.TextDisplayEntity.RIGHT_ALIGNMENT_FLAG);
    }

    private static int orientationHash(AffineTransformation transformation) {
        int result = transformation.getLeftRotation().hashCode();
        result = 31 * result + transformation.getRightRotation().hashCode();
        Vector3fc scale = transformation.getScale();
        result = 31 * result + Float.floatToIntBits(scale.x());
        result = 31 * result + Float.floatToIntBits(scale.y());
        return 31 * result + Float.floatToIntBits(scale.z());
    }

    private static int lineCount(DisplayEntity.TextDisplayEntity.TextLines lines) {
        return lines == null ? 0 : lines.lines().size();
    }

    private static DisplayEntity.TextDisplayEntity.TextLines bypassLines(
            DisplayEntity.TextDisplayEntity.TextLines lines) {
        if (lines == null || lines.lines().isEmpty()) {
            return lines;
        }
        List<DisplayEntity.TextDisplayEntity.TextLine> wrapped =
                new ArrayList<DisplayEntity.TextDisplayEntity.TextLine>(lines.lines().size());
        for (DisplayEntity.TextDisplayEntity.TextLine line : lines.lines()) {
            wrapped.add(new DisplayEntity.TextDisplayEntity.TextLine(
                    TranslationBypassText.wrap(line.contents()), line.width()));
        }
        return new DisplayEntity.TextDisplayEntity.TextLines(wrapped, lines.width());
    }
}

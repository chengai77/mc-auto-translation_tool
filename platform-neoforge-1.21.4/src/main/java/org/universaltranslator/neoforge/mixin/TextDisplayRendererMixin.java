package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
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
import org.universaltranslator.core.HologramTextAlignment;
import org.universaltranslator.core.HologramTopAnchor;
import org.universaltranslator.core.PlayerFollowHologramTracker;
import org.universaltranslator.neoforge.RenderedTextBridge;
import org.universaltranslator.neoforge.HologramTextDisplayGroups;
import org.universaltranslator.neoforge.TextDisplayAnchorState;
import org.universaltranslator.neoforge.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

/** 完整翻译全息文本 */
@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
abstract class TextDisplayRendererMixin {
    private static final PlayerFollowHologramTracker PLAYER_FOLLOW_TRACKER =
            new PlayerFollowHologramTracker(2_048);
    private static final float PLAYER_FOLLOW_UP_OFFSET = 0.25F;
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
        PlayerFollowHologramTracker.Match playerFollow = detectPlayerFollow(entity);
        anchorState.universalTranslator$setPlayerFollowHidden(
                playerFollow.following() && playerFollow.localPlayer());
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
        HologramTextDisplayGroups.Result result = playerFollow.following()
                ? null
                : HologramTextDisplayGroups.translate(
                        System.identityHashCode(entity.getEntityWorld()),
                        entity.getId(), entity.getX(), entity.getY(), entity.getZ(),
                        state.yaw, state.pitch, orientationHash(transformation),
                        source.text(), localTranslation.x(), localTranslation.y(), scale.x(),
                        state.textLines == null ? 0 : state.textLines.width(),
                        alignment(source.flags()));
        Text translated = playerFollow.following()
                ? RenderedTextBridge.translateHologramText(source.text())
                : result.text();
        if (translated != source.text()) {
            String originalText = source.text().getString();
            String translatedText = translated.getString();
            boolean centerShortPhrase = !playerFollow.following()
                    && HologramTextAlignment.shouldCenter(originalText, translatedText);
            byte flags = playerFollow.following()
                    || (!result.centered() && !centerShortPhrase)
                    ? source.flags() : centeredFlags(source.flags());
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
            if (playerFollow.following() || !result.hidden()) {
                anchorState.universalTranslator$setTopAnchorOffset(playerFollow.following()
                        ? PLAYER_FOLLOW_UP_OFFSET
                        : HologramTopAnchor.offset(originalLines, lineCount(state.textLines)));
                anchorState.universalTranslator$setHorizontalOffset(
                        playerFollow.following() ? 0.0F : result.horizontalOffset());
            }
        }
        state.textLines = bypassLines(state.textLines);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/TextDisplayEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void universalTranslator$alignTranslatedTop(
            TextDisplayEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float tickProgress,
            CallbackInfo callback
    ) {
        TextDisplayAnchorState anchor = (TextDisplayAnchorState) state;
        if (anchor.universalTranslator$isPlayerFollowHidden()
                && MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            callback.cancel();
            return;
        }
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

    private static PlayerFollowHologramTracker.Match detectPlayerFollow(
            DisplayEntity.TextDisplayEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        long entityKey = trackingKey(entity.getEntityWorld(), entity);
        Integer confirmedPlayerId = PLAYER_FOLLOW_TRACKER.confirmedPlayerId(entityKey);
        AbstractClientPlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        if (client.world != null) {
            for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                double dx = entity.getX() - player.getX();
                double dz = entity.getZ() - player.getZ();
                double dy = entity.getY() - player.getY();
                double distance = dx * dx + dz * dz;
                boolean trackedPlayer = confirmedPlayerId != null
                        && confirmedPlayerId.intValue() == player.getId();
                boolean nearby = trackedPlayer
                        ? dy >= 0.8D && dy <= 4.2D && distance <= 1.44D
                        : dy >= 1.2D && dy <= 3.8D && distance <= 0.36D;
                if (nearby && (trackedPlayer || distance < nearestDistance)) {
                    nearest = player;
                    nearestDistance = distance;
                    if (trackedPlayer) {
                        break;
                    }
                }
            }
        }
        if (nearest == null) {
            return PLAYER_FOLLOW_TRACKER.miss(entityKey);
        }
        return PLAYER_FOLLOW_TRACKER.observe(
                entityKey, nearest.getId(), nearest == client.player,
                entity.getX(), entity.getY(), entity.getZ(),
                nearest.getX(), nearest.getY(), nearest.getZ());
    }

    private static long trackingKey(Object world, Object entity) {
        return ((long) System.identityHashCode(world) << 32)
                ^ (System.identityHashCode(entity) & 0xffffffffL);
    }
}


package org.universaltranslator.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.HologramLineWidth;
import org.universaltranslator.core.HologramTextAlignment;
import org.universaltranslator.core.HologramTopAnchor;
import org.universaltranslator.core.PlayerFollowHologramTracker;
import org.universaltranslator.fabric.HologramTextDisplayGroups;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

/** 旧渲染器全息翻译 */
@Mixin(DisplayEntityRenderer.TextDisplayEntityRenderer.class)
abstract class TextDisplayRendererMixin {
    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;"
                    + "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;IF)V";
    private static final PlayerFollowHologramTracker PLAYER_FOLLOW_TRACKER =
            new PlayerFollowHologramTracker(2_048);
    private static final float PLAYER_FOLLOW_UP_OFFSET = 0.25F;

    @Unique
    private DisplayEntity.TextDisplayEntity.TextLines universalTranslator$translatedLines;
    @Unique
    private byte universalTranslator$translatedFlags;
    @Unique
    private boolean universalTranslator$hasTranslatedFlags;

    @Shadow
    private DisplayEntity.TextDisplayEntity.TextLines getLines(Text text, int lineWidth) {
        throw new AssertionError();
    }

    @Inject(method = RENDER_METHOD, at = @At("HEAD"), cancellable = true)
    private void universalTranslator$prepareTextDisplay(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.Data source,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float tickProgress,
            CallbackInfo callback
    ) {
        universalTranslator$translatedLines = null;
        universalTranslator$hasTranslatedFlags = false;
        if (source == null || source.text() == null) {
            return;
        }

        PlayerFollowHologramTracker.Match playerFollow = detectPlayerFollow(entity);
        if (playerFollow.following() && playerFollow.localPlayer()
                && MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            callback.cancel();
            return;
        }

        AffineTransformation transformation = transformation(entity, tickProgress);
        Vector3f localTranslation = transformation.getTranslation();
        Vector3f scale = transformation.getScale();
        DisplayEntity.TextDisplayEntity.TextLines originalLines =
                getLines(source.text(), source.lineWidth());
        HologramTextDisplayGroups.Result result = playerFollow.following()
                ? null
                : HologramTextDisplayGroups.translate(
                        System.identityHashCode(entity.getEntityWorld()),
                        entity.getId(), entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYaw(), entity.getPitch(), orientationHash(transformation),
                        source.text(), localTranslation.x(), localTranslation.y(), scale.x(),
                        originalLines.width(), alignment(source.flags()));
        Text translated = playerFollow.following()
                ? RenderedTextBridge.translateHologramText(source.text())
                : result.text();
        if (translated == source.text()) {
            universalTranslator$translatedLines = bypassLines(originalLines);
            return;
        }

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
        if (translatedLineWidth != layoutLimit) {
            translatedLines = getLines(translated, translatedLineWidth);
        }
        universalTranslator$translatedLines = bypassLines(translatedLines);
        universalTranslator$translatedFlags = flags;
        universalTranslator$hasTranslatedFlags = true;

        float horizontal = playerFollow.following() ? 0.0F : result.horizontalOffset();
        float vertical = playerFollow.following()
                ? PLAYER_FOLLOW_UP_OFFSET
                : HologramTopAnchor.offset(
                        lineCount(originalLines), lineCount(universalTranslator$translatedLines));
        if (horizontal != 0.0F || vertical != 0.0F) {
            matrices.translate(horizontal, vertical, 0.0F);
        }
    }

    @Redirect(
            method = RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;splitLines("
                            + "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;)"
                            + "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;"))
    private DisplayEntity.TextDisplayEntity.TextLines universalTranslator$useTranslatedLines(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.LineSplitter splitter
    ) {
        return universalTranslator$translatedLines == null
                ? entity.splitLines(splitter) : universalTranslator$translatedLines;
    }

    @Redirect(
            method = RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;flags()B"))
    private byte universalTranslator$useTranslatedFlags(
            DisplayEntity.TextDisplayEntity.Data data) {
        return universalTranslator$hasTranslatedFlags
                ? universalTranslator$translatedFlags : data.flags();
    }

    @Inject(method = RENDER_METHOD, at = @At("RETURN"))
    private void universalTranslator$clearTextDisplay(
            DisplayEntity.TextDisplayEntity entity,
            DisplayEntity.TextDisplayEntity.Data source,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float tickProgress,
            CallbackInfo callback
    ) {
        universalTranslator$translatedLines = null;
        universalTranslator$hasTranslatedFlags = false;
    }

    private static AffineTransformation transformation(
            DisplayEntity.TextDisplayEntity entity, float tickProgress) {
        DisplayEntity.RenderState state = entity.getRenderState();
        if (state == null || state.transformation() == null) {
            return AffineTransformation.identity();
        }
        return state.transformation().interpolate(entity.getLerpProgress(tickProgress));
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
        Vector3f scale = transformation.getScale();
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

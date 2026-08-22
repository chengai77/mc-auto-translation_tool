package org.universaltranslator.fabric.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.RenderedTextBridge;

/** 翻译名牌状态 */
@Mixin(EntityRenderer.class)
abstract class EntityNameRenderStateMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void universalTranslator$translateNameTag(
            Entity entity,
            EntityRenderState state,
            float tickProgress,
            CallbackInfo callback) {
        if (entity instanceof ArmorStand
                && (entity.isInvisible() || ((ArmorStand) entity).isMarker())) {
            return;
        }
        state.nameTag = RenderedTextBridge.translateEntityName(state.nameTag);
        state.scoreText = RenderedTextBridge.translateEntityName(state.scoreText);
    }
}

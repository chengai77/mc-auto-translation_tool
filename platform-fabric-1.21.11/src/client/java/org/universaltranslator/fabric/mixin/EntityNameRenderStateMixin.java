package org.universaltranslator.fabric.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.RenderedTextBridge;

/** 翻译名牌状态 */
@Mixin(EntityRenderer.class)
abstract class EntityNameRenderStateMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void universalTranslator$translateNameTag(
            Entity entity,
            EntityRenderState state,
            float tickProgress,
            CallbackInfo callback) {
        state.displayName = RenderedTextBridge.translateEntityName(state.displayName);
    }
}

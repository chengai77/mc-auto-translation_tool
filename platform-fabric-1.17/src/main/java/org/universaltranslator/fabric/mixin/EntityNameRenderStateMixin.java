package org.universaltranslator.fabric.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.RenderedTextBridge;

/** 翻译实体名牌 */
@Mixin(EntityRenderer.class)
abstract class EntityNameRenderStateMixin {
    /** 第一人称隐藏自身名牌，避免挡脸 */
    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$hideFirstPersonSelfName(
            Entity entity,
            Text text,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo callback
    ) {
        if (RenderedTextBridge.shouldHideEntityName(entity)) {
            callback.cancel();
        }
    }

    @ModifyVariable(
            method = "renderLabelIfPresent",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Text universalTranslator$translateNameTag(Text name, Entity entity) {
        return RenderedTextBridge.translateEntityName(entity, name);
    }
}

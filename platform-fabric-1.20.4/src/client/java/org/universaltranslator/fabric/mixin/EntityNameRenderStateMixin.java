package org.universaltranslator.fabric.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.universaltranslator.fabric.RenderedTextBridge;

/** 翻译实体名牌 */
@Mixin(EntityRenderer.class)
abstract class EntityNameRenderStateMixin {
    @ModifyVariable(
            method = "renderLabelIfPresent",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private Text universalTranslator$translateNameTag(Text name, Entity entity) {
        return RenderedTextBridge.translateEntityName(entity, name);
    }
}

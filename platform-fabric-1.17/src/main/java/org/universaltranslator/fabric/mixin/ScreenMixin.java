package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.universaltranslator.fabric.RenderedTextBridge;

import java.util.List;

/** 物品提示前翻译 */
@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
    private void universalTranslator$translateItemTooltip(
            ItemStack stack,
            CallbackInfoReturnable<List<Text>> callback) {
        callback.setReturnValue(RenderedTextBridge.translateItemTooltip(callback.getReturnValue()));
    }

    @ModifyVariable(
            method = "renderTooltip(Lnet/minecraft/client/util/math/MatrixStack;Ljava/util/List;II)V",
            at = @At("HEAD"), argsOnly = true)
    private List<Text> universalTranslator$translateTooltipLines(List<Text> lines) {
        return RenderedTextBridge.translateTooltip(lines);
    }
}

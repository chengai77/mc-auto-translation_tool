package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.universaltranslator.neoforge.RenderedTextBridge;

import java.util.List;

/** 物品提示前翻译 */
@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(
            method = "getTooltipFromItem(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true)
    private static void universalTranslator$translateItemTooltip(
            MinecraftClient client,
            ItemStack stack,
            CallbackInfoReturnable<List<Text>> callback) {
        callback.setReturnValue(RenderedTextBridge.translateItemTooltip(callback.getReturnValue()));
    }
}

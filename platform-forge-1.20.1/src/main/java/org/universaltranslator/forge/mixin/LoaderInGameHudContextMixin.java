package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.forge.RenderedTextBridge;
import org.universaltranslator.forge.TranslationRenderContext;

/** 加载器物品名适配 */
@Mixin(InGameHud.class)
abstract class LoaderInGameHudContextMixin {
    @ModifyVariable(
            method = "renderSelectedItemName",
            at = @At(value = "STORE"),
            ordinal = 0)
    private Text universalTranslator$translateHeldItemName(Text name) {
        return RenderedTextBridge.translateHeldItemName(name);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"))
    private void universalTranslator$suppressHeldItemText(
            DrawContext context, int yShift, CallbackInfo callback) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "renderSelectedItemName", at = @At("RETURN"))
    private void universalTranslator$restoreHeldItemText(
            DrawContext context, int yShift, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

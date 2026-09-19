package org.universaltranslator.forge.mixin;

import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.forge.RenderedTextBridge;
import org.universaltranslator.forge.TranslationRenderContext;

/** 整页翻译书本 */
@Mixin(BookScreen.class)
abstract class BookScreenMixin {
    @Shadow
    private int cachedPageIndex;

    @Inject(
            method = "render(Lnet/minecraft/client/font/DrawnTextConsumer;Z)V",
            at = @At("HEAD"))
    private void universalTranslator$suppressBookLineHooks(
            DrawnTextConsumer consumer, boolean hidden, CallbackInfo callback) {
        RenderedTextBridge.beginBookPageRender();
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/font/DrawnTextConsumer;Z)V",
            at = @At("RETURN"))
    private void universalTranslator$restoreBookLineHooks(
            DrawnTextConsumer consumer, boolean hidden, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
        if (RenderedTextBridge.consumeBookPagePending()) {
            cachedPageIndex = -1;
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/font/DrawnTextConsumer;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/BookScreen$Contents;getPage(I)Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translatePage(BookScreen.Contents contents, int pageIndex) {
        return RenderedTextBridge.translateBookPage(contents.getPage(pageIndex));
    }
}


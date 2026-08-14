package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationRenderContext;

/** 整页翻译书本 */
@Mixin(BookViewScreen.class)
abstract class BookScreenMixin {
    @Shadow
    private int cachedPage;

    @Inject(
            method = "visitText(Lnet/minecraft/client/gui/ActiveTextCollector;Z)V",
            at = @At("HEAD"))
    private void universalTranslator$suppressBookLineHooks(
            ActiveTextCollector collector, boolean hidden, CallbackInfo callback) {
        RenderedTextBridge.beginBookPageRender();
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(
            method = "visitText(Lnet/minecraft/client/gui/ActiveTextCollector;Z)V",
            at = @At("RETURN"))
    private void universalTranslator$restoreBookLineHooks(
            ActiveTextCollector collector, boolean hidden, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
        if (RenderedTextBridge.consumeBookPagePending()) {
            cachedPage = -1;
        }
    }

    @Redirect(
            method = "visitText(Lnet/minecraft/client/gui/ActiveTextCollector;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;getPage(I)Lnet/minecraft/network/chat/Component;"))
    private Component universalTranslator$translatePage(BookViewScreen.BookAccess bookAccess, int pageIndex) {
        return RenderedTextBridge.translateBookPage(bookAccess.getPage(pageIndex));
    }
}

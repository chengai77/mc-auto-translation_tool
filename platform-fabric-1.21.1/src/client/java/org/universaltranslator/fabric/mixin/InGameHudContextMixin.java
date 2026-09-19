package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.DownloadStatusOverlay;
import org.universaltranslator.fabric.TranslationLogOverlay;
import org.universaltranslator.fabric.TranslationRenderContext;

@Mixin(InGameHud.class)
abstract class InGameHudContextMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$renderPinnedLog(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationLogOverlay.render(context, 0.0F);
        DownloadStatusOverlay.render(context);
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadTitle(Text title, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(title, TextKind.TITLE, false)) {
            callback.cancel();
        }
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadSubtitle(Text subtitle, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(subtitle, TextKind.SUBTITLE, false)) {
            callback.cancel();
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadOverlayMessage(
            Text message, boolean tinted, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(message, TextKind.ACTION_BAR, tinted)) {
            callback.cancel();
        }
    }

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void universalTranslator$enterChat(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.CHAT);
    }

    @Inject(method = "renderChat", at = @At("RETURN"))
    private void universalTranslator$leaveChat(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterScoreboard(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.SCOREBOARD_LINE);
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveScoreboard(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(method = "renderPlayerList", at = @At("HEAD"))
    private void universalTranslator$enterPlayerList(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.PLAYER_LIST_HEADER);
    }

    @Inject(method = "renderPlayerList", at = @At("RETURN"))
    private void universalTranslator$leavePlayerList(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"))
    private void universalTranslator$enterTitle(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.TITLE);
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("RETURN"))
    private void universalTranslator$leaveTitle(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void universalTranslator$enterItemNameOverlay(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.ACTION_BAR);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void universalTranslator$leaveActionBar(
            DrawContext context, RenderTickCounter tickCounter, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Redirect(
            method = "renderHeldItemTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;getName()Lnet/minecraft/text/Text;"),
            require = 0)
    private Text universalTranslator$translateHeldItemName(ItemStack stack) {
        return RenderedTextBridge.translateHeldItemName(stack.getName());
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"))
    private void universalTranslator$suppressHeldItemLowLevelText(
            DrawContext context, CallbackInfo callback) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("RETURN"))
    private void universalTranslator$restoreHeldItemLowLevelText(
            DrawContext context, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow
    private Text overlayMessage;

    @Shadow
    private Text title;

    @Shadow
    private Text subtitle;

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$renderPinnedLog(
            DrawContext context, float tickDelta, CallbackInfo callback) {
        TranslationLogOverlay.render(context, tickDelta);
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

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterScoreboard(
            DrawContext context, ScoreboardObjective objective, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.SCOREBOARD_LINE);
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveScoreboard(
            DrawContext context, ScoreboardObjective objective, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;"
                            + "overlayMessage:Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translateOverlayMessage(InGameHud hud) {
        return RenderedTextBridge.translateHudText(overlayMessage, TextKind.ACTION_BAR);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;"
                            + "title:Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translateTitle(InGameHud hud) {
        return RenderedTextBridge.translateHudText(title, TextKind.TITLE);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;F)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;"
                            + "subtitle:Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translateSubtitle(InGameHud hud) {
        return RenderedTextBridge.translateHudText(subtitle, TextKind.SUBTITLE);
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

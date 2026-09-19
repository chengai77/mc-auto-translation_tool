package org.universaltranslator.neoforge.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.neoforge.RenderedTextBridge;
import org.universaltranslator.neoforge.DownloadStatusOverlay;
import org.universaltranslator.neoforge.TranslationLogOverlay;
import org.universaltranslator.neoforge.TranslationRenderContext;

@Mixin(Hud.class)
abstract class InGameHudContextMixin {
    @Inject(
            method = "extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterChat(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.CHAT);
    }

    @Inject(
            method = "extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveChat(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(
            method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterScoreboard(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.SCOREBOARD_LINE);
    }

    @Inject(
            method = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveScoreboard(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(
            method = "extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterPlayerList(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.PLAYER_LIST_HEADER);
    }

    @Inject(
            method = "extractTabList(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void universalTranslator$leavePlayerList(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(
            method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterTitle(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.TITLE);
    }

    @Inject(
            method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveTitle(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadTitle(Component title, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(title, TextKind.TITLE, false)) {
            callback.cancel();
        }
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadSubtitle(Component subtitle, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(subtitle, TextKind.SUBTITLE, false)) {
            callback.cancel();
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadActionBar(
            Component message, boolean tinted, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(message, TextKind.ACTION_BAR, tinted)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterItemNameOverlay(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.ACTION_BAR);
    }

    @Inject(
            method = "extractOverlayMessage(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveActionBar(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
        TranslationRenderContext.pop();
        TranslationLogOverlay.extract(graphics);
        DownloadStatusOverlay.extract(graphics);
    }

    @Redirect(
            method = "extractSelectedItemName",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component universalTranslator$translateHeldItemName(ItemStack stack) {
        return RenderedTextBridge.translateHeldItemName(stack.getHoverName());
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void universalTranslator$suppressHeldItemLowLevelText(
            GuiGraphicsExtractor graphics, CallbackInfo callback) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void universalTranslator$restoreHeldItemLowLevelText(
            GuiGraphicsExtractor graphics, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
    }
}


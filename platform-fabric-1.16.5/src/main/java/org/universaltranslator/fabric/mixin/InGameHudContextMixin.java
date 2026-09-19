package org.universaltranslator.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.MessageType;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.ChatMessageOrigin;
import org.universaltranslator.fabric.DownloadStatusOverlay;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationLogOverlay;
import org.universaltranslator.fabric.TranslationRenderContext;

import java.util.UUID;

@Mixin(InGameHud.class)
abstract class InGameHudContextMixin {
    @Shadow
    private Text overlayMessage;

    @Shadow
    private Text title;

    @Shadow
    private Text subtitle;

    @Unique
    private boolean universalTranslator$redispatchingTitles;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("RETURN"))
    private void universalTranslator$renderPinnedLog(
            MatrixStack matrices, float tickDelta, CallbackInfo callback) {
        TranslationLogOverlay.render(matrices, tickDelta);
        DownloadStatusOverlay.render(matrices);
    }

    /** 1.16.5 标题字幕共用一个入口 */
    @Inject(method = "setTitles", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadTitles(
            Text newTitle,
            Text newSubtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks,
            CallbackInfo callback
    ) {
        if (universalTranslator$redispatchingTitles) {
            return;
        }
        boolean blockTitle = newTitle != null
                && RenderedTextBridge.preloadUrgentHudText(newTitle, TextKind.TITLE, false);
        boolean blockSubtitle = newSubtitle != null
                && RenderedTextBridge.preloadUrgentHudText(newSubtitle, TextKind.SUBTITLE, false);
        if (!blockTitle && !blockSubtitle) {
            return;
        }
        callback.cancel();
        if (blockTitle == blockSubtitle) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        // 只等待未就绪项，另一项立即补发
        universalTranslator$redispatchingTitles = true;
        try {
            client.inGameHud.setTitles(
                    blockTitle ? null : newTitle,
                    blockSubtitle ? null : newSubtitle,
                    fadeInTicks, stayTicks, fadeOutTicks);
        } finally {
            universalTranslator$redispatchingTitles = false;
        }
    }

    /** 标记玩家聊天来源 */
    @Inject(method = "addChatMessage", at = @At("HEAD"))
    private void universalTranslator$markChatOrigin(
            MessageType type, Text message, UUID sender, CallbackInfo callback) {
        ChatMessageOrigin.begin(type == MessageType.CHAT);
    }

    @Inject(method = "addChatMessage", at = @At("RETURN"))
    private void universalTranslator$clearChatOrigin(
            MessageType type, Text message, UUID sender, CallbackInfo callback) {
        ChatMessageOrigin.clear();
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$preloadOverlayMessage(
            Text message, boolean tinted, CallbackInfo callback) {
        if (RenderedTextBridge.preloadUrgentHudText(message, TextKind.ACTION_BAR, tinted)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"))
    private void universalTranslator$enterScoreboard(
            MatrixStack matrices, ScoreboardObjective objective, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.SCOREBOARD_LINE);
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"))
    private void universalTranslator$leaveScoreboard(
            MatrixStack matrices, ScoreboardObjective objective, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;"
                            + "overlayMessage:Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translateOverlayMessage(InGameHud hud) {
        return RenderedTextBridge.translateHudText(overlayMessage, TextKind.ACTION_BAR);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;"
                            + "title:Lnet/minecraft/text/Text;"))
    private Text universalTranslator$translateTitle(InGameHud hud) {
        return RenderedTextBridge.translateHudText(title, TextKind.TITLE);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V",
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

    @Inject(
            method = "renderHeldItemTooltip(Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"))
    private void universalTranslator$suppressHeldItemLowLevelText(
            MatrixStack matrices, CallbackInfo callback) {
        TranslationRenderContext.pushSuppressTranslation();
    }

    @Inject(
            method = "renderHeldItemTooltip(Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("RETURN"))
    private void universalTranslator$restoreHeldItemLowLevelText(
            MatrixStack matrices, CallbackInfo callback) {
        TranslationRenderContext.popSuppressTranslation();
    }
}

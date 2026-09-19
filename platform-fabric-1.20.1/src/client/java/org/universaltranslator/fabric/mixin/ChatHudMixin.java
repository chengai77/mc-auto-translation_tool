package org.universaltranslator.fabric.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;
import org.universaltranslator.fabric.TranslationRenderContext;

import java.util.ArrayList;
import java.util.List;

/** 聊天整句翻译 */
@Mixin(ChatHud.class)
abstract class ChatHudMixin {
    private static final String ADD_MESSAGE_METHOD =
            "addMessage(Lnet/minecraft/text/Text;"
                    + "Lnet/minecraft/network/message/MessageSignatureData;I"
                    + "Lnet/minecraft/client/gui/hud/MessageIndicator;Z)V";

    @Unique
    private boolean universalTranslator$playerMessage;

    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$enterChat(
            DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.CHAT);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$leaveChat(
            DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo callback) {
        TranslationRenderContext.pop();
    }

    @Inject(method = ADD_MESSAGE_METHOD, at = @At("HEAD"))
    private void universalTranslator$captureMessageType(
            Text message,
            MessageSignatureData signature,
            int ticks,
            MessageIndicator indicator,
            boolean refresh,
            CallbackInfo callback
    ) {
        universalTranslator$playerMessage = signature != null;
    }

    @Redirect(
            method = ADD_MESSAGE_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/ChatMessages;"
                            + "breakRenderedChatMessageLines(Lnet/minecraft/text/StringVisitable;"
                            + "ILnet/minecraft/client/font/TextRenderer;)Ljava/util/List;"))
    private List<OrderedText> universalTranslator$translateAndBreakLines(
            StringVisitable message, int width, TextRenderer renderer) {
        StringVisitable translated = RenderedTextBridge.translateChatMessage(
                message, universalTranslator$playerMessage);
        List<OrderedText> lines = ChatMessages.breakRenderedChatMessageLines(
                translated, width, renderer);
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        List<OrderedText> wrapped = new ArrayList<OrderedText>(lines.size());
        for (OrderedText line : lines) {
            wrapped.add(TranslationBypassText.wrap(line));
        }
        return wrapped;
    }

    @Inject(method = ADD_MESSAGE_METHOD, at = @At("RETURN"))
    private void universalTranslator$clearMessageType(
            Text message,
            MessageSignatureData signature,
            int ticks,
            MessageIndicator indicator,
            boolean refresh,
            CallbackInfo callback
    ) {
        universalTranslator$playerMessage = false;
    }
}

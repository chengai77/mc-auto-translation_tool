package org.universaltranslator.fabric.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.ChatMessageOrigin;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;
import org.universaltranslator.fabric.TranslationRenderContext;

import java.util.ArrayList;
import java.util.List;

/** 聊天整句翻译 */
@Mixin(ChatHud.class)
abstract class ChatHudMixin {
    // 带时序参数的聊天入口
    private static final String ADD_MESSAGE_METHOD = "addMessage(Lnet/minecraft/text/Text;IIZ)V";

    @Inject(method = "render", at = @At("HEAD"))
    private void universalTranslator$enterChat(
            MatrixStack matrices, int tickDelta, CallbackInfo callback) {
        TranslationRenderContext.push(TextKind.CHAT);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void universalTranslator$leaveChat(
            MatrixStack matrices, int tickDelta, CallbackInfo callback) {
        TranslationRenderContext.pop();
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
                message, ChatMessageOrigin.consumePlayerMessage());
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
}

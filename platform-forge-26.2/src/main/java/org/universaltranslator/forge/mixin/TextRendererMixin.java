package org.universaltranslator.forge.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.forge.RenderedTextBridge;
import org.universaltranslator.forge.TranslationBypassText;
import org.universaltranslator.forge.TranslationRenderContext;

/** 捕获世界文本 */
@Mixin(Font.class)
abstract class TextRendererMixin {
    @Shadow
    public abstract int width(String text);

    @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translatePreparedString(String text) {
        return RenderedTextBridge.translate(
                text, this::width, TranslationRenderContext.currentOr(defaultKind()));
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence universalTranslator$translatePreparedOrderedText(
            FormattedCharSequence text) {
        if (TranslationBypassText.isWrapped(text)) {
            return text;
        }
        return RenderedTextBridge.translate(
                text, TranslationRenderContext.currentOr(defaultKind()));
    }

    private static TextKind defaultKind() {
        return TextKind.OTHER;
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translateMeasuredString(String text) {
        return text;
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
            at = @At("HEAD"), argsOnly = true)
    private FormattedText universalTranslator$translateMeasuredText(FormattedText text) {
        return text;
    }

    @ModifyVariable(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence universalTranslator$translateMeasuredOrderedText(
            FormattedCharSequence text) {
        return text;
    }
}


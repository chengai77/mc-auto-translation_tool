package org.universaltranslator.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationRenderContext;

/** Captures world-space text such as nameplates, holograms, signs and display entities. */
@Mixin(TextRenderer.class)
abstract class TextRendererMixin {
    @Shadow
    public abstract int getWidth(String text);

    @ModifyVariable(
            method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translatePreparedString(String text) {
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(defaultKind()));
    }

    @ModifyVariable(
            method = "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText universalTranslator$translatePreparedOrderedText(OrderedText text) {
        return RenderedTextBridge.translate(
                text, this::getWidth, TranslationRenderContext.currentOr(defaultKind()));
    }

    private static TextKind defaultKind() {
        return MinecraftClient.getInstance().currentScreen == null ? TextKind.HOLOGRAM : TextKind.OTHER;
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String universalTranslator$translateMeasuredString(String text) {
        return text;
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/StringVisitable;)I",
            at = @At("HEAD"), argsOnly = true)
    private StringVisitable universalTranslator$translateMeasuredText(StringVisitable text) {
        return text;
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText universalTranslator$translateMeasuredOrderedText(OrderedText text) {
        return text;
    }
}

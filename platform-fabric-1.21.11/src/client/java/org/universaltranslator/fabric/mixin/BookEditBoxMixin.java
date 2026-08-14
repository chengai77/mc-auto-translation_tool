package org.universaltranslator.fabric.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.TranslationRenderContext;

@Mixin(EditBoxWidget.class)
abstract class BookEditBoxMixin {
    @Unique
    private boolean universalTranslator$bookTextContext;

    @Inject(
            method = "renderContents(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"))
    private void universalTranslator$pushBookText(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof BookEditScreen) {
            universalTranslator$bookTextContext = true;
            TranslationRenderContext.push(TextKind.BOOK);
        }
    }

    @Inject(
            method = "renderContents(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("RETURN"))
    private void universalTranslator$popBookText(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (universalTranslator$bookTextContext) {
            universalTranslator$bookTextContext = false;
            TranslationRenderContext.pop();
        }
    }
}

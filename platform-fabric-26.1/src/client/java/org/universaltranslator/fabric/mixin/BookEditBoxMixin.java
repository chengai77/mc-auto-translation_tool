package org.universaltranslator.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.core.TextKind;
import org.universaltranslator.fabric.TranslationRenderContext;

@Mixin(MultiLineEditBox.class)
abstract class BookEditBoxMixin {
    @Unique
    private boolean universalTranslator$bookTextContext;

    @Inject(
            method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"))
    private void universalTranslator$pushBookText(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (Minecraft.getInstance().screen instanceof BookEditScreen) {
            universalTranslator$bookTextContext = true;
            TranslationRenderContext.push(TextKind.BOOK);
        }
    }

    @Inject(
            method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void universalTranslator$popBookText(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (universalTranslator$bookTextContext) {
            universalTranslator$bookTextContext = false;
            TranslationRenderContext.pop();
        }
    }
}

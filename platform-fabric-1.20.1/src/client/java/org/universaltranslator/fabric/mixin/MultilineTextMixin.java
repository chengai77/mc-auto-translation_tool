package org.universaltranslator.fabric.mixin;

import net.minecraft.client.font.MultilineText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.universaltranslator.fabric.RenderedTextBridge;
import org.universaltranslator.fabric.TranslationBypassText;

import java.util.ArrayList;
import java.util.List;

/** 整段翻译多行文本 */
@Mixin(MultilineText.class)
interface MultilineTextMixin {
    @Redirect(
            method = {
                    "create(Lnet/minecraft/client/font/TextRenderer;"
                            + "Lnet/minecraft/text/StringVisitable;I)"
                            + "Lnet/minecraft/client/font/MultilineText;",
                    "create(Lnet/minecraft/client/font/TextRenderer;"
                            + "Lnet/minecraft/text/StringVisitable;II)"
                            + "Lnet/minecraft/client/font/MultilineText;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;"
                            + "wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"))
    private static List<OrderedText> universalTranslator$translateBeforeWrapping(
            TextRenderer renderer, StringVisitable text, int width) {
        StringVisitable translated = RenderedTextBridge.translateUiText(text);
        List<OrderedText> lines = renderer.wrapLines(translated, width);
        List<OrderedText> wrapped = new ArrayList<OrderedText>(lines.size());
        for (OrderedText line : lines) {
            wrapped.add(TranslationBypassText.wrap(line));
        }
        return wrapped;
    }
}

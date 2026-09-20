package org.universaltranslator.fabric.mixin;

import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.universaltranslator.fabric.RenderedTextBridge;

/** 动态刷新界面段落 */
@Mixin(MultilineTextWidget.class)
abstract class MultilineTextWidgetMixin {
    @ModifyArg(
            method = "getCacheKey",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/MultilineTextWidget$CacheKey;"
                            + "<init>(Lnet/minecraft/text/Text;ILjava/util/OptionalInt;)V"),
            index = 0)
    private Text universalTranslator$translateParagraph(Text message) {
        StringVisitable translated = RenderedTextBridge.translateUiText(message);
        return translated instanceof Text ? (Text) translated : message;
    }
}

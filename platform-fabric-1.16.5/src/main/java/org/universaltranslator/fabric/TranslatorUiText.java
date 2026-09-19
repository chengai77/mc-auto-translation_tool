package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** 约束界面文字 */
final class TranslatorUiText {
    private static final String ELLIPSIS = "...";
    /** 按钮对应的完整文本，用于悬停提示 */
    private static final Map<ButtonWidget, Text> FULL_TEXTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TranslatorUiText() {
    }

    /** 空文本，等价高版本 Text.empty() */
    static Text empty() {
        return new LiteralText("");
    }

    /** 创建按钮，1.16.5 无 builder */
    static ButtonWidget button(
            int x,
            int y,
            int width,
            int height,
            Text message,
            ButtonWidget.PressAction action
    ) {
        ButtonWidget widget = new ButtonWidget(
                x, y, width, height, message, action, TranslatorUiText::renderFullTextTooltip);
        FULL_TEXTS.put(widget, message);
        return widget;
    }

    static void setButtonMessage(TextRenderer renderer, ButtonWidget button, Text message) {
        int availableWidth = Math.max(1, button.getWidth() - 12);
        boolean clipped = renderer.getWidth(message) > availableWidth;
        button.setMessage(clipped ? fit(renderer, message, availableWidth) : message);
        FULL_TEXTS.put(button, clipped ? message : null);
    }

    static void drawCentered(
            MatrixStack matrices,
            TextRenderer renderer,
            Text text,
            int centerX,
            int y,
            int color,
            int maxWidth
    ) {
        if (text == null) {
            return;
        }
        DrawableHelper.drawCenteredText(matrices, renderer, fit(renderer, text, maxWidth), centerX, y, color);
    }

    static void drawLeft(
            MatrixStack matrices,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        if (text == null) {
            return;
        }
        DrawableHelper.drawTextWithShadow(matrices, renderer, fit(renderer, text, maxWidth), x, y, color);
    }

    private static Text fit(TextRenderer renderer, Text text, int maxWidth) {
        if (text == null || maxWidth <= 0 || renderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int contentWidth = Math.max(1, maxWidth - renderer.getWidth(ELLIPSIS));
        String trimmed = renderer.trimToWidth(text.getString(), contentWidth);
        return new LiteralText(trimmed + ELLIPSIS).setStyle(text.getStyle());
    }

    /** 悬停时显示被截断前的完整文本 */
    private static void renderFullTextTooltip(
            ButtonWidget button,
            MatrixStack matrices,
            int mouseX,
            int mouseY
    ) {
        Text fullText = FULL_TEXTS.get(button);
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (fullText == null || screen == null) {
            return;
        }
        screen.renderTooltip(matrices, fullText, mouseX, mouseY);
    }
}

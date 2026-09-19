package org.universaltranslator.neoforge;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** 约束界面文字 */
final class TranslatorUiText {
    private static final String ELLIPSIS = "...";

    private TranslatorUiText() {
    }

    static void setButtonMessage(TextRenderer renderer, ButtonWidget button, Text message) {
        int availableWidth = Math.max(1, button.getWidth() - 12);
        boolean clipped = renderer.getWidth(message) > availableWidth;
        button.setMessage(clipped ? fit(renderer, message, availableWidth) : message);
        button.setTooltip(clipped ? Tooltip.of(message) : null);
    }

    static void drawCentered(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int centerX,
            int y,
            int color,
            int maxWidth
    ) {
        context.drawCenteredTextWithShadow(renderer, fit(renderer, text, maxWidth), centerX, y, color);
    }

    static void drawLeft(
            DrawContext context,
            TextRenderer renderer,
            Text text,
            int x,
            int y,
            int color,
            int maxWidth
    ) {
        context.drawTextWithShadow(renderer, fit(renderer, text, maxWidth), x, y, color);
    }

    private static Text fit(TextRenderer renderer, Text text, int maxWidth) {
        if (text == null || maxWidth <= 0 || renderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int contentWidth = Math.max(1, maxWidth - renderer.getWidth(ELLIPSIS));
        String trimmed = renderer.trimToWidth(text.getString(), contentWidth);
        return Text.literal(trimmed + ELLIPSIS).setStyle(text.getStyle());
    }
}


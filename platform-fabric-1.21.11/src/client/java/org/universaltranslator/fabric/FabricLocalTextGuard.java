package org.universaltranslator.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.universaltranslator.core.TranslationTextStyling;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** 本地输入识别 */
final class FabricLocalTextGuard {
    private FabricLocalTextGuard() {
    }

    static boolean isLocalInput(MinecraftClient client, String rendered) {
        if (client == null || rendered == null || rendered.isEmpty()) {
            return false;
        }
        for (TextFieldWidget field : findTextFields(client.currentScreen)) {
            if (field.isFocused() && matches(field.getText(), rendered)) {
                return true;
            }
        }
        return false;
    }

    private static List<TextFieldWidget> findTextFields(Screen screen) {
        List<TextFieldWidget> fields = new ArrayList<TextFieldWidget>();
        if (screen == null) {
            return fields;
        }
        for (Object child : screen.children()) {
            if (child instanceof TextFieldWidget) {
                fields.add((TextFieldWidget) child);
            }
        }
        for (Class<?> current = screen.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!TextFieldWidget.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof TextFieldWidget) {
                        fields.add((TextFieldWidget) value);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return fields;
    }

    private static boolean matches(String typed, String rendered) {
        if (typed == null || typed.isEmpty()) {
            return false;
        }
        String visible = TranslationTextStyling.stripLegacyFormatting(rendered);
        return visible.equals(typed) || visible.equals(typed + "_")
                || visible.length() >= 2 && typed.contains(visible);
    }
}

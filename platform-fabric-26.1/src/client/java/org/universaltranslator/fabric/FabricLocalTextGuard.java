package org.universaltranslator.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.universaltranslator.core.TranslationTextStyling;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** 本地输入识别 */
final class FabricLocalTextGuard {
    private FabricLocalTextGuard() {
    }

    static boolean isLocalInput(Minecraft client, String rendered) {
        if (client == null || rendered == null || rendered.isEmpty()) {
            return false;
        }
        for (EditBox field : findTextFields(client.screen)) {
            if (field.isFocused() && matches(field.getValue(), rendered)) {
                return true;
            }
        }
        return false;
    }

    private static List<EditBox> findTextFields(Screen screen) {
        List<EditBox> fields = new ArrayList<EditBox>();
        if (screen == null) {
            return fields;
        }
        for (Object child : screen.children()) {
            if (child instanceof EditBox) {
                fields.add((EditBox) child);
            }
        }
        for (Class<?> current = screen.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!EditBox.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof EditBox) {
                        fields.add((EditBox) value);
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

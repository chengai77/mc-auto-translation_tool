package org.universaltranslator.core;

/** 语言键适配器 */
public interface UiTranslator {
    String translate(String key, Object... arguments);
}

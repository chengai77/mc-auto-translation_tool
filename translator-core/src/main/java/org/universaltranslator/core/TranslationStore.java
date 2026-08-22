package org.universaltranslator.core;

/** 缓存抽象层 */
public interface TranslationStore {
    String get(String key);

    void put(String key, String value);

    void clear();
}

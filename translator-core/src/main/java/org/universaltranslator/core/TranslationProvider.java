package org.universaltranslator.core;

/** 翻译引擎接口 */
public interface TranslationProvider {
    String id();

    String translate(TranslationRequest request) throws Exception;
}

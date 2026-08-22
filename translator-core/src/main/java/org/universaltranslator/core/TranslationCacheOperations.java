package org.universaltranslator.core;

import java.io.IOException;
import java.nio.file.Path;

/** 缓存文件操作 */
public final class TranslationCacheOperations {
    private TranslationCacheOperations() {
    }

    public static int importInto(TranslationStore store, Path source) throws IOException {
        if (store instanceof PersistentTranslationCache) {
            return ((PersistentTranslationCache) store).importFrom(source);
        }
        if (store instanceof TranslationCache) {
            return ((TranslationCache) store).importFrom(source);
        }
        throw new IOException("Unsupported cache store");
    }

    public static Path exportFrom(TranslationStore store, Path target) throws IOException {
        if (store instanceof PersistentTranslationCache) {
            return ((PersistentTranslationCache) store).exportTo(target);
        }
        if (store instanceof TranslationCache) {
            return ((TranslationCache) store).exportTo(target);
        }
        throw new IOException("Unsupported cache store");
    }

    public static void clear(TranslationStore store) throws IOException {
        if (store instanceof PersistentTranslationCache) {
            ((PersistentTranslationCache) store).clearFile();
            return;
        }
        if (store instanceof TranslationCache) {
            ((TranslationCache) store).clear();
            return;
        }
        throw new IOException("Unsupported cache store");
    }
}

package org.universaltranslator.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** 线程安全LRU缓存 */
public final class TranslationCache implements TranslationStore {
    private final Map<String, String> entries;

    public TranslationCache(final int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.entries = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maximumEntries;
            }
        };
    }

    @Override
    public synchronized String get(String key) {
        String value = entries.get(key);
        return value == null ? entries.get(TranslationCacheFile.hashKey(key)) : value;
    }

    @Override
    public synchronized void put(String key, String value) {
        entries.put(key, value);
    }

    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int importFrom(Path source) throws IOException {
        Properties properties = TranslationCacheFile.read(source);
        int imported = 0;
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key != null && value != null && !key.trim().isEmpty()) {
                entries.put(key, value);
                imported++;
            }
        }
        return imported;
    }

    public synchronized Path exportTo(Path target) throws IOException {
        return TranslationCacheFile.write(target, entries);
    }
}

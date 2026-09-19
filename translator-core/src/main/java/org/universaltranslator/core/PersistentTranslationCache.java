package org.universaltranslator.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Small disk cache that hashes source/cache keys before persistence. Translated values are
 * saved locally as plain UTF-8 text so repeated/restarted sessions can render them immediately.
 * Disk write failures never break translation and the in-memory value remains usable.
 */
public final class PersistentTranslationCache implements TranslationStore {
    private static final Pattern HASH_KEY = Pattern.compile("[0-9a-fA-F]{64}");
    private final Path file;
    private final Map<String, String> entries;

    public PersistentTranslationCache(Path file, final int maximumEntries) throws IOException {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.file = file;
        this.entries = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maximumEntries;
            }
        };
        load();
    }

    @Override
    public synchronized String get(String key) {
        return entries.get(TranslationCacheFile.hashKey(key));
    }

    @Override
    public synchronized void put(String key, String value) {
        entries.put(TranslationCacheFile.hashKey(key), value);
        persistBestEffort();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 只清内存
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Path exportTo(Path target) throws IOException {
        return TranslationCacheFile.write(target, entries);
    }

    public synchronized int importFrom(Path source) throws IOException {
        Properties properties = TranslationCacheFile.read(source);
        Map<String, String> previous = new LinkedHashMap<String, String>(entries);
        int imported = 0;
        try {
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key != null && value != null && !key.trim().isEmpty()) {
                    // 哈希键可直接复用
                    entries.put(isHashKey(key) ? key.toLowerCase()
                            : TranslationCacheFile.hashKey(key), value);
                    imported++;
                }
            }
            TranslationCacheFile.write(file, entries);
            return imported;
        } catch (IOException failure) {
            entries.clear();
            entries.putAll(previous);
            throw failure;
        }
    }

    public synchronized void clearFile() throws IOException {
        entries.clear();
        Files.deleteIfExists(file);
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        Properties properties;
        try {
            properties = TranslationCacheFile.read(file);
        } catch (IOException malformedCache) {
            return;
        }
        boolean normalized = false;
        for (String key : properties.stringPropertyNames()) {
            String storedKey = isHashKey(key) ? key.toLowerCase()
                    : TranslationCacheFile.hashKey(key);
            entries.put(storedKey, properties.getProperty(key));
            normalized |= !storedKey.equals(key);
        }
        if (normalized) {
            persistBestEffort();
        }
    }

    private void persistBestEffort() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            TranslationCacheFile.write(file, entries);
        } catch (IOException ignored) {
            // 只读缓存可用
        }
    }

    private static boolean isHashKey(String key) {
        return HASH_KEY.matcher(key.trim()).matches();
    }

}

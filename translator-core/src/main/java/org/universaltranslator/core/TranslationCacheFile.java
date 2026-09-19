package org.universaltranslator.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

/** 缓存文件格式 */
final class TranslationCacheFile {
    static final String FILE_NAME = "universal_translator-cache.properties";
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;

    private TranslationCacheFile() {
    }

    static Properties read(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Cache file does not exist");
        }
        if (Files.size(source) > MAX_FILE_BYTES) {
            throw new IOException("Cache file is too large");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            try {
                properties.load(reader);
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Cache file is malformed", malformed);
            }
        }
        if (properties.isEmpty()) {
            throw new IOException("Cache file contains no translations");
        }
        return properties;
    }

    static Path write(Path selectedTarget, Map<String, String> entries) throws IOException {
        if (entries == null || entries.isEmpty()) {
            throw new IOException("There are no cached translations to export");
        }
        Path target = resolveTarget(selectedTarget);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.putAll(entries);
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Universal Translator cache v1; entries=" + entries.size());
            }
            moveIntoPlace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (!Files.isRegularFile(target) || Files.size(target) == 0L) {
            throw new IOException("Cache file was not created completely");
        }
        return target;
    }

    static String hashKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format("%02x", item & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static Path resolveTarget(Path selectedTarget) throws IOException {
        if (selectedTarget == null) {
            throw new IOException("Export target is missing");
        }
        Path target = selectedTarget.toAbsolutePath().normalize();
        if (Files.exists(target) && Files.isDirectory(target)) {
            return target.resolve(FILE_NAME);
        }
        String name = target.getFileName() == null ? "" : target.getFileName().toString();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".properties")) {
            target = target.resolveSibling(name + ".properties");
        }
        return target;
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

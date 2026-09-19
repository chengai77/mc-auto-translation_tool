package org.universaltranslator.core.offline;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 跨实例离线资源目录 */
public final class OfflineStoragePaths {
    private static final String DIRECTORY_OVERRIDE = "universaltranslator.offline.directory";

    private OfflineStoragePaths() {
    }

    public static Path sharedDirectory() {
        String override = clean(System.getProperty(DIRECTORY_OVERRIDE));
        if (!override.isEmpty()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        String userHome = clean(System.getProperty("user.home"));
        if (userHome.isEmpty()) {
            throw new IllegalStateException("User home directory is unavailable");
        }
        return Paths.get(userHome, ".universal-translator", "offline")
                .toAbsolutePath().normalize();
    }

    public static List<Path> legacySearchRoots(Path configDirectory) {
        Set<Path> roots = new LinkedHashSet<Path>();
        Path config = normalize(configDirectory);
        add(roots, config == null ? null : config.resolve("universal-translator-offline"));
        addAncestors(roots, config);

        Path userHome = path(System.getProperty("user.home"));
        add(roots, userHome == null ? null
                : userHome.resolve(".minecraft/config/universal-translator-offline"));
        add(roots, userHome == null ? null
                : userHome.resolve(".local/share/PrismLauncher/instances"));
        add(roots, userHome == null ? null
                : userHome.resolve("Library/Application Support/PrismLauncher/instances"));

        Path appData = path(System.getenv("APPDATA"));
        add(roots, appData == null ? null
                : appData.resolve(".minecraft/config/universal-translator-offline"));
        add(roots, appData == null ? null : appData.resolve("PrismLauncher/instances"));
        add(roots, appData == null ? null : appData.resolve("PolyMC/instances"));
        add(roots, appData == null ? null : appData.resolve("MultiMC/instances"));
        add(roots, appData == null ? null : appData.resolve("com.modrinth.theseus/profiles"));
        add(roots, appData == null ? null : appData.resolve("HMCL/instances"));

        roots.remove(sharedDirectory());
        return Collections.unmodifiableList(new ArrayList<Path>(roots));
    }

    private static void addAncestors(Set<Path> roots, Path start) {
        Path current = start;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            add(roots, current.resolve("config/universal-translator-offline"));
            add(roots, current.resolve("universal-translator-offline"));
            add(roots, current.resolve(".minecraft/config/universal-translator-offline"));
            add(roots, current.resolve("instances"));
            add(roots, current.resolve("versions"));
        }
    }

    private static void add(Set<Path> roots, Path path) {
        Path normalized = normalize(path);
        if (normalized != null) {
            roots.add(normalized);
        }
    }

    private static Path path(String value) {
        String clean = clean(value);
        return clean.isEmpty() ? null : normalize(Paths.get(clean));
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

package org.universaltranslator.core;

import org.universaltranslator.core.offline.VerifiedDownloader;
import org.universaltranslator.core.provider.LlamaCppOfflineProvider;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 离线模型定位自检 */
final class OfflineModelInstallationSelfTest {
    private OfflineModelInstallationSelfTest() {
    }

    static void runAll() throws Exception {
        migratesVerifiedModelFromSiblingInstance();
        migratesVerifiedModelFromAncestorConfig();
        rejectsMismatchedGgufModel();
    }

    private static void migratesVerifiedModelFromSiblingInstance() throws Exception {
        Path workspace = Files.createTempDirectory("ut-model-migration-");
        try {
            byte[] modelBytes = new byte[] {'G', 'G', 'U', 'F', 1, 2, 3, 4};
            Path oldModel = workspace.resolve("old-instance/config/universal-translator-offline")
                    .resolve("models/test-model/old-name.gguf");
            Files.createDirectories(oldModel.getParent());
            Files.write(oldModel, modelBytes);

            Path currentRoot = workspace.resolve(
                    "current-instance/config/universal-translator-offline");
            LlamaCppOfflineProvider provider = provider(
                    currentRoot, modelBytes.length, VerifiedDownloader.sha256(oldModel));
            Path canonical = currentRoot.resolve("models/test-model/expected.gguf")
                    .toAbsolutePath().normalize();

            Path installed = findInstalledModel(provider, canonical);
            assertEquals(canonical, installed);
            assertEquals(VerifiedDownloader.sha256(oldModel),
                    VerifiedDownloader.sha256(canonical));
            provider.close();
        } finally {
            deleteTree(workspace);
        }
    }

    private static void rejectsMismatchedGgufModel() throws Exception {
        Path workspace = Files.createTempDirectory("ut-model-reject-");
        try {
            byte[] expected = new byte[] {'G', 'G', 'U', 'F', 1, 2, 3, 4};
            byte[] mismatched = new byte[] {'G', 'G', 'U', 'F', 4, 3, 2, 1};
            Path oldModel = workspace.resolve("old-instance/config/universal-translator-offline")
                    .resolve("models/test-model/wrong.gguf");
            Files.createDirectories(oldModel.getParent());
            Files.write(oldModel, mismatched);

            Path expectedFile = workspace.resolve("expected.gguf");
            Files.write(expectedFile, expected);
            Path currentRoot = workspace.resolve(
                    "current-instance/config/universal-translator-offline");
            LlamaCppOfflineProvider provider = provider(
                    currentRoot, expected.length, VerifiedDownloader.sha256(expectedFile));
            Path canonical = currentRoot.resolve("models/test-model/expected.gguf")
                    .toAbsolutePath().normalize();

            if (findInstalledModel(provider, canonical) != null || Files.exists(canonical)) {
                throw new AssertionError("Mismatched GGUF model must not be migrated");
            }
            provider.close();
        } finally {
            deleteTree(workspace);
        }
    }

    private static void migratesVerifiedModelFromAncestorConfig() throws Exception {
        Path workspace = Files.createTempDirectory("ut-model-ancestor-");
        try {
            byte[] modelBytes = new byte[] {'G', 'G', 'U', 'F', 9, 8, 7, 6};
            Path oldModel = workspace.resolve(".minecraft/config/universal-translator-offline")
                    .resolve("models/renamed-from-old-version.gguf");
            Files.createDirectories(oldModel.getParent());
            Files.write(oldModel, modelBytes);

            Path currentRoot = workspace.resolve(
                    ".minecraft/versions/test/config/universal-translator-offline");
            LlamaCppOfflineProvider provider = provider(
                    currentRoot, modelBytes.length, VerifiedDownloader.sha256(oldModel));
            Path canonical = currentRoot.resolve("models/test-model/expected.gguf")
                    .toAbsolutePath().normalize();

            Path installed = findInstalledModel(provider, canonical);
            assertEquals(canonical, installed);
            assertEquals(VerifiedDownloader.sha256(oldModel),
                    VerifiedDownloader.sha256(canonical));
            provider.close();
        } finally {
            deleteTree(workspace);
        }
    }

    private static LlamaCppOfflineProvider provider(
            Path root, long size, String sha256) {
        return new LlamaCppOfflineProvider(
                root, false, "test-model", "expected.gguf",
                URI.create("https://example.com/expected.gguf"), size, sha256);
    }

    private static Path findInstalledModel(
            LlamaCppOfflineProvider provider, Path canonical) throws Exception {
        Method method = LlamaCppOfflineProvider.class.getDeclaredMethod(
                "findInstalledModel", Path.class);
        method.setAccessible(true);
        return (Path) method.invoke(provider, canonical);
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}

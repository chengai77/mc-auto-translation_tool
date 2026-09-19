package org.universaltranslator.core.provider;

import org.universaltranslator.core.TranslationProvider;
import org.universaltranslator.core.TranslationRequest;
import org.universaltranslator.core.TranslationProviderStatus;
import org.universaltranslator.core.TranslationOutputValidator;
import org.universaltranslator.core.OfflineModel;
import org.universaltranslator.core.offline.OfflineEngineAsset;
import org.universaltranslator.core.offline.OfflineProcessSupport;
import org.universaltranslator.core.offline.SafeArchiveExtractor;
import org.universaltranslator.core.offline.VerifiedDownloader;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 本地llama.cpp */
public final class LlamaCppOfflineProvider
        implements TranslationProvider, TranslationProviderStatus, AutoCloseable {
    private static final long STARTUP_FAILURE_RETRY_MILLIS = 5L * 60L * 1000L;
    private static final long PROGRESS_REPORT_BYTES = 256L * 1024L;
    private static final int MODEL_SEARCH_DEPTH = 8;
    private static final String LITE_CACHE_REVISION = "direct-output-v4";
    private static final String QUALITY_CACHE_REVISION = "direct-output-v3";
    public static final String DEFAULT_MODEL_ID = OfflineModel.LITE.modelId();
    public static final String DEFAULT_MODEL_FILE = OfflineModel.LITE.modelFile();
    public static final URI DEFAULT_MODEL_URI = URI.create(
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/"
                    + "872f8a96064a1242ac3a3359cad77c3042548405/" + DEFAULT_MODEL_FILE);
    public static final URI DEFAULT_MODEL_CHINA_URI = URI.create(
            "https://modelscope.cn/models/qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/"
                    + DEFAULT_MODEL_FILE);
    public static final long DEFAULT_MODEL_SIZE = OfflineModel.LITE.expectedBytes();
    public static final String DEFAULT_MODEL_SHA256 =
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
    public static final String QUALITY_MODEL_ID = OfflineModel.QUALITY.modelId();
    public static final String QUALITY_MODEL_FILE = OfflineModel.QUALITY.modelFile();
    public static final URI QUALITY_MODEL_URI = URI.create(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/"
                    + "62a8d092b0a1047016f3edbd0fde387598727aa5/" + QUALITY_MODEL_FILE);
    public static final URI QUALITY_MODEL_CHINA_URI = URI.create(
            "https://modelscope.cn/models/qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/"
                    + QUALITY_MODEL_FILE);
    public static final long QUALITY_MODEL_SIZE = OfflineModel.QUALITY.expectedBytes();
    public static final String QUALITY_MODEL_SHA256 =
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";

    private final Path root;
    private final boolean autoDownload;
    private final String modelId;
    private final String modelFile;
    private final URI modelUri;
    private final URI modelChinaUri;
    private final long modelSize;
    private final String modelSha256;
    private final List<Path> legacySearchRoots;
    private volatile String status = "等待首次离线翻译";
    private volatile Process process;
    private volatile OpenAiChatTranslationProvider localApi;
    private volatile Thread shutdownHook;
    private volatile String progressStage = "";
    private volatile int progressPercent = -1;
    private volatile long nextStartupAttemptAt;
    private volatile String startupFailureMessage = "";
    private volatile boolean closed;
    private volatile VerifiedDownloader.Cancellation activeDownload;
    private boolean engineRepairAttempted;

    public LlamaCppOfflineProvider(Path root, boolean autoDownload) {
        this(root, autoDownload, DEFAULT_MODEL_ID, DEFAULT_MODEL_FILE,
                DEFAULT_MODEL_CHINA_URI, DEFAULT_MODEL_URI, DEFAULT_MODEL_SIZE, DEFAULT_MODEL_SHA256);
    }

    public static LlamaCppOfflineProvider forModel(Path root, boolean autoDownload, String selection) {
        return forModel(root, autoDownload, OfflineModel.fromConfig(selection));
    }

    public static LlamaCppOfflineProvider forModel(
            Path root, boolean autoDownload, OfflineModel selection) {
        return forModel(root, autoDownload, selection, java.util.Collections.<Path>emptyList());
    }

    public static LlamaCppOfflineProvider forModel(
            Path root,
            boolean autoDownload,
            OfflineModel selection,
            Iterable<Path> legacySearchRoots
    ) {
        OfflineModel normalized = selection == null ? OfflineModel.LITE : selection;
        if (normalized == OfflineModel.LITE) {
            return new LlamaCppOfflineProvider(root, autoDownload, DEFAULT_MODEL_ID, DEFAULT_MODEL_FILE,
                    DEFAULT_MODEL_CHINA_URI, DEFAULT_MODEL_URI, DEFAULT_MODEL_SIZE,
                    DEFAULT_MODEL_SHA256, legacySearchRoots);
        }
        if (normalized == OfflineModel.QUALITY) {
            return new LlamaCppOfflineProvider(root, autoDownload, QUALITY_MODEL_ID, QUALITY_MODEL_FILE,
                    QUALITY_MODEL_CHINA_URI, QUALITY_MODEL_URI, QUALITY_MODEL_SIZE,
                    QUALITY_MODEL_SHA256, legacySearchRoots);
        }
        throw new IllegalArgumentException("Unsupported offline model: " + normalized);
    }

    /** 当前版本下载和诊断共用的规范模型路径。 */
    public static Path modelPath(Path root, OfflineModel selection) {
        OfflineModel normalized = selection == null ? OfflineModel.LITE : selection;
        return modelPath(root, normalized.modelId(), normalized.modelFile());
    }

    private static Path modelPath(Path root, String modelId, String modelFile) {
        if (root == null) {
            throw new IllegalArgumentException("Offline model directory is required");
        }
        return root.toAbsolutePath().normalize()
                .resolve("models").resolve(modelId).resolve(modelFile);
    }

    public LlamaCppOfflineProvider(
            Path root,
            boolean autoDownload,
            String modelId,
            String modelFile,
            URI modelUri,
            long modelSize,
            String modelSha256
    ) {
        this(root, autoDownload, modelId, modelFile, null, modelUri, modelSize, modelSha256);
    }

    private LlamaCppOfflineProvider(
            Path root,
            boolean autoDownload,
            String modelId,
            String modelFile,
            URI modelChinaUri,
            URI modelUri,
            long modelSize,
            String modelSha256
    ) {
        this(root, autoDownload, modelId, modelFile, modelChinaUri, modelUri,
                modelSize, modelSha256, java.util.Collections.<Path>emptyList());
    }

    private LlamaCppOfflineProvider(
            Path root,
            boolean autoDownload,
            String modelId,
            String modelFile,
            URI modelChinaUri,
            URI modelUri,
            long modelSize,
            String modelSha256,
            Iterable<Path> legacySearchRoots
    ) {
        if (root == null) {
            throw new IllegalArgumentException("Offline model directory is required");
        }
        this.root = root.toAbsolutePath().normalize();
        this.autoDownload = autoDownload;
        this.modelId = requireSimpleName("model id", modelId);
        this.modelFile = requireSimpleName("model file", modelFile);
        this.modelChinaUri = modelChinaUri;
        this.modelUri = modelUri;
        this.modelSize = modelSize;
        this.modelSha256 = modelSha256;
        this.legacySearchRoots = normalizeSearchRoots(legacySearchRoots);
    }

    @Override
    public String id() {
        String revision = DEFAULT_MODEL_ID.equals(modelId)
                ? LITE_CACHE_REVISION
                : QUALITY_MODEL_ID.equals(modelId) ? QUALITY_CACHE_REVISION : "";
        return "offline-llama:" + modelId + (revision.isEmpty() ? "" : ":" + revision);
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public String translate(TranslationRequest request) throws Exception {
        try {
            ensureRunning();
            OpenAiChatTranslationProvider api = localApi;
            if (api == null) {
                throw new IllegalStateException("Offline translation process is not ready");
            }
            status = "离线模型运行中";
            try {
                return api.translate(request);
            } catch (Exception firstFailure) {
                // 断连后重启一次
                if (!OfflineProcessSupport.isLoopbackConnectionFailure(firstFailure)) {
                    throw firstFailure;
                }
                closeProcess();
                ensureRunning();
                OpenAiChatTranslationProvider restartedApi = localApi;
                if (restartedApi == null) {
                    throw firstFailure;
                }
                return restartedApi.translate(request);
            }
        } catch (Exception error) {
            status = TranslationOutputValidator.isOutputValidationFailure(error)
                    ? "离线模型运行中"
                    : "离线翻译失败：" + safeMessage(error);
            throw error;
        }
    }

    private void ensureRunning() throws Exception {
        ensureOpen();
        if (process != null && process.isAlive() && localApi != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextStartupAttemptAt > now && !startupFailureMessage.isEmpty()) {
            throw new IOException(startupFailureMessage);
        }
        closeProcess();
        Files.createDirectories(root);
        Path server = ensureEngine();
        Path model = ensureModel();
        ensureOpen();
        try {
            startServer(server, model);
        } catch (OfflineProcessExitedException firstFailure) {
            closeProcess();
            if (autoDownload && !engineRepairAttempted) {
                engineRepairAttempted = true;
                status = "离线引擎启动失败，正在自动修复";
                deleteTree(engineInstallDirectory());
                server = ensureEngine();
                try {
                    startServer(server, model);
                    return;
                } catch (Exception repairFailure) {
                    closeProcess();
                    throw delayStartupRetries(repairFailure);
                }
            }
            throw delayStartupRetries(firstFailure);
        } catch (Exception startupFailure) {
            closeProcess();
            throw delayStartupRetries(startupFailure);
        }
    }

    private void startServer(Path server, Path model) throws Exception {
        ensureOpen();
        int port = reserveLoopbackPort();
        Path log = root.resolve("llama-server.log");
        long logStart = Files.isRegularFile(log) ? Files.size(log) : 0L;
        int processors = Runtime.getRuntime().availableProcessors();
        // 限制推理线程
        // 小模型两线程
        // 避免掉帧
        int threads = Math.max(1, Math.min(2, processors / 2));
        status = "正在启动离线模型";
        ProcessBuilder builder = new ProcessBuilder();
        String modelArgument = OfflineProcessSupport.useRelativeModelPath(builder, model);
        builder.command(
                server.toString(),
                "-m", modelArgument,
                "--host", "127.0.0.1",
                "--port", Integer.toString(port),
                "--alias", "universal-translator-local",
                "--ctx-size", "2048",
                "--parallel", "1",
                "--threads", Integer.toString(threads),
                "--threads-batch", Integer.toString(threads));
        OfflineProcessSupport.configureLibraryPath(builder, server.getParent());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Process child = builder.start();
        synchronized (this) {
            if (closed) {
                child.destroyForcibly();
                throw new CancellationException("Offline translation was closed");
            }
            process = child;
        }
        registerShutdownHook();
        try {
            waitUntilHealthy(port, child, 90_000L, log, logStart);
        } catch (Exception startupFailure) {
            closeProcess();
            throw startupFailure;
        }
        synchronized (this) {
            if (closed || process != child) {
                child.destroyForcibly();
                throw new CancellationException("Offline translation was closed");
            }
            localApi = new OpenAiChatTranslationProvider(
                    "http://127.0.0.1:" + port + "/v1/chat/completions",
                    "", "universal-translator-local", "offline-loopback:" + modelId,
                    new org.universaltranslator.core.net.HttpJsonClient(1_000, 15_000));
        }
        nextStartupAttemptAt = 0L;
        startupFailureMessage = "";
        status = "离线模型已就绪";
    }

    private IOException delayStartupRetries(Exception failure) {
        String message = safeMessage(failure) + "；已暂停自动重试 5 分钟";
        startupFailureMessage = message;
        nextStartupAttemptAt = System.currentTimeMillis() + STARTUP_FAILURE_RETRY_MILLIS;
        return new IOException(message, failure);
    }

    private Path ensureEngine() throws IOException {
        OfflineEngineAsset asset = OfflineEngineAsset.current();
        Path engineRoot = engineRoot(asset);
        Path installed = engineRoot.resolve("installed");
        if (Files.isDirectory(installed)) {
            try {
                return SafeArchiveExtractor.findServer(installed);
            } catch (IOException ignored) {
                deleteTree(installed);
            }
        }
        Path legacyInstalled = findLegacyEngine(asset);
        if (legacyInstalled != null) {
            status = "正在安装离线引擎";
            migrateDirectory(legacyInstalled, installed);
            return SafeArchiveExtractor.findServer(installed);
        }
        if (!autoDownload) {
            throw new IOException("Offline engine is not installed and automatic download is disabled");
        }
        status = "正在下载离线引擎（约 " + Math.max(1L, asset.size / 1_000_000L) + " MB）";
        Path archive = engineRoot.resolve(asset.archiveName);
        VerifiedDownloader.Cancellation cancellation = beginDownload();
        try {
            VerifiedDownloader.download(asset.downloadSources(), archive, asset.size, asset.sha256,
                    progressListener("正在下载离线引擎"), cancellation);
        } finally {
            endDownload(cancellation);
        }
        status = "离线引擎下载并校验完成";
        Path staging = engineRoot.resolve("installing");
        deleteTree(staging);
        Files.createDirectories(staging);
        status = "正在安装离线引擎";
        try {
            SafeArchiveExtractor.extract(archive, staging);
            SafeArchiveExtractor.findServer(staging);
            Files.createDirectories(engineRoot);
            try {
                Files.move(staging, installed, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(staging, installed, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteTree(staging);
            throw exception;
        }
        return SafeArchiveExtractor.findServer(installed);
    }

    private Path engineInstallDirectory() {
        return engineRoot(OfflineEngineAsset.current()).resolve("installed");
    }

    private Path findLegacyEngine(OfflineEngineAsset asset) {
        String engineDirectory = "b9637-" + asset.platformId;
        for (Path searchRoot : legacySearchRoots) {
            Path direct = searchRoot.resolve("engines").resolve(engineDirectory).resolve("installed");
            if (isUsableEngineDirectory(direct)) {
                return direct;
            }
            Path discovered = findPathSafely(searchRoot, MODEL_SEARCH_DEPTH, path ->
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            && "installed".equals(path.getFileName().toString())
                            && path.getParent() != null
                            && engineDirectory.equals(path.getParent().getFileName().toString())
                            && isUsableEngineDirectory(path));
            if (discovered != null) {
                return discovered;
            }
        }
        return null;
    }

    private static boolean isUsableEngineDirectory(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            SafeArchiveExtractor.findServer(directory);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void migrateDirectory(Path source, Path destination) throws IOException {
        Path staging = destination.resolveSibling(destination.getFileName().toString() + ".migrating");
        deleteTree(staging);
        try (java.util.stream.Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                Path target = staging.resolve(source.relativize(path)).normalize();
                if (!target.startsWith(staging)) {
                    throw new IOException("Legacy engine path escaped migration directory");
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException failure) {
            deleteTree(staging);
            throw failure;
        }
        SafeArchiveExtractor.findServer(staging);
        Files.createDirectories(destination.getParent());
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveUnsupported) {
            Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path engineRoot(OfflineEngineAsset asset) {
        return root.resolve("engines").resolve("b9637-" + asset.platformId);
    }

    private Path ensureModel() throws IOException {
        Path model = modelPath(root, modelId, modelFile);
        Path installed = findInstalledModel(model);
        if (installed != null) {
            return installed;
        }
        if (!autoDownload) {
            throw new IOException("Offline model is not installed and automatic download is disabled");
        }
        migrateLegacyPartialModel(model);
        status = "正在下载离线模型（国内源优先，约 "
                + Math.max(1L, modelSize / 1_000_000L) + " MB）";
        List<URI> sources = modelChinaUri == null
                ? java.util.Collections.singletonList(modelUri)
                : Arrays.asList(modelChinaUri, modelUri);
        VerifiedDownloader.Cancellation cancellation = beginDownload();
        Path downloaded;
        try {
            downloaded = VerifiedDownloader.download(
                    sources, model, modelSize, modelSha256,
                    progressListener("正在下载离线模型"), cancellation);
        } finally {
            endDownload(cancellation);
        }
        status = "离线模型下载并校验完成";
        return downloaded;
    }

    /** 兼容旧版本将模型放在不同目录时的已有安装。 */
    private Path findInstalledModel(Path canonical) throws IOException {
        Set<Path> candidates = new LinkedHashSet<Path>();
        List<Path> offlineRoots = new ArrayList<Path>();
        addSearchRoot(offlineRoots, root);
        for (Path legacyRoot : legacySearchRoots) {
            addSearchRoot(offlineRoots, legacyRoot);
            addAncestorOfflineRoots(offlineRoots, legacyRoot);
            addSiblingOfflineRoots(offlineRoots, legacyRoot);
        }
        addSearchRoot(offlineRoots, root.getParent());
        if (root.getParent() != null) {
            addSearchRoot(offlineRoots, root.getParent().getParent());
        }
        addAncestorOfflineRoots(offlineRoots, root);
        addSiblingOfflineRoots(offlineRoots, root);
        addSiblingOfflineRoots(offlineRoots, root.getParent());
        if (root.getParent() != null) {
            addSiblingOfflineRoots(offlineRoots, root.getParent().getParent());
        }
        for (Path offlineRoot : offlineRoots) {
            addCandidate(candidates, offlineRoot.resolve("models").resolve(modelId).resolve(modelFile));
            addCandidate(candidates, offlineRoot.resolve("models").resolve(modelFile));
            addCandidate(candidates, offlineRoot.resolve(modelId).resolve(modelFile));
            addCandidate(candidates, offlineRoot.resolve(modelFile));
            addGgufCandidates(candidates, offlineRoot.resolve("models"));
            addGgufCandidates(candidates, offlineRoot.resolve("models").resolve(modelId));
            addGgufCandidates(candidates, offlineRoot.resolve(modelId));
            collectPathsSafely(offlineRoot, MODEL_SEARCH_DEPTH,
                    LlamaCppOfflineProvider::isGgufFile,
                    path -> addCandidate(candidates, path));
        }
        for (Path candidate : candidates) {
            if (isPinnedModel(candidate)) {
                if (!candidate.equals(canonical)) {
                    Files.createDirectories(canonical.getParent());
                    Files.copy(candidate, canonical, StandardCopyOption.REPLACE_EXISTING);
                    if (!isPinnedModel(canonical)) {
                        Files.deleteIfExists(canonical);
                        throw new IOException("Copied offline model did not pass integrity verification");
                    }
                }
                return canonical;
            }
        }
        return null;
    }

    private static void addCandidate(Set<Path> candidates, Path candidate) {
        if (candidate != null) {
            candidates.add(candidate.toAbsolutePath().normalize());
        }
    }

    private static void addGgufCandidates(Set<Path> candidates, Path directory) {
        collectPathsSafely(directory, 1, LlamaCppOfflineProvider::isGgufFile,
                path -> addCandidate(candidates, path));
    }

    private boolean isPinnedModel(Path candidate) {
        try {
            return Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(candidate) == modelSize
                    && modelSha256.equalsIgnoreCase(VerifiedDownloader.sha256(candidate));
        } catch (IOException ignored) {
            return false;
        }
    }

    private void migrateLegacyPartialModel(Path canonical) throws IOException {
        Path destination = canonical.resolveSibling(canonical.getFileName().toString() + ".part");
        long destinationSize = regularFileSize(destination);
        Path largest = null;
        long largestSize = destinationSize;
        String partialName = modelFile + ".part";
        for (Path searchRoot : legacySearchRoots) {
            final Path[] candidate = new Path[]{largest};
            final long[] candidateSize = new long[]{largestSize};
            collectPathsSafely(searchRoot, MODEL_SEARCH_DEPTH, path ->
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && partialName.equalsIgnoreCase(path.getFileName().toString()), path -> {
                long size = regularFileSize(path);
                if (size > candidateSize[0] && size <= modelSize) {
                    candidate[0] = path;
                    candidateSize[0] = size;
                }
            });
            if (candidate[0] != null && candidateSize[0] > largestSize) {
                largest = candidate[0];
                largestSize = candidateSize[0];
            }
        }
        if (largest != null) {
            Files.createDirectories(destination.getParent());
            Files.copy(largest, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long regularFileSize(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return -1L;
        }
        try {
            return Files.size(file);
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static boolean isGgufFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".gguf");
    }

    private static Path findPathSafely(
            Path root, int maximumDepth, Predicate<Path> predicate) {
        final Path[] found = new Path[1];
        walkPathsSafely(root, maximumDepth, path -> {
            if (found[0] == null && predicate.test(path)) {
                found[0] = path;
            }
        }, () -> found[0] != null);
        return found[0];
    }

    private static void collectPathsSafely(
            Path root, int maximumDepth, Predicate<Path> predicate, Consumer<Path> consumer) {
        walkPathsSafely(root, maximumDepth, path -> {
            if (predicate.test(path)) {
                consumer.accept(path);
            }
        }, () -> false);
    }

    private static void walkPathsSafely(
            Path root,
            int maximumDepth,
            Consumer<Path> visitor,
            java.util.function.BooleanSupplier finished
    ) {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, java.util.Collections.<java.nio.file.FileVisitOption>emptySet(),
                    maximumDepth, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory, BasicFileAttributes attributes) {
                            if (attributes.isSymbolicLink() || attributes.isOther()
                                    || !Files.isReadable(directory)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            visitor.accept(directory);
                            return finished.getAsBoolean()
                                    ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                            if (attributes.isRegularFile() && !attributes.isSymbolicLink()) {
                                visitor.accept(file);
                            }
                            return finished.getAsBoolean()
                                    ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException error) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException | RuntimeException ignored) {
            // 单个受保护目录不影响其他候选。
        }
    }

    private static void addSearchRoot(List<Path> roots, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) {
            roots.add(normalized);
        }
    }

    private static List<Path> normalizeSearchRoots(Iterable<Path> candidates) {
        List<Path> roots = new ArrayList<Path>();
        if (candidates != null) {
            for (Path candidate : candidates) {
                addSearchRoot(roots, candidate);
            }
        }
        return java.util.Collections.unmodifiableList(roots);
    }

    /** 覆盖启动器实例嵌套在 .minecraft/versions 等目录的布局。 */
    private static void addAncestorOfflineRoots(List<Path> roots, Path directory) {
        Path current = directory == null ? null : directory.toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            addSearchRoot(roots, current.resolve("config").resolve("universal-translator-offline"));
            addSearchRoot(roots, current.resolve("universal-translator-offline"));
        }
    }

    private static void addSiblingOfflineRoots(List<Path> roots, Path directory) {
        Path parent = directory == null ? null : directory.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (java.util.stream.Stream<Path> children = Files.list(parent)) {
            children.filter(Files::isDirectory).forEach(child -> {
                addSearchRoot(roots, child.resolve("config").resolve("universal-translator-offline"));
                addSearchRoot(roots, child.resolve("universal-translator-offline"));
            });
        } catch (IOException | RuntimeException ignored) {
            // 继续使用当前实例的规范路径。
        }
    }

    private VerifiedDownloader.Cancellation beginDownload() throws IOException {
        ensureOpen();
        VerifiedDownloader.Cancellation cancellation = new VerifiedDownloader.Cancellation();
        synchronized (this) {
            if (closed) {
                throw new CancellationException("Offline translation was closed");
            }
            activeDownload = cancellation;
        }
        return cancellation;
    }

    private void endDownload(VerifiedDownloader.Cancellation cancellation) {
        synchronized (this) {
            if (activeDownload == cancellation) {
                activeDownload = null;
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Offline translation was closed");
        }
    }

    private VerifiedDownloader.ProgressListener progressListener(final String stage) {
        progressStage = stage;
        progressPercent = 0;
        status = stage + "：0%";
        return new VerifiedDownloader.ProgressListener() {
            private long lastReportedBytes;

            @Override
            public void onProgress(long downloadedBytes, long totalBytes) {
                if (totalBytes <= 0L) {
                    return;
                }
                int percent = (int) Math.min(100L, downloadedBytes * 100L / totalBytes);
                int previous = progressPercent;
                if (!stage.equals(progressStage)
                        || percent != previous
                        || downloadedBytes - lastReportedBytes >= PROGRESS_REPORT_BYTES
                        || downloadedBytes >= totalBytes) {
                    progressStage = stage;
                    progressPercent = percent;
                    lastReportedBytes = downloadedBytes;
                    long percentTenths = Math.min(1_000L, downloadedBytes * 1_000L / totalBytes);
                    status = stage + "：" + (percentTenths / 10L) + "."
                            + (percentTenths % 10L) + "%（"
                            + formatMegabytes(downloadedBytes) + " / "
                            + formatMegabytes(totalBytes) + " MB）";
                }
            }
        };
    }

    private static void waitUntilHealthy(
            int port,
            Process child,
            long timeoutMillis,
            Path log,
            long logStart
    ) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            if (!child.isAlive()) {
                int exitCode = child.exitValue();
                String detail = OfflineProcessSupport.readNewLogTail(log, logStart);
                throw new OfflineProcessExitedException(
                        OfflineProcessSupport.describeStartupExit(exitCode, detail));
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(
                        "http://127.0.0.1:" + port + "/health").toURL().openConnection();
                connection.setConnectTimeout(500);
                connection.setReadTimeout(1000);
                int response = connection.getResponseCode();
                if (response >= 200 && response < 300) {
                    return;
                }
            } catch (IOException exception) {
                lastFailure = exception;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            Thread.sleep(250L);
        }
        throw new IOException("Offline model did not become ready within 90 seconds", lastFailure);
    }

    private static int reserveLoopbackPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String requireSimpleName(String label, String value) {
        if (value == null || value.trim().isEmpty()
                || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value.trim();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 157) + "...";
    }

    private static String formatMegabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / 1_000_000.0D);
    }

    @Override
    public void close() {
        VerifiedDownloader.Cancellation cancellation;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            cancellation = activeDownload;
            activeDownload = null;
            localApi = null;
        }
        if (cancellation != null) {
            cancellation.cancel();
        }
        closeProcess();
        status = "离线模型已停止";
    }

    private synchronized void registerShutdownHook() {
        if (closed) {
            return;
        }
        if (shutdownHook != null) {
            return;
        }
        Thread hook = new Thread(new Runnable() {
            @Override
            public void run() {
                closeProcessAtShutdown();
            }
        }, "universal-translator-offline-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            shutdownHook = hook;
        } catch (IllegalStateException shuttingDown) {
            // 关闭即停模型
            // 退出不留进程
            closeProcessAtShutdown();
        }
    }

    private void closeProcess() {
        closeProcess(true);
    }

    private void closeProcessAtShutdown() {
        Process child;
        synchronized (this) {
            localApi = null;
            shutdownHook = null;
            child = process;
            process = null;
        }
        if (child != null) {
            child.destroyForcibly();
        }
    }

    private void closeProcess(boolean unregisterHook) {
        Thread hook = shutdownHook;
        Process child;
        synchronized (this) {
            localApi = null;
            shutdownHook = null;
            child = process;
            process = null;
        }
        if (unregisterHook && hook != null && hook != Thread.currentThread()) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // 关闭钩子进行中
            }
        }
        if (child == null) {
            return;
        }
        child.destroy();
        Thread reaper = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!child.waitFor(2L, TimeUnit.SECONDS)) {
                        child.destroyForcibly();
                        child.waitFor(2L, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    child.destroyForcibly();
                }
            }
        }, "universal-translator-offline-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    private static final class OfflineProcessExitedException extends IOException {
        private OfflineProcessExitedException(String message) {
            super(message);
        }
    }
}

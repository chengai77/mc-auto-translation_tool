package org.universaltranslator.core;

import org.universaltranslator.core.net.EndpointPolicy;
import org.universaltranslator.core.net.JsonStrings;
import org.universaltranslator.core.net.TencentCloudV3Signer;
import org.universaltranslator.core.offline.VerifiedDownloader;
import org.universaltranslator.core.offline.SafeArchiveExtractor;
import org.universaltranslator.core.offline.OfflineEngineAsset;
import org.universaltranslator.core.offline.OfflineProcessSupport;
import org.universaltranslator.core.provider.FallbackTranslationProvider;
import org.universaltranslator.core.provider.LlamaCppOfflineProvider;
import org.universaltranslator.core.provider.OpenAiChatTranslationProvider;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 无依赖自检 */
public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        protectsDynamicScoreboardValues();
        preservesNumericQuantityAndPercentageSemantics();
        repairsCachedStyledNumericGrammar();
        avoidsFalseNumericClassifiers();
        skipsAlreadyChineseAndNonTextValues();
        protectsExistingChineseInMixedText();
        stylesCompletedTranslations();
        validatesSmallModelOutputs();
        retriesRejectedProviderOutputs();
        retriesRejectedStructuredProviderOutputs();
        keepsValidationFallbackStatusHealthy();
        preservesRecentUserMessages();
        classifiesPlayerChatMessages();
        cachesDynamicTemplates();
        deduplicatesConcurrentRequests();
        completesQueuedRequestsWhenClosed();
        fallsBackToOriginalOnFailure();
        enforcesSafeEndpoints();
        normalizesCustomOpenAiEndpoints();
        rejectsConversationalApiOutput();
        passesNumericHintsToOfflineLite();
        handlesJsonStrings();
        updatesRenderLookupsWithoutBlocking();
        translatesTooltipLinesIndependently();
        translatesWrappedVisualLinesAsOneSentence();
        keepsScoreboardRowsIndependent();
        joinsBookSentenceBreaksWithSpace();
        preservesBookMenuRows();
        preservesSeparatorLinesInStructuredText();
        preservesSignTokenPunctuation();
        translatesHologramTextBlocksAsOneSentence();
        groupsHologramsBeforeUsingFragmentTranslations();
        groupsLargeHologramMenus();
        translatesCompleteHologramBeforeWrapping();
        normalizesChineseDateOrder();
        validatesChineseConditionClauseOrder();
        repairsUnnaturalChineseConditionClauseOrder();
        alignsTranslatedHologramTops();
        preservesCompleteHologramDateOrder();
        preservesHologramMenuAndTextureRows();
        keepsCrossLineStyledHologramSentencesTogether();
        segmentsComplexHologramsByLogicalBlock();
        fallsBackWhenHologramBlockChangesProtectedTokens();
        preservesHologramBracketBoundaries();
        removesUnusedHologramSourceBlocks();
        translatesOutgoingChatAsynchronously();
        suppressesRecoverableRenderFailures();
        exposesRenderTranslationFailures();
        protectsLiteralsOffTheRenderThread();
        boundsBusyLobbyTranslationWork();
        reservesPendingCapacityForForegroundText();
        reservesPendingCapacityForPlayerChat();
        reservesPendingCapacityForSystemMessages();
        rateLimitsBusyLobbyWithoutStarvingTooltips();
        urgentTitlesBypassBlockedNormalQueue();
        foregroundMessagesBypassBlockedWorldQueue();
        systemMessagesBypassBlockedUrgentHudMessages();
        playerChatBypassesBlockedSystemMessages();
        renderPlayerChatBypassesSystemFlood();
        renderSystemMessagesBypassForegroundFlood();
        sharedQueueReservesForegroundCapacity();
        titleLookupsNeverBlockRenderThread();
        cacheHitsRenderImmediatelyAfterRestart();
        dynamicHologramCacheHitsImmediatelyAfterRestart();
        dynamicProtectedNamesAvoidUnprotectedCache();
        doesNotTranslateCompletedOutputAgain();
        persistsOnlyHashedCacheKeys();
        exportsAndReimportsCacheFile();
        ignoresMalformedPersistentCache();
        protectsPlayerNames();
        protectsNetworkAddresses();
        protectsInlineTextureCodes();
        InlineTextureCodeSelfTest.runAll();
        HologramLineWidthSelfTest.runAll();
        SpatialHologramLayoutSelfTest.runAll();
        translatesProtectedChatAsOneSentence();
        retriesStructuredChatWithAlternateTokens();
        fallsBackForStyledChatAfterTokenFormatsFail();
        acceptsTextMovedAcrossTextureStyleBoundaries();
        preservesStyledTranslationRanges();
        translatesComplexStyledChatMenusLocally();
        SegmentBatchSelfTest.runAll();
        skipsFullyProtectedText();
        neverSendsProtectedValuesToProvider();
        prefersGameGlossaryForAmbiguousTerms();
        passesGameHintsAndContextToProvider();
        prefersChinaDownloadSources();
        configuresWindowsOfflineRuntimePath();
        usesRelativeOfflineModelPath();
        reportsOfflineStartupDiagnostics();
        matchesTencentCloudOfficialSignatureVector();
        keepsOriginalTextInBilingualMode();
        preservesMultilineTranslatedOnlyDisplay();
        fallsBackFromOfflineToApi();
        verifiesDownloadedFileHashes();
        reportsVerifiedDownloadProgress();
        extractsOfflineEngineArchivesSafely();
        normalizesOfflineModelSelections();
        supportsTraditionalChineseTargets();
        formatsSecretFreeDiagnostics();
        localizesDiagnosticsAndRuntimeStatus();
        System.out.println("CoreSelfTest: all checks passed");
    }

    private static void translatesOutgoingChatAsynchronously() throws Exception {
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "outgoing-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                assertEquals("en", request.getTargetLanguage());
                assertEquals("你好 __UT_0__", request.getText());
                return "Hello __UT_0__";
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.setProtectedLiteralsSupplier(() -> Arrays.asList("Steve_42"));
            TranslationResult result = session.translateInteractive(
                    "你好 Steve_42", TextKind.CHAT, "en", false)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertEquals("Hello Steve_42", result.getTranslatedText());
        }
    }

    private static void supportsTraditionalChineseTargets() {
        assertEquals("zh-TW", TargetLanguage.canonicalize("zh_Hant"));
        assertEquals("zh-TW", TargetLanguage.canonicalize("zh-HK"));
        assertEquals("zh-TW", TargetLanguage.nextPreset("zh-CN"));
        assertEquals("en", TargetLanguage.nextPreset("zh-TW"));
        assertEquals("繁體中文", TargetLanguage.displayName("zh-TW"));
        assertEquals("zt", TargetLanguage.libreTranslateCode("zh-TW"));
        assertEquals("zh", TargetLanguage.libreTranslateCode("zh-CN"));
        assertTrue(TargetLanguage.translationInstruction("zh-TW")
                .contains("Traditional Chinese characters"));
        assertFalse(LanguageHeuristics.shouldTranslate("金幣：123", "zh-TW"));
        assertTrue(LanguageHeuristics.shouldTranslate("Coins: 123", "zh-TW"));
    }

    private static void localizesDiagnosticsAndRuntimeStatus() {
        UiTranslator translator = new UiTranslator() {
            @Override
            public String translate(String key, Object... arguments) {
                return key + (arguments.length == 0 ? "" : "=" + java.util.Arrays.toString(arguments));
            }
        };
        TranslationDiagnosticsSnapshot snapshot = new TranslationDiagnosticsSnapshot(
                true, "offline", "offline-llama:model", "zh-TW", OfflineModel.LITE,
                true, true, OfflineModel.LITE.expectedBytes(), 1000L, "离线模型运行中");
        String output = String.join("\n", snapshot.localizedLines(translator));
        assertTrue(output.contains("screen.universal_translator.diagnostics.enabled"));
        assertTrue(output.contains("status.universal_translator.offline_running"));
        assertEquals("status.universal_translator.translation_failed=[timeout]",
                TranslationStatusLocalizer.localize("翻译失败：timeout", translator));
        assertEquals("status.universal_translator.primary_running",
                TranslationStatusLocalizer.localize("主翻译服务运行中", translator));
        assertTrue(TranslationStatusLocalizer.isFailure("离线翻译失败：timeout"));
        assertFalse(TranslationStatusLocalizer.isFailure("离线模型已就绪"));
        assertTrue(TranslationStatusLocalizer.isDownloadProgress(
                "正在下载离线模型：42%"));
        assertFalse(TranslationStatusLocalizer.isDownloadProgress("离线模型已就绪"));
        assertEquals("status.universal_translator.model_downloading_progress=[42%]",
                TranslationStatusLocalizer.localize("正在下载离线模型：42%", translator));
    }

    private static void normalizesOfflineModelSelections() throws Exception {
        assertEquals(OfflineModel.LITE, OfflineModel.fromConfig(null));
        assertEquals(OfflineModel.LITE, OfflineModel.fromConfig("unknown-model"));
        assertEquals(OfflineModel.LITE, OfflineModel.fromConfig(
                "qwen2.5-0.5b-instruct-q4-k-m"));
        assertEquals(OfflineModel.QUALITY, OfflineModel.fromConfig(" QUALITY "));
        assertEquals(OfflineModel.QUALITY, OfflineModel.fromConfig(
                "qwen2.5-1.5b-instruct-q4-k-m"));
        assertEquals(OfflineModel.QUALITY, OfflineModel.LITE.next());
        assertEquals(OfflineModel.LITE, OfflineModel.QUALITY.next());

        Path directory = Files.createTempDirectory("universal-translator-model-selection-");
        try (LlamaCppOfflineProvider provider = LlamaCppOfflineProvider.forModel(
                directory, false, "invalid-selection")) {
            assertEquals("offline-llama:" + OfflineModel.LITE.modelId()
                    + ":compact-prompt-v1", provider.id());
        }
    }

    private static void formatsSecretFreeDiagnostics() {
        TranslationDiagnosticsSnapshot snapshot = new TranslationDiagnosticsSnapshot(
                true,
                "offline",
                "fallback:offline-llama:model:libretranslate:https://secret.example/translate",
                "zh-CN",
                OfflineModel.QUALITY,
                true,
                true,
                OfflineModel.QUALITY.expectedBytes(),
                1_500L,
                "离线模型失败 https://secret.example/translate api-key=abc123\n重试中");
        String output = String.join("\n", snapshot.displayLines());
        assertTrue(output.contains("离线模型：Quality"));
        assertTrue(output.contains("模型文件：已安装并且大小正确"));
        assertTrue(output.contains("运行服务：离线模型 + API 回退"));
        assertFalse(output.contains("secret.example"));
        assertFalse(output.contains("https://"));
        assertFalse(output.contains("abc123"));
        assertFalse(output.contains("\n重试中"));
        assertTrue(output.contains("[地址已隐藏]"));
        assertTrue(output.contains("api-key=[已隐藏]"));
    }

    private static void keepsOriginalTextInBilingualMode() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "en", "zh-CN", new TranslationCache(100), 1,
                TranslationDisplayMode.ORIGINAL_AND_TRANSLATED)) {
            session.lookup("Coins: 42", TextKind.SCOREBOARD_LINE);
            long deadline = System.currentTimeMillis() + 2000L;
            String translated;
            do {
                Thread.sleep(10L);
                translated = session.lookup("Coins: 42", TextKind.SCOREBOARD_LINE);
            } while ("Coins: 42".equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals("Coins: 42 \u00a78| \u00a7f\u91d1\u5e01: 42", translated);
        }

        String original = "[item/diamond_spear@items]Melee attack";
        RecordingProvider textureProvider = new RecordingProvider("\u8fd1\u6218\u653b\u51fb");
        try (RenderTranslationSession session = new RenderTranslationSession(
                textureProvider, "en", "zh-CN", new TranslationCache(100), 1,
                TranslationDisplayMode.ORIGINAL_AND_TRANSLATED)) {
            String translated;
            long deadline = System.currentTimeMillis() + 2000L;
            do {
                Thread.sleep(10L);
                translated = session.lookup(original, TextKind.CHAT);
            } while (original.equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals(original + " \u00a78| \u00a7f\u8fd1\u6218\u653b\u51fb", translated);
            assertTrue(InlineTextureCode.hasSameSequence(original, translated));
        }
    }

    private static void preservesMultilineTranslatedOnlyDisplay() {
        String translated = "第一行\n第二行";
        assertEquals(translated, TranslationDisplayText.translatedOnly(
                translated, TranslationDisplayMode.TRANSLATED_ONLY));
        assertEquals(translated, TranslationDisplayText.translatedOnly(
                TranslationDisplayText.bilingual("First\nSecond", translated),
                TranslationDisplayMode.ORIGINAL_AND_TRANSLATED));
    }

    private static void fallsBackFromOfflineToApi() throws Exception {
        CountingProvider primary = new CountingProvider(true);
        CountingProvider fallback = new CountingProvider(false);
        TranslationProvider provider = new FallbackTranslationProvider(primary, fallback);
        String translated = provider.translate(new TranslationRequest(
                "Coins: 8", "en", "zh-CN", TextKind.SCOREBOARD_LINE));
        assertEquals("\u91d1\u5e01: 8", translated);
        assertEquals(1, primary.calls.get());
        assertEquals(1, fallback.calls.get());
        assertEquals("主翻译服务失败，已使用 API 回退",
                ((TranslationProviderStatus) provider).status());
    }

    private static void verifiesDownloadedFileHashes() throws Exception {
        Path file = Files.createTempFile("universal-translator-hash-", ".txt");
        Files.write(file, "offline".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("8e2c7ac508139a02af859de64a4743c1f3946837279332c35ec8f5ddf20654ae",
                VerifiedDownloader.sha256(file));
    }

    private static void reportsVerifiedDownloadProgress() throws Exception {
        Path file = Files.createTempFile("universal-translator-progress-", ".txt");
        byte[] bytes = "offline".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, bytes);
        AtomicLong downloaded = new AtomicLong();
        AtomicLong total = new AtomicLong();
        VerifiedDownloader.download(
                Arrays.asList(URI.create("https://example.invalid/model")),
                file,
                bytes.length,
                VerifiedDownloader.sha256(file),
                (current, expected) -> {
                    downloaded.set(current);
                    total.set(expected);
                });
        assertEquals((long) bytes.length, downloaded.get());
        assertEquals((long) bytes.length, total.get());
    }

    private static void extractsOfflineEngineArchivesSafely() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-archive-");
        Path tar = directory.resolve("engine.tar.gz");
        byte[] script = "#!/bin/sh\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (OutputStream file = Files.newOutputStream(tar);
             GZIPOutputStream gzip = new GZIPOutputStream(file)) {
            writeTarEntry(gzip, "llama-test/llama-server", script);
            gzip.write(new byte[1024]);
        }
        Path tarOutput = directory.resolve("tar-output");
        SafeArchiveExtractor.extract(tar, tarOutput);
        assertTrue(Files.isRegularFile(tarOutput.resolve("llama-test/llama-server")));

        Path zip = directory.resolve("engine.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("llama-test/llama-server.exe"));
            output.write(script);
            output.closeEntry();
        }
        Path zipOutput = directory.resolve("zip-output");
        SafeArchiveExtractor.extract(zip, zipOutput);
        assertTrue(Files.isRegularFile(zipOutput.resolve("llama-test/llama-server.exe")));

        Path unsafeZip = directory.resolve("unsafe.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(unsafeZip))) {
            output.putNextEntry(new ZipEntry("../escape"));
            output.write(script);
            output.closeEntry();
        }
        assertThrows(() -> SafeArchiveExtractor.extract(unsafeZip, directory.resolve("unsafe-output")));
    }

    private static void writeTarEntry(OutputStream output, String name, byte[] data) throws Exception {
        byte[] header = new byte[512];
        byte[] encodedName = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(encodedName, 0, header, 0, encodedName.length);
        writeTarOctal(header, 100, 8, 0755);
        writeTarOctal(header, 108, 8, 0);
        writeTarOctal(header, 116, 8, 0);
        writeTarOctal(header, 124, 12, data.length);
        writeTarOctal(header, 136, 12, 0);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        byte[] magic = "ustar".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        long checksum = 0L;
        for (byte item : header) {
            checksum += item & 0xff;
        }
        String checksumText = String.format("%06o", checksum);
        byte[] checksumBytes = checksumText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(checksumBytes, 0, header, 148, checksumBytes.length);
        header[154] = 0;
        header[155] = ' ';
        output.write(header);
        output.write(data);
        int padding = (512 - (data.length % 512)) % 512;
        output.write(new byte[padding]);
    }

    private static void writeTarOctal(byte[] header, int offset, int length, long value) {
        String encoded = Long.toOctalString(value);
        int start = offset + length - 1 - encoded.length();
        Arrays.fill(header, offset, start, (byte) '0');
        byte[] bytes = encoded.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, start, bytes.length);
        header[offset + length - 1] = 0;
    }

    private static void doesNotTranslateCompletedOutputAgain() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.lookup("Coins: 42", TextKind.OTHER);
            long deadline = System.currentTimeMillis() + 2000L;
            String translated;
            do {
                Thread.sleep(10L);
                translated = session.lookup("Coins: 42", TextKind.OTHER);
            } while ("Coins: 42".equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals("\u91d1\u5e01: 42", translated);
            assertEquals("\u91d1\u5e01: 42", session.lookup(translated, TextKind.OTHER));
            Thread.sleep(50L);
            assertEquals(1, provider.calls.get());
        }
    }

    private static void enforcesSafeEndpoints() {
        assertEquals("http", EndpointPolicy.requireSafeEndpoint("http://127.0.0.1:5000/translate").getScheme());
        assertEquals("https", EndpointPolicy.requireSafeEndpoint("https://translate.example/translate").getScheme());
        assertThrows(() -> EndpointPolicy.requireSafeEndpoint("http://translate.example/translate"));
        assertThrows(() -> EndpointPolicy.requireSafeEndpoint("https://user:secret@translate.example/translate"));
    }

    private static void normalizesCustomOpenAiEndpoints() {
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatTranslationProvider.normalizeChatCompletionsEndpoint("https://api.example.com"));
        assertEquals("https://api.example.com/v1/chat/completions",
                OpenAiChatTranslationProvider.normalizeChatCompletionsEndpoint("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/custom/v1/chat/completions",
                OpenAiChatTranslationProvider.normalizeChatCompletionsEndpoint("https://api.example.com/custom/v1"));
        assertEquals("https://api.example.com/openai/chat/completions",
                OpenAiChatTranslationProvider.normalizeChatCompletionsEndpoint(
                        "https://api.example.com/openai/chat/completions/"));
        assertThrows(() -> OpenAiChatTranslationProvider.normalizeChatCompletionsEndpoint(
                "https://api.example.com/v1?token=secret"));
    }

    private static void rejectsConversationalApiOutput() throws Exception {
        final ServerSocket server = new ServerSocket(0, 2,
                InetAddress.getByName("127.0.0.1"));
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> firstRequest = new AtomicReference<String>();
        final AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int attempt = 0; attempt < 2; attempt++) {
                        try (Socket socket = server.accept()) {
                            String body = readHttpBody(socket.getInputStream());
                            firstRequest.compareAndSet(null, body);
                            int call = calls.incrementAndGet();
                            String content = call == 1
                                    ? "\u6211\u662f Codex\uff0c\u57fa\u4e8e GPT-5\u7684\u6a21\u578b\u3002"
                                    : "\u4f60\u662f\u4ec0\u4e48\u6a21\u578b\uff1f\u8bf7\u544a\u8bc9\u6211\u3002";
                            writeHttpResponse(socket, "{\"choices\":[{\"message\":{\"content\":"
                                    + JsonStrings.quote(content) + "}}]}");
                        }
                    }
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                } finally {
                    try {
                        server.close();
                    } catch (IOException ignored) {
                        // 测试结束
                    }
                }
            }
        }, "translation-api-test-server");
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            OpenAiChatTranslationProvider provider = new OpenAiChatTranslationProvider(
                    "http://127.0.0.1:" + server.getLocalPort(), "", "test-model", "api-test");
            String translated = provider.translate(new TranslationRequest(
                    "What model are you? Please tell me.", "auto", "zh-CN", TextKind.CHAT));
            assertEquals("\u4f60\u662f\u4ec0\u4e48\u6a21\u578b\uff1f\u8bf7\u544a\u8bc9\u6211\u3002", translated);
            assertEquals(2, calls.get());
            assertTrue(firstRequest.get().contains("\\\"source_text\\\""));
            assertTrue(firstRequest.get().contains("\\\"numeric_reference\\\""));
            assertTrue(firstRequest.get().contains("What model are you? Please tell me."));
        } finally {
            server.close();
            serverThread.join(5000L);
        }
        if (serverThread.isAlive()) {
            throw new AssertionError("Translation API test server did not stop");
        }
        if (serverFailure.get() != null) {
            throw new AssertionError("Translation API test server failed", serverFailure.get());
        }
    }

    private static void passesNumericHintsToOfflineLite() throws Exception {
        final ServerSocket server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"));
        final AtomicReference<String> requestBody = new AtomicReference<String>();
        final AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = server.accept()) {
                    requestBody.set(readHttpBody(socket.getInputStream()));
                    writeHttpResponse(socket, "{\"choices\":[{\"message\":{\"content\":"
                            + JsonStrings.quote("找到__UT_0__个试炼") + "}}]}");
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                } finally {
                    try {
                        server.close();
                    } catch (IOException ignored) {
                        // 测试结束
                    }
                }
            }
        }, "offline-lite-numeric-hint-test");
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            String model = OfflineModel.LITE.modelId();
            OpenAiChatTranslationProvider provider = new OpenAiChatTranslationProvider(
                    "http://127.0.0.1:" + server.getLocalPort(), "", model,
                    "offline-loopback-test-" + model);
            String translated = provider.translate(new TranslationRequest(
                    "Find __UT_0__ trials", "auto", "zh-CN", TextKind.CHAT, "",
                    "numeric_token_reference:\n"
                            + "__UT_0__=plain_number; role=count_or_measurement"));
            assertEquals("找到__UT_0__个试炼", translated);
            assertTrue(requestBody.get().contains("numeric_token_reference"));
            assertTrue(requestBody.get().contains("role=count_or_measurement"));
        } finally {
            server.close();
            serverThread.join(5000L);
        }
        if (serverThread.isAlive()) {
            throw new AssertionError("Offline Lite API test server did not stop");
        }
        if (serverFailure.get() != null) {
            throw new AssertionError("Offline Lite API test server failed", serverFailure.get());
        }
    }

    private static String readHttpBody(InputStream input) throws IOException {
        byte[] marker = new byte[]{'\r', '\n', '\r', '\n'};
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < marker.length) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("HTTP request ended before headers");
            }
            header.write(value);
            if (value == marker[matched]) {
                matched++;
            } else {
                matched = value == marker[0] ? 1 : 0;
            }
        }
        String headers = new String(header.toByteArray(), StandardCharsets.ISO_8859_1);
        int contentLength = 0;
        for (String line : headers.split("\\r\\n")) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                contentLength = Integer.parseInt(line.substring(15).trim());
                break;
            }
        }
        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < body.length) {
            int count = input.read(body, offset, body.length - offset);
            if (count < 0) {
                throw new IOException("HTTP request ended before body");
            }
            offset += count;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static void writeHttpResponse(Socket socket, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream output = socket.getOutputStream();
        output.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }

    private static void handlesJsonStrings() {
        String value = "line 1\n\"\u91d1\u5e01\" \\";
        String json = "{\"translatedText\":" + JsonStrings.quote(value) + "}";
        assertEquals(value, JsonStrings.readStringField(json, "translatedText"));
        assertEquals(null, JsonStrings.readStringField(json, "missing"));
        assertEquals("\u91d1\u5e01", JsonStrings.readStringField(
                "{\"Response\":{\"Choices\":[{\"Message\":{\"Content\":\"\\u91d1\\u5e01\"}}]}}",
                "Content"));
    }

    private static void matchesTencentCloudOfficialSignatureVector() throws Exception {
        String payload = "{\"Limit\": 1, \"Filters\": [{\"Values\": [\"\\u672a\\u547d\\u540d\"], \"Name\": \"instance-name\"}]}";
        Map<String, String> headers = TencentCloudV3Signer.headers(
                "cvm",
                "cvm.tencentcloudapi.com",
                "DescribeInstances",
                "2017-03-12",
                "AKID********************************",
                "********************************",
                payload,
                1551113065L);
        assertEquals(
                "TC3-HMAC-SHA256 Credential=AKID********************************/2019-02-25/cvm/tc3_request, "
                        + "SignedHeaders=content-type;host;x-tc-action, "
                        + "Signature=10b1a37a7301a02ca19a647ad722d5e43b4b3cff309d421d85b46093f6ab6c4f",
                headers.get("Authorization"));
        assertEquals("1551113065", headers.get("X-TC-Timestamp"));
    }

    private static void updatesRenderLookupsWithoutBlocking() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            assertEquals("Coins: 42", session.lookup("Coins: 42", TextKind.SCOREBOARD_LINE));
            long deadline = System.currentTimeMillis() + 2000L;
            String translated;
            do {
                Thread.sleep(10L);
                translated = session.lookup("Coins: 42", TextKind.SCOREBOARD_LINE);
            } while ("Coins: 42".equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals("\u91d1\u5e01: 42", translated);
        }
    }

    private static void translatesTooltipLinesIndependently() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            java.util.List<String> original = Arrays.asList("Players online", "Coins");
            assertEquals(original, session.lookupIndependentLines(original, TextKind.TOOLTIP));
            long deadline = System.currentTimeMillis() + 2000L;
            java.util.List<String> translated;
            java.util.List<String> expected = Arrays.asList("\u5728\u7ebf\u73a9\u5bb6", "\u91d1\u5e01");
            do {
                Thread.sleep(10L);
                translated = session.lookupIndependentLines(original, TextKind.TOOLTIP);
            } while (!expected.equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals(expected, translated);
            assertEquals("Coins", provider.lastRequest.get());
            assertEquals(2, provider.calls.get());
        }
    }

    private static void translatesWrappedVisualLinesAsOneSentence() throws Exception {
        RecordingProvider provider = new RecordingProvider("欢迎来到 服务器");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            java.util.List<String> original = Arrays.asList("Welcome to", "the server");
            assertEquals(original, session.lookupLines(original, TextKind.CHAT));
            long deadline = System.currentTimeMillis() + 2000L;
            java.util.List<String> translated;
            do {
                Thread.sleep(10L);
                translated = session.lookupLines(original, TextKind.CHAT);
            } while (original.equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals("Welcome to the server", provider.lastRequest.get());
            assertEquals(1, provider.calls.get());
            assertEquals(Arrays.asList("欢迎来到", "服务器"), translated);
        }
    }

    private static void keepsScoreboardRowsIndependent() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u73b0\u91d1");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.setProtectedLiteralsSupplier(() -> Arrays.asList("PlayerOne"));
            assertEquals("Cash", session.lookup("Cash", TextKind.SCOREBOARD_LINE));
            assertEquals("PlayerOne 0", session.lookup("PlayerOne 0", TextKind.SCOREBOARD_LINE));

            long deadline = System.currentTimeMillis() + 2000L;
            String translatedTitle;
            do {
                Thread.sleep(10L);
                translatedTitle = session.lookup("Cash", TextKind.SCOREBOARD_LINE);
                session.lookup("PlayerOne 0", TextKind.SCOREBOARD_LINE);
            } while ("Cash".equals(translatedTitle) && System.currentTimeMillis() < deadline);

            assertEquals("\u73b0\u91d1", translatedTitle);
            assertEquals("PlayerOne 0", session.lookup("PlayerOne 0", TextKind.SCOREBOARD_LINE));
            assertEquals("Cash", provider.lastRequest.get());
            assertEquals(1, provider.calls.get());
        }
    }

    private static void joinsBookSentenceBreaksWithSpace() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u8bd1\u6587");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String original = "site you downloaded\nthis map from.\n\nThank you";
            session.lookup(original, TextKind.BOOK);
            long deadline = System.currentTimeMillis() + 2000L;
            while (provider.lastRequest.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertEquals("site you downloaded this map from. Thank you", provider.lastRequest.get());
        }
    }

    private static void preservesBookMenuRows() throws Exception {
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "book-menu-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                if ("Sellable items:".equals(text)) {
                    return "\u53ef\u51fa\u552e\u7269\u54c1:";
                }
                if ("Wheat seeds".equals(text)) {
                    return "\u5c0f\u9ea6\u79cd\u5b50";
                }
                if ("Beetroot seeds".equals(text)) {
                    return "\u751c\u83dc\u79cd\u5b50";
                }
                if ("Wheat".equals(text)) {
                    return "\u5c0f\u9ea6";
                }
                if ("Potatos".equals(text)) {
                    return "\u571f\u8c46";
                }
                if ("Carrots".equals(text)) {
                    return "\u80e1\u841d\u535c";
                }
                if ("Beetroot".equals(text)) {
                    return "\u751c\u83dc";
                }
                if ("Sugarcane".equals(text)) {
                    return "\u7518\u8517";
                }
                return text;
            }
        };
        String original = "Sellable items:\n\n---------------------\n\n"
                + "Wheat seeds ----- 0$\n"
                + "Beetroot seeds -- 0$\n"
                + "Wheat ----------- 2$\n"
                + "Potatos --------- 5$\n"
                + "Carrots --------- 9$\n"
                + "Beetroot -------- 95$\n"
                + "Sugarcane ------- 24$";
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String translated;
            long deadline = System.currentTimeMillis() + 2000L;
            do {
                Thread.sleep(10L);
                translated = session.lookup(original, TextKind.BOOK);
            } while (!translated.contains("\u7518\u8517")
                    && System.currentTimeMillis() < deadline);
            java.util.List<String> lines = VisualTextBoundaries.splitLines(translated);
            assertEquals(11, lines.size());
            assertEquals("\u53ef\u51fa\u552e\u7269\u54c1:", lines.get(0));
            assertEquals("---------------------", lines.get(2));
            assertMenuLine(lines.get(4), "\u5c0f\u9ea6\u79cd\u5b50", "0$");
            assertMenuLine(lines.get(5), "\u751c\u83dc\u79cd\u5b50", "0$");
            assertMenuLine(lines.get(6), "\u5c0f\u9ea6", "2$");
            assertMenuLine(lines.get(7), "\u571f\u8c46", "5$");
            assertMenuLine(lines.get(8), "\u80e1\u841d\u535c", "9$");
            assertMenuLine(lines.get(9), "\u751c\u83dc", "95$");
            assertMenuLine(lines.get(10), "\u7518\u8517", "24$");
            assertFalse(translated.contains("0\u751c\u83dc"));
            assertFalse(translated.contains("0\u5c0f\u9ea6"));
        }
    }

    private static void assertMenuLine(String line, String label, String value) {
        assertTrue(line.startsWith(label));
        assertTrue(line.endsWith(value));
        assertTrue(line.contains("--"));
    }

    private static void preservesSeparatorLinesInStructuredText() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u8bd1\u6587");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            java.util.List<String> sign = Arrays.asList("------", "Storage", "------", "");
            java.util.List<String> expectedSign = Arrays.asList("------", "\u8bd1\u6587", "------", "");
            java.util.List<String> translatedSign;
            long deadline = System.currentTimeMillis() + 2000L;
            do {
                Thread.sleep(10L);
                translatedSign = session.lookupLines(sign, TextKind.SIGN);
            } while (!expectedSign.equals(translatedSign) && System.currentTimeMillis() < deadline);
            assertEquals(expectedSign, translatedSign);
            assertEquals("Storage", provider.lastRequest.get());

            String book = "Intro\n------\nStorage";
            String expectedBook = "\u8bd1\u6587\n------\n\u8bd1\u6587";
            String translatedBook;
            deadline = System.currentTimeMillis() + 2000L;
            do {
                Thread.sleep(10L);
                translatedBook = session.lookup(book, TextKind.BOOK);
            } while (!expectedBook.equals(translatedBook) && System.currentTimeMillis() < deadline);
            assertEquals(expectedBook, translatedBook);
            assertFalse(provider.lastRequest.get().contains("------"));
        }
    }

    private static void preservesSignTokenPunctuation() throws Exception {
        assertEquals("\u5b9e\u7528\u63d0\u793a __UT_0__\uff1a \u5c06\u62f4\u7ef3",
                StructuredPunctuation.restoreTokenAdjacent(
                        "Helpful Tip __UT_0__: Tie Leads",
                        "\u5b9e\u7528\u63d0\u793a __UT_0__\uff1a\u5c06\u62f4\u7ef3"));
        final AtomicReference<String> lastRequest = new AtomicReference<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "sign-token-punctuation-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                lastRequest.set(request.getText());
                return "\u5b9e\u7528\u63d0\u793a __UT_0__ \u5c06\u62f4\u7ef3\u7cfb\u5230\u6805\u680f\u67f1\u4e0a";
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    "Helpful Tip 2: Tie Leads to Fence Posts",
                    "auto", "zh-CN", TextKind.SIGN).get(2, TimeUnit.SECONDS);
            assertEquals("\u5b9e\u7528\u63d0\u793a 2: \u5c06\u62f4\u7ef3\u7cfb\u5230\u6805\u680f\u67f1\u4e0a",
                    result.getTranslatedText());
            String request = lastRequest.get();
            assertEquals("Helpful Tip __UT_0__: Tie Leads to Fence Posts", request);
            assertFalse(request.contains("2"));
        }
    }

    private static void translatesHologramTextBlocksAsOneSentence() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u6216\u70b9\u51fb\u4e0b\u8f7d");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            java.util.List<String> expected = Arrays.asList(
                    "\u6216\u70b9", "\u51fb\u4e0b", "\u8f7d");
            assertEquals("Or", session.lookup("Or", TextKind.HOLOGRAM));
            assertEquals("download it", session.lookup("download it", TextKind.HOLOGRAM));
            assertEquals("by clicking", session.lookup("by clicking", TextKind.HOLOGRAM));
            assertEquals(0, provider.calls.get());

            session.lookup("Or", TextKind.HOLOGRAM);
            session.lookup("download it", TextKind.HOLOGRAM);
            session.lookup("by clicking", TextKind.HOLOGRAM);

            long deadline = System.currentTimeMillis() + 2000L;
            while (provider.calls.get() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            deadline = System.currentTimeMillis() + 2000L;
            String translated;
            String second;
            String third;
            java.util.List<String> actual;
            do {
                translated = session.lookup("Or", TextKind.HOLOGRAM);
                second = session.lookup("download it", TextKind.HOLOGRAM);
                third = session.lookup("by clicking", TextKind.HOLOGRAM);
                actual = Arrays.asList(translated, second, third);
                Thread.sleep(10L);
            } while (!expected.equals(actual) && System.currentTimeMillis() < deadline);

            assertEquals("Or download it by clicking", provider.lastRequest.get());
            assertEquals(1, provider.calls.get());
            assertEquals(expected, actual);
        }
    }

    private static void groupsHologramsBeforeUsingFragmentTranslations() throws Exception {
        AtomicReference<String> requests = new AtomicReference<String>("");
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "hologram-fragment-cache-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                requests.set(requests.get() + "|" + request.getText());
                if ("Or".equals(request.getText())) {
                    return "\u6216";
                }
                if ("download it".equals(request.getText())) {
                    return "\u4e0b\u8f7d\u5b83";
                }
                if ("by clicking".equals(request.getText())) {
                    return "\u901a\u8fc7\u70b9\u51fb";
                }
                if ("Or download it by clicking".equals(request.getText())) {
                    return "\u6216\u70b9\u51fb\u4e0b\u8f7d";
                }
                if ("__UT_0__Or__UT_1__".equals(request.getText())) {
                    return "__UT_0__\u6216__UT_1__";
                }
                if ("__UT_0__download it__UT_1__".equals(request.getText())) {
                    return "__UT_0__\u4e0b\u8f7d它__UT_1__";
                }
                if ("__UT_0__by clicking__UT_1__".equals(request.getText())) {
                    return "__UT_0__\u901a\u8fc7\u70b9\u51fb__UT_1__";
                }
                return request.getText();
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            waitForCompleteLookup(session, "Or");
            waitForCompleteLookup(session, "download it");
            waitForCompleteLookup(session, "by clicking");

            java.util.List<String> expected = Arrays.asList(
                    "\u6216\u70b9", "\u51fb\u4e0b", "\u8f7d");
            for (int frame = 0; frame < 3; frame++) {
                lookupHologramTwice(session, "Or");
                lookupHologramTwice(session, "download it");
                lookupHologramTwice(session, "by clicking");
            }

            long deadline = System.currentTimeMillis() + 2000L;
            java.util.List<String> actual;
            do {
                actual = Arrays.asList(
                        lookupHologramTwice(session, "Or"),
                        lookupHologramTwice(session, "download it"),
                        lookupHologramTwice(session, "by clicking"));
                if (expected.equals(actual)) {
                    break;
                }
                Thread.sleep(10L);
            } while (System.currentTimeMillis() < deadline);

            assertTrue(requests.get().contains("|Or download it by clicking"));
            assertFalse(actual.contains("\u6216"));
            assertFalse(actual.contains("\u4e0b\u8f7d\u5b83"));
            assertFalse(actual.contains("\u901a\u8fc7\u70b9\u51fb"));
            assertEquals(expected, actual);
        }
    }

    private static void groupsLargeHologramMenus() {
        VisualLineGrouper grouper = new VisualLineGrouper();
        java.util.List<String> lines = Arrays.asList(
                "------------------------------",
                "Click [HERE] to install the Apiary Resource Pack!",
                "------------------------------",
                "------------------------------",
                "Settings",
                "Difficulty: Easy Normal Hard",
                "Player Glowing: ON OFF",
                "[item/heart@icons] [item/skull@icons] - Sidebar",
                "[item/heart@icons] [item/skull@icons] - Below Names",
                "[item/heart@icons] [item/skull@icons] - Player List",
                "------------------------------");
        java.util.List<String> groupedLines = Arrays.asList(
                "Click [HERE] to install the Apiary Resource Pack!",
                "Settings",
                "Difficulty: Easy Normal Hard",
                "Player Glowing: ON OFF",
                "[item/heart@icons] [item/skull@icons] - Sidebar",
                "[item/heart@icons] [item/skull@icons] - Below Names",
                "[item/heart@icons] [item/skull@icons] - Player List");
        for (String line : lines) {
            VisualLineGrouper.Group group = grouper.group(line, TextKind.HOLOGRAM);
            assertTrue(group != null && group.collecting());
        }
        for (String line : lines) {
            VisualLineGrouper.Group group = grouper.group(line, TextKind.HOLOGRAM);
            if (VisualTextBoundaries.isSeparatorLine(line)) {
                assertTrue(group != null && group.collecting());
            } else {
                assertTrue(group != null && !group.collecting());
                assertEquals(groupedLines, group.lines);
            }
        }
    }

    private static String lookupHologramTwice(RenderTranslationSession session, String text) {
        String first = session.lookup(text, TextKind.HOLOGRAM);
        session.lookup(text, TextKind.HOLOGRAM);
        return first;
    }

    private static void translatesCompleteHologramBeforeWrapping() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u9996\u5148\u770b\u5230\u91d1\u8272\u65b9\u5757");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String original = "First of all\nsee a golden\nblock";
            String translated = waitForCompleteLookup(session, original);
            assertEquals("__UT_0__First of all see a golden block__UT_1__",
                    provider.lastRequest.get());
            assertEquals(1, provider.calls.get());
            assertEquals("\u9996\u5148\u770b\u5230\u91d1\u8272\u65b9\u5757", translated);
        }
    }

    private static void normalizesChineseDateOrder() {
        assertEquals("活动日期：2026年3月27日",
                LocalizedDateOrder.normalize("活动日期：3月27日，2026", "zh-CN"));
        assertEquals("2026年3月27日和2027年11月8日",
                LocalizedDateOrder.normalize(
                        "3 月 27 日, 2026和11月8日，2027年", "zh-TW"));
        assertEquals("March 27, 2026",
                LocalizedDateOrder.normalize("March 27, 2026", "zh-CN"));
        assertEquals("3月27日，2026",
                LocalizedDateOrder.normalize("3月27日，2026", "en"));
    }

    private static void validatesChineseConditionClauseOrder() {
        String source = "Your attack cooldown will not reset when you miss or switch weapons.";
        LocalizedClauseOrder.requireNatural(source,
                "你的攻击冷却在你打空或切换武器时不会重置。", "zh-CN");
        LocalizedClauseOrder.requireNatural(source,
                "當你未命中或切換武器時，你的攻擊冷卻不會重置。", "zh-TW");
        assertThrows(() -> LocalizedClauseOrder.requireNatural(source,
                "你的攻击冷却不会重置在你打空或切换武器时。", "zh-CN"));
        assertThrows(() -> LocalizedClauseOrder.requireNatural(source,
                "你的攻擊冷卻不會重置，當你未命中或切換武器時。", "zh-TW"));
        assertEquals("你的攻击冷却在你打空或切换武器时不会重置。",
                LocalizedClauseOrder.normalize(source,
                        "你的攻击冷却不会重置在你打空或切换武器时。", "zh-CN"));
        assertEquals("當你未命中或切換武器時，你的攻擊冷卻不會重置。",
                LocalizedClauseOrder.normalize(source,
                        "你的攻擊冷卻不會重置，當你未命中或切換武器時。", "zh-TW"));

        String styled = "{UT_HOLOGRAM_BLOCK_0_START}[item/diamond_pickaxe@items]"
                + "你的攻击冷却{UT_STYLE_0_START}不会重置{UT_STYLE_0_END}"
                + "{UT_STYLE_1_START}在你打空或切换武器时{UT_STYLE_1_END}"
                + "。{UT_HOLOGRAM_BLOCK_0_END}";
        assertThrows(() -> LocalizedClauseOrder.requireNatural(source, styled, "zh-CN"));
        assertEquals("{UT_HOLOGRAM_BLOCK_0_START}[item/diamond_pickaxe@items]"
                        + "你的攻击冷却{UT_STYLE_1_START}在你打空或切换武器时"
                        + "{UT_STYLE_1_END}{UT_STYLE_0_START}不会重置"
                        + "{UT_STYLE_0_END}。{UT_HOLOGRAM_BLOCK_0_END}",
                LocalizedClauseOrder.normalize(source, styled, "zh-CN"));
        LocalizedClauseOrder.requireNatural(source,
                "你的攻击冷却不会重置在你打空或切换武器时。", "en");
    }

    private static void repairsUnnaturalChineseConditionClauseOrder() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "clause-order-all-paths-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                return calls.incrementAndGet() == 1
                        ? "你的攻击冷却不会重置当你打空或切换武器时"
                        : "你的攻击冷却在你打空或切换武器时不会重置";
            }
        };
        TranslationStore cache = new TranslationCache(16);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(provider, cache, 1)) {
            String source = "Your attack cooldown will not reset when you miss or switch weapons";
            TranslationResult translated = coordinator.translate(
                    source, "auto", "zh-CN", TextKind.CHAT).get(2, TimeUnit.SECONDS);
            assertEquals("你的攻击冷却在你打空或切换武器时不会重置",
                    translated.getTranslatedText());
            TranslationResult cached = coordinator.cachedTranslation(
                    source, "auto", "zh-CN", TextKind.CHAT,
                    Arrays.<String>asList(), false);
            assertEquals("你的攻击冷却在你打空或切换武器时不会重置",
                    cached.getTranslatedText());
            assertEquals(1, calls.get());
        }

        final AtomicInteger structuredCalls = new AtomicInteger();
        String pickaxe = "[item/diamond_pickaxe@items]";
        TranslationProvider structuredProvider = new TranslationProvider() {
            @Override
            public String id() {
                return "clause-order-structured-retry-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                return structuredCalls.incrementAndGet() == 1
                        ? "你的攻击冷却不会重置在你打空或切换武器时"
                        : "你的攻击冷却在你打空或切换武器时不会重置";
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                structuredProvider, new TranslationCache(16), 1)) {
            TranslationResult result = coordinator.translate(
                    pickaxe + "Your attack cooldown will not reset when you miss or switch weapons",
                    "auto", "zh-CN", TextKind.HOLOGRAM).get(2, TimeUnit.SECONDS);
            assertEquals(pickaxe + "你的攻击冷却在你打空或切换武器时不会重置",
                    result.getTranslatedText());
            assertEquals(1, structuredCalls.get());
        }

        final AtomicInteger styledCalls = new AtomicInteger();
        String style0Start = "{UT_STYLE_0_START}";
        String style0End = "{UT_STYLE_0_END}";
        String style1Start = "{UT_STYLE_1_START}";
        String style1End = "{UT_STYLE_1_END}";
        String styledSource = "Your attack cooldown " + style0Start
                + "does not reset" + style0End + style1Start
                + " when you miss or switch weapons" + style1End;
        TranslationProvider styledProvider = new TranslationProvider() {
            @Override
            public String id() {
                return "clause-order-styled-repair-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                styledCalls.incrementAndGet();
                assertTrue(request.getText().contains("__UT_0__"));
                return "你的攻击冷却__UT_0__不会重置__UT_1__"
                        + "__UT_2__在你未命中或切换武器时__UT_3__";
            }
        };
        String styledExpected = "你的攻击冷却" + style1Start
                + "在你未命中或切换武器时" + style1End
                + style0Start + "不会重置" + style0End;
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                styledProvider, new TranslationCache(16), 1)) {
            TranslationResult result = coordinator.translate(
                    styledSource, "auto", "zh-CN", TextKind.HOLOGRAM)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals(styledExpected, result.getTranslatedText());
            assertEquals(1, styledCalls.get());

            TranslationResult cached = coordinator.cachedTranslation(
                    styledSource, "auto", "zh-CN", TextKind.HOLOGRAM,
                    Arrays.<String>asList(), false);
            assertTrue(cached != null && cached.isTranslated());
            assertEquals(styledExpected, cached.getTranslatedText());
            assertEquals(1, styledCalls.get());
        }
    }

    private static void alignsTranslatedHologramTops() {
        assertEquals(Float.valueOf(0.0F), Float.valueOf(HologramTopAnchor.offset(2, 2)));
        assertEquals(Float.valueOf(-0.5F), Float.valueOf(HologramTopAnchor.offset(1, 3)));
        assertEquals(Float.valueOf(0.5F), Float.valueOf(HologramTopAnchor.offset(3, 1)));
    }

    private static void preservesCompleteHologramDateOrder() throws Exception {
        final AtomicReference<String> lastRequest = new AtomicReference<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "hologram-date-order-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                lastRequest.set(request.getText());
                return "__UT_0__\u6b22\u8fce\u6765\u5230__UT_1__\u5c0f\u65f6\u5947\u8ff9\uff1a\u517b\u8702\u573a\uff0c"
                        + "\u8fd9\u5f20 CTM \u5730\u56fe\u4e8e3\u6708__UT_3__\u65e5\uff0c__UT_4__\u7528__UT_2__\u5c0f\u65f6\u5efa\u6210\uff01"
                        + "\u8fd9\u4e9b\u6d3b\u52a8\u6301\u7eed__UT_5__\u5c0f\u65f6\uff0c\u4ee5\u63d0\u4f9b\u66f4\u9002\u5408\u4e0d\u540c\u65f6\u533a\u7684\u4f53\u9a8c__UT_6__";
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String original = "Welcome to 24-Hour Miracle: Apiary, a CTM map that was\n"
                    + "built in 24 Hours on March 27, 2026! These events last for 24\n"
                    + "hours to make for a more time-zone friendly experience";
            String translated = waitForCompleteLookup(session, original);
            assertEquals("\u6b22\u8fce\u6765\u523024\u5c0f\u65f6\u5947\u8ff9\uff1a\u517b\u8702\u573a\uff0c\u8fd9\u5f20 CTM \u5730\u56fe\u4e8e"
                    + "2026\u5e743\u670827\u65e5\u752824\u5c0f\u65f6\u5efa\u6210\uff01\u8fd9\u4e9b\u6d3b\u52a8\u6301\u7eed24\u5c0f\u65f6\uff0c"
                    + "\u4ee5\u63d0\u4f9b\u66f4\u9002\u5408\u4e0d\u540c\u65f6\u533a\u7684\u4f53\u9a8c", translated);
            assertEquals("__UT_0__Welcome to __UT_1__-Hour Miracle: Apiary, a CTM map that was "
                    + "built in __UT_2__ Hours on March __UT_3__, __UT_4__! These events last for "
                    + "__UT_5__ hours to make for a more time-zone friendly experience__UT_6__",
                    lastRequest.get());
            assertEquals(1, VisualTextBoundaries.splitLines(translated).size());
        }
    }

    private static void preservesHologramMenuAndTextureRows() {
        String sword = "[item/iron_sword@items]";
        String pickaxe = "[item/iron_pickaxe@items]";
        String bow = "[item/bow@items]";
        String spear = "[item/diamond_spear@items]";
        String indicator = "[hud/crosshair_attack_indicator_full@gui]";
        String textureRule = indicator + indicator + indicator;
        String aqua = "\u00a7b";
        String original = "Difficulty: Easy Normal Hard\n"
                + "Player Glowing: ON OFF\n"
                + aqua + sword + "Your attacks have no invuln-frames\n"
                + pickaxe + "Your attack cooldown does not reset\n"
                + "when you miss or switch weapons\n"
                + "----------------\n"
                + textureRule + "\n"
                + bow + "Partial swings will not hit, only full\n"
                + "swings work\n"
                + spear + "Critical hits deal more damage";
        HologramTextLayout.Plan plan = HologramTextLayout.prepare(original);
        assertTrue(plan != null);
        assertFalse(InlineTextureCode.matcher(plan.request()).find());
        String translated = plan.restore(
                "{UT_HOLOGRAM_BLOCK_0_START}\u96be\u5ea6\uff1a\u7b80\u5355 \u666e\u901a \u56f0\u96be"
                        + "{UT_HOLOGRAM_BLOCK_0_END}\n"
                        + "{UT_HOLOGRAM_BLOCK_1_START}\u73a9\u5bb6\u53d1\u5149\uff1a\u5f00\u542f \u5173\u95ed"
                        + "{UT_HOLOGRAM_BLOCK_1_END}\n"
                        + "{UT_HOLOGRAM_BLOCK_2_START}" + aqua
                        + "\u4f60\u7684\u653b\u51fb\u6ca1\u6709\u65e0\u654c\u5e27"
                        + "{UT_HOLOGRAM_BLOCK_2_END}\n"
                        + "{UT_HOLOGRAM_BLOCK_3_START}"
                        + "\u4f60\u7684\u653b\u51fb\u51b7\u5374\u5728\u4f60\u6253\u7a7a\u6216\u5207\u6362\u6b66\u5668\u65f6\u4e0d\u4f1a\u91cd\u7f6e"
                        + "{UT_HOLOGRAM_BLOCK_3_END}\n"
                        + "{UT_HOLOGRAM_BLOCK_4_START}"
                        + "\u90e8\u5206\u6325\u51fb\u65e0\u6548\uff0c\u53ea\u6709\u5b8c\u6574\u6325\u51fb\u624d\u4f1a\u547d\u4e2d"
                        + "{UT_HOLOGRAM_BLOCK_4_END}\n"
                        + "{UT_HOLOGRAM_BLOCK_5_START}\u66b4\u51fb\u4f1a\u9020\u6210\u66f4\u9ad8\u4f24\u5bb3"
                        + "{UT_HOLOGRAM_BLOCK_5_END}");
        assertEquals("\u96be\u5ea6\uff1a\u7b80\u5355 \u666e\u901a \u56f0\u96be\n"
                + "\u73a9\u5bb6\u53d1\u5149\uff1a\u5f00\u542f \u5173\u95ed\n"
                + aqua + sword + "\u4f60\u7684\u653b\u51fb\u6ca1\u6709\u65e0\u654c\u5e27\n"
                + pickaxe + "\u4f60\u7684\u653b\u51fb\u51b7\u5374\u5728\u4f60\u6253\u7a7a\u6216\u5207\u6362\u6b66\u5668\u65f6\u4e0d\u4f1a\u91cd\u7f6e\n"
                + "----------------\n"
                + textureRule + "\n"
                + bow + "\u90e8\u5206\u6325\u51fb\u65e0\u6548\uff0c\u53ea\u6709\u5b8c\u6574\u6325\u51fb\u624d\u4f1a\u547d\u4e2d\n"
                + spear + "\u66b4\u51fb\u4f1a\u9020\u6210\u66f4\u9ad8\u4f24\u5bb3", translated);
        assertEquals(1, countOccurrences(translated, sword));
        assertEquals(1, countOccurrences(translated, pickaxe));
        assertEquals(1, countOccurrences(translated, bow));
        assertEquals(1, countOccurrences(translated, spear));
        assertEquals(3, countOccurrences(translated, indicator));
        java.util.List<String> lines = VisualTextBoundaries.splitLines(translated);
        assertTrue(lines.get(2).startsWith(aqua + sword));
        assertTrue(lines.get(3).startsWith(pickaxe));
        assertEquals(textureRule, lines.get(5));
        assertTrue(lines.get(6).startsWith(bow));
        assertTrue(lines.get(7).startsWith(spear));
    }

    private static void keepsCrossLineStyledHologramSentencesTogether() {
        String sword = "[item/iron_sword@items]";
        String pickaxe = "[item/diamond_pickaxe@items]";
        String bow = "[item/bow@items]";
        String style0Start = "{UT_STYLE_0_START}";
        String style0End = "{UT_STYLE_0_END}";
        String style1Start = "{UT_STYLE_1_START}";
        String style1End = "{UT_STYLE_1_END}";
        String style2Start = "{UT_STYLE_2_START}";
        String style2End = "{UT_STYLE_2_END}";
        String original = sword + "Your attacks " + style0Start
                + "have no invuln-frames\n" + style0End
                + pickaxe + "Your attack cooldown " + style1Start
                + "does not reset" + style1End + "\n" + style2Start
                + "when you miss or switch weapons\n" + style2End
                + bow + "Partial swings work";

        HologramTextLayout.Plan plan = HologramTextLayout.prepare(original);
        assertTrue(plan != null);
        String request = plan.request();
        assertTrue(request.contains("Your attacks " + style0Start
                + "have no invuln-frames" + style0End));
        assertTrue(request.contains("Your attack cooldown " + style1Start
                + "does not reset" + style1End + " " + style2Start
                + "when you miss or switch weapons" + style2End));

        String translatedTemplate = request
                .replace("Your attacks " + style0Start
                                + "have no invuln-frames" + style0End,
                        "你的攻击" + style0Start + "没有无敌帧" + style0End)
                .replace("Your attack cooldown " + style1Start
                                + "does not reset" + style1End + " " + style2Start
                                + "when you miss or switch weapons" + style2End,
                        "你的攻击冷却" + style2Start + "在你未命中或切换武器时"
                                + style2End + style1Start + "不会重置" + style1End)
                .replace("Partial swings work", "完整挥击有效");
        String restored = plan.restore(translatedTemplate);
        assertTrue(restored != null);
        String visible = StyledTranslationTemplate.strip(restored);
        assertTrue(visible.contains(pickaxe
                + "你的攻击冷却在你未命中或切换武器时不会重置"));
        assertEquals(3, VisualTextBoundaries.splitLines(visible).size());
        assertEquals(1, countOccurrences(restored, sword));
        assertEquals(1, countOccurrences(restored, pickaxe));
        assertEquals(1, countOccurrences(restored, bow));
    }

    private static void segmentsComplexHologramsByLogicalBlock() throws Exception {
        String sword = "[item/iron_sword@items]";
        String pickaxe = "[item/iron_pickaxe@items]";
        String bow = "[item/bow@items]";
        String spear = "[item/diamond_spear@items]";
        String original = "Difficulty: Easy Normal Hard\n"
                + "Player Glowing: ON OFF\n"
                + "\u00a7b" + sword + "Your attacks have no invuln-frames\n"
                + pickaxe + "Your attack cooldown does not reset\n"
                + "when you miss or switch weapons\n"
                + bow + "Partial swings will not hit, only full\n"
                + "swings work\n"
                + spear + "Critical hits deal more damage";
        HologramTextLayout.Plan plan = HologramTextLayout.prepare(original);
        assertTrue(plan != null);
        ProtectedText protectedText = ProtectedText.parse(plan.request());
        assertTrue(StructuredTemplateTranslator.requiresHologramSegmentation(
                protectedText, TextKind.HOLOGRAM));

        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "hologram-block-segmentation-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                requests.add(text);
                assertFalse(VisualTextLayout.containsLineBreak(text));
                assertTrue(countOccurrences(text, "__UT_") <= 2);
                return text.replace("Difficulty: Easy Normal Hard", "难度：简单 普通 困难")
                        .replace("Player Glowing: ON OFF", "玩家发光：开启 关闭")
                        .replace("Your attacks have no invuln-frames", "你的攻击没有无敌帧")
                        .replace("Your attack cooldown does not reset when you miss or switch weapons",
                                "你的攻击冷却在你打空或切换武器时不会重置")
                        .replace("Partial swings will not hit, only full swings work",
                                "部分挥击无效，只有完整挥击才会命中")
                        .replace("Critical hits deal more damage", "暴击会造成更高伤害");
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    plan.request(), "auto", "zh-CN", TextKind.HOLOGRAM)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            String restored = plan.restore(result.getTranslatedText());
            assertTrue(restored != null);
            assertTrue(restored.contains(pickaxe
                    + "你的攻击冷却在你打空或切换武器时不会重置"));
            assertEquals(6, requests.size());
            assertTrue(requests.toString().contains(
                    "Your attack cooldown does not reset when you miss or switch weapons"));

            int calls = requests.size();
            TranslationResult cached = coordinator.cachedTranslation(
                    plan.request(), "auto", "zh-CN", TextKind.HOLOGRAM,
                    Arrays.<String>asList(), false);
            assertTrue(cached != null && cached.isTranslated());
            assertEquals(calls, requests.size());
        }
    }

    private static void fallsBackWhenHologramBlockChangesProtectedTokens() throws Exception {
        String source = "{UT_HOLOGRAM_BLOCK_0_START}"
                + "{UT_STYLE_0_START}Settings{UT_STYLE_0_END} "
                + "{UT_STYLE_1_START}Easy{UT_STYLE_1_END} "
                + "{UT_STYLE_2_START}Normal{UT_STYLE_2_END} "
                + "{UT_STYLE_3_START}Hard{UT_STYLE_3_END} "
                + "{UT_STYLE_4_START}ON{UT_STYLE_4_END} "
                + "{UT_STYLE_5_START}OFF{UT_STYLE_5_END}"
                + "{UT_HOLOGRAM_BLOCK_0_END}";
        AtomicInteger structuredAttempts = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "hologram-token-fallback-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                if (countOccurrences(text, "__UT_") > 4) {
                    structuredAttempts.incrementAndGet();
                    return text.replaceFirst("__UT_\\d+__", "");
                }
                return text.replace("Settings", "设置")
                        .replace("Easy", "简单")
                        .replace("Normal", "普通")
                        .replace("Hard", "困难")
                        .replace("ON", "开")
                        .replace("OFF", "关");
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    source, "auto", "zh-CN", TextKind.HOLOGRAM)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals(2, structuredAttempts.get());
            String visible = StyledTranslationTemplate.strip(result.getTranslatedText());
            assertEquals("{UT_HOLOGRAM_BLOCK_0_START}设置 简单 普通 困难 开 关"
                    + "{UT_HOLOGRAM_BLOCK_0_END}", visible);

            TranslationResult cached = coordinator.cachedTranslation(
                    source, "auto", "zh-CN", TextKind.HOLOGRAM,
                    Arrays.<String>asList(), false);
            assertTrue(cached != null && cached.isTranslated());
            assertEquals(result.getTranslatedText(), cached.getTranslatedText());
        }
    }

    private static void preservesHologramBracketBoundaries() throws Exception {
        java.util.List<String> textureSegments = VisualTextBoundaries.splitBracketSegments(
                "[item/diamond_spear@items]Attack [Option]After");
        assertEquals(3, textureSegments.size());
        assertEquals("[item/diamond_spear@items]Attack", textureSegments.get(0));
        assertEquals("[Option]", textureSegments.get(1));
        assertEquals("After", textureSegments.get(2));

        RecordingProvider provider = new RecordingProvider("\u524d\u7f00\u3010\u9009\u9879\u3011\u5c3e\u90e8");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String original = "Before\n[Option]\nAfter";
            assertEquals("\u524d\u7f00\n\u3010\u9009\u9879\u3011\n\u5c3e\u90e8", waitForCompleteLookup(session, original));
            assertEquals("__UT_0__Before[Option]After__UT_1__", provider.lastRequest.get());
        }
    }

    private static void removesUnusedHologramSourceBlocks() throws Exception {
        RecordingProvider provider = new RecordingProvider("\u8bd1\u6587");
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            String original = "First\nSecond\nThird";
            assertEquals("\u8bd1\u6587", waitForCompleteLookup(session, original));
        }
    }

    private static int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while (text != null && value != null && !value.isEmpty()
                && (index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static String waitForCompleteLookup(
            RenderTranslationSession session, String original) throws Exception {
        long deadline = System.currentTimeMillis() + 2000L;
        String translated;
        do {
            translated = session.lookupComplete(original, TextKind.HOLOGRAM);
            if (!original.equals(translated)) {
                return translated;
            }
            Thread.sleep(10L);
        } while (System.currentTimeMillis() < deadline);
        return translated;
    }

    private static void exposesRenderTranslationFailures() throws Exception {
        CountingProvider provider = new CountingProvider(true);
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.lookup("Server restarting", TextKind.TITLE);
            long deadline = System.currentTimeMillis() + 2000L;
            while (session.lastFailureStatus().isEmpty()
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertTrue(session.lastFailureStatus().startsWith("翻译失败：simulated outage"));
        }
    }

    private static void suppressesRecoverableRenderFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "recoverable-output-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                if ("Recovered text".equals(request.getText())) {
                    return "\u5df2\u6062\u590d\u6587\u672c";
                }
                return TranslationOutputValidator.requireValid(
                        request.getText(), request.getText(), request.getTargetLanguage());
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.lookup("Unmapped fragment", TextKind.CHAT);
            long deadline = System.currentTimeMillis() + 2000L;
            while (calls.get() < 2
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertTrue(calls.get() >= 2);
            Thread.sleep(50L);
            assertEquals("", session.lastFailureStatus());

            String translated = "Recovered text";
            deadline = System.currentTimeMillis() + 2000L;
            while ("Recovered text".equals(translated)
                    && System.currentTimeMillis() < deadline) {
                translated = session.lookup("Recovered text", TextKind.CHAT);
                Thread.sleep(10L);
            }
            assertEquals("\u5df2\u6062\u590d\u6587\u672c", translated);
            assertEquals("", session.lastFailureStatus());
        }
    }

    private static void protectsLiteralsOffTheRenderThread() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        AtomicReference<String> iterationThread = new AtomicReference<String>();
        Iterable<String> names = new Iterable<String>() {
            @Override
            public java.util.Iterator<String> iterator() {
                iterationThread.set(Thread.currentThread().getName());
                return Arrays.asList("Steve_42", "Alex_7").iterator();
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            session.setProtectedLiteralsSupplier(() -> names);
            long started = System.nanoTime();
            assertEquals("Welcome Steve_42", session.lookup("Welcome Steve_42", TextKind.CHAT));
            long callerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(callerMillis < 250L);

            long deadline = System.currentTimeMillis() + 2000L;
            while (iterationThread.get() == null && System.currentTimeMillis() < deadline) {
                session.lookup("Welcome Steve_42", TextKind.CHAT);
                Thread.sleep(10L);
            }
            assertTrue(iterationThread.get() != null
                    && iterationThread.get().startsWith("universal-translator-"));
        }
    }

    private static void boundsBusyLobbyTranslationWork() throws Exception {
        BlockingProvider provider = new BlockingProvider();
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            long started = System.nanoTime();
            for (int index = 0; index < 5_000; index++) {
                session.lookup("Player message " + index, TextKind.OTHER);
            }
            long callerMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(callerMillis < 1_000L);
        } finally {
            provider.release.countDown();
        }
    }

    private static void reservesPendingCapacityForForegroundText() {
        PendingTranslationBudget budget = new PendingTranslationBudget(128, 32);
        for (int index = 0; index < 128; index++) {
            assertTrue(budget.tryAcquire(false));
        }
        assertFalse(budget.tryAcquire(false));
        for (int index = 0; index < 32; index++) {
            assertTrue(budget.tryAcquire(true));
        }
        assertFalse(budget.tryAcquire(true));
        assertEquals(128, budget.backgroundCount());
        assertEquals(32, budget.foregroundCount());
        budget.release(false);
        budget.release(true);
        assertTrue(budget.tryAcquire(false));
        assertTrue(budget.tryAcquire(true));
        budget.reset();
        assertEquals(0, budget.backgroundCount());
        assertEquals(0, budget.foregroundCount());
    }

    private static void reservesPendingCapacityForPlayerChat() {
        PendingTranslationBudget budget = new PendingTranslationBudget(128, 32, 16);
        for (int index = 0; index < 32; index++) {
            assertTrue(budget.tryAcquire(true));
        }
        assertFalse(budget.tryAcquire(true));
        for (int index = 0; index < 16; index++) {
            assertTrue(budget.tryAcquireChat());
        }
        assertFalse(budget.tryAcquireChat());
        assertEquals(32, budget.foregroundCount());
        assertEquals(16, budget.chatCount());
        budget.releaseChat();
        assertEquals(15, budget.chatCount());
    }

    private static void reservesPendingCapacityForSystemMessages() {
        PendingTranslationBudget budget = new PendingTranslationBudget(128, 32, 16, 24);
        for (int index = 0; index < 32; index++) {
            assertTrue(budget.tryAcquire(true));
        }
        assertFalse(budget.tryAcquire(true));
        for (int index = 0; index < 24; index++) {
            assertTrue(budget.tryAcquireSystemMessage());
        }
        assertFalse(budget.tryAcquireSystemMessage());
        assertEquals(32, budget.foregroundCount());
        assertEquals(24, budget.systemMessageCount());
        budget.releaseSystemMessage();
        assertEquals(23, budget.systemMessageCount());
    }

    private static void rateLimitsBusyLobbyWithoutStarvingTooltips() throws Exception {
        KindRecordingProvider provider = new KindRecordingProvider();
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            for (int index = 0; index < 500; index++) {
                session.lookup("Transient lobby label " + index, TextKind.OTHER);
            }
            session.lookup("Special tooltip description", TextKind.TOOLTIP);
            long deadline = System.currentTimeMillis() + 2_000L;
            while (provider.lastKind.get() != TextKind.TOOLTIP
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertEquals(TextKind.TOOLTIP, provider.lastKind.get());
            assertTrue(provider.calls.get() <= 5);
        }
    }

    private static void urgentTitlesBypassBlockedNormalQueue() throws Exception {
        UrgentBypassProvider provider = new UrgentBypassProvider();
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 2)) {
            coordinator.translate("Normal queued one", "auto", "zh-CN", TextKind.OTHER);
            coordinator.translate("Normal queued two", "auto", "zh-CN", TextKind.OTHER);
            assertTrue(provider.normalStarted.await(1, TimeUnit.SECONDS));
            TranslationResult title = coordinator.translate(
                    "Server restarting", "auto", "zh-CN", TextKind.TITLE)
                    .get(500, TimeUnit.MILLISECONDS);
            assertEquals("服务器正在重启", title.getTranslatedText());
        } finally {
            provider.releaseNormal.countDown();
        }
    }

    private static void foregroundMessagesBypassBlockedWorldQueue() throws Exception {
        ForegroundBypassProvider provider = new ForegroundBypassProvider();
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 2)) {
            coordinator.translate("World label one", "auto", "zh-CN", TextKind.OTHER);
            coordinator.translate("World label two", "auto", "zh-CN", TextKind.HOLOGRAM);
            assertTrue(provider.backgroundStarted.await(1, TimeUnit.SECONDS));
            TranslationResult chat = coordinator.translate(
                    "Player joined", "auto", "zh-CN", TextKind.CHAT)
                    .get(500, TimeUnit.MILLISECONDS);
            TranslationResult system = coordinator.translate(
                    "Objective complete", "auto", "zh-CN", TextKind.SYSTEM_MESSAGE)
                    .get(500, TimeUnit.MILLISECONDS);
            assertEquals("玩家已加入", chat.getTranslatedText());
            assertEquals("目标已完成", system.getTranslatedText());
        } finally {
            provider.releaseBackground.countDown();
        }
    }

    private static void systemMessagesBypassBlockedUrgentHudMessages() throws Exception {
        CountDownLatch hudStarted = new CountDownLatch(2);
        CountDownLatch releaseHud = new CountDownLatch(1);
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "system-message-reserve-test";
            }

            @Override
            public String translate(TranslationRequest request) throws Exception {
                if (request.getKind() == TextKind.TITLE
                        || request.getKind() == TextKind.ACTION_BAR) {
                    hudStarted.countDown();
                    releaseHud.await();
                    return request.getText();
                }
                if (request.getKind() == TextKind.SYSTEM_MESSAGE) {
                    return "\u6570\u636e\u5305\u6d88\u606f\u4ecd\u53ef\u7528";
                }
                return request.getText();
            }
        };
        try {
            try (TranslationCoordinator coordinator = new TranslationCoordinator(
                    provider, new TranslationCache(100), 2)) {
                coordinator.translate(
                        "Blocking title", "auto", "zh-CN", TextKind.TITLE);
                coordinator.translate(
                        "Blocking action bar", "auto", "zh-CN", TextKind.ACTION_BAR);
                assertTrue(hudStarted.await(1, TimeUnit.SECONDS));

                TranslationResult system = coordinator.translate(
                        "Datapack message still works", "auto", "zh-CN",
                        TextKind.SYSTEM_MESSAGE).get(500, TimeUnit.MILLISECONDS);
                assertEquals("\u6570\u636e\u5305\u6d88\u606f\u4ecd\u53ef\u7528",
                        system.getTranslatedText());
            }
        } finally {
            releaseHud.countDown();
        }
    }

    private static void playerChatBypassesBlockedSystemMessages() throws Exception {
        CountDownLatch systemsStarted = new CountDownLatch(2);
        CountDownLatch releaseSystems = new CountDownLatch(1);
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "player-chat-reserve-test";
            }

            @Override
            public String translate(TranslationRequest request) throws Exception {
                if (request.getKind() == TextKind.SYSTEM_MESSAGE) {
                    systemsStarted.countDown();
                    releaseSystems.await();
                    return "\u7cfb\u7edf\u6d88\u606f";
                }
                if (request.getKind() == TextKind.CHAT) {
                    return "\u73a9\u5bb6\u804a\u5929\u4ecd\u53ef\u7528";
                }
                return request.getText();
            }
        };
        try {
            try (TranslationCoordinator coordinator = new TranslationCoordinator(
                    provider, new TranslationCache(100), 2)) {
                coordinator.translate(
                        "Long system message one", "auto", "zh-CN", TextKind.SYSTEM_MESSAGE);
                coordinator.translate(
                        "Long system message two", "auto", "zh-CN", TextKind.SYSTEM_MESSAGE);
                assertTrue(systemsStarted.await(1, TimeUnit.SECONDS));

                TranslationResult chat = coordinator.translate(
                        "Player chat still works", "auto", "zh-CN", TextKind.CHAT)
                        .get(500, TimeUnit.MILLISECONDS);
                assertEquals("\u73a9\u5bb6\u804a\u5929\u4ecd\u53ef\u7528",
                        chat.getTranslatedText());
            }
        } finally {
            releaseSystems.countDown();
        }
    }

    private static void renderPlayerChatBypassesSystemFlood() throws Exception {
        CountDownLatch releaseSystems = new CountDownLatch(1);
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "render-player-chat-reserve-test";
            }

            @Override
            public String translate(TranslationRequest request) throws Exception {
                if (request.getKind() == TextKind.SYSTEM_MESSAGE) {
                    releaseSystems.await();
                    return "\u7cfb\u7edf\u6d88\u606f";
                }
                if (request.getKind() == TextKind.CHAT) {
                    return "\u73a9\u5bb6\u804a\u5929\u4ecd\u53ef\u7528";
                }
                return request.getText();
            }
        };
        try {
            try (RenderTranslationSession session = new RenderTranslationSession(
                    provider, "auto", "zh-CN", 200, 2)) {
                for (int index = 0; index < 64; index++) {
                    session.lookup(
                            "System flood message " + index, TextKind.SYSTEM_MESSAGE);
                }
                assertEquals("\u73a9\u5bb6\u804a\u5929\u4ecd\u53ef\u7528",
                        waitForLookup(session, "Player chat still works", TextKind.CHAT));
            }
        } finally {
            releaseSystems.countDown();
        }
    }

    private static void renderSystemMessagesBypassForegroundFlood() throws Exception {
        CountDownLatch foregroundStarted = new CountDownLatch(2);
        CountDownLatch releaseForeground = new CountDownLatch(1);
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "render-system-message-reserve-test";
            }

            @Override
            public String translate(TranslationRequest request) throws Exception {
                if (request.getKind() == TextKind.SIGN) {
                    foregroundStarted.countDown();
                    releaseForeground.await();
                    return request.getText();
                }
                if (request.getKind() == TextKind.SYSTEM_MESSAGE) {
                    return "\u6570\u636e\u5305\u83dc\u5355\u4ecd\u53ef\u7ffb\u8bd1";
                }
                return request.getText();
            }
        };
        try {
            try (RenderTranslationSession session = new RenderTranslationSession(
                    provider, "auto", "zh-CN", 200, 2)) {
                for (int index = 0; index < 16; index++) {
                    session.lookup("Blocking sign text " + index, TextKind.SIGN);
                }
                assertTrue(foregroundStarted.await(1, TimeUnit.SECONDS));
                Thread.sleep(1_100L);
                for (int index = 16; index < 32; index++) {
                    session.lookup("Blocking sign text " + index, TextKind.SIGN);
                }
                assertEquals("\u6570\u636e\u5305\u83dc\u5355\u4ecd\u53ef\u7ffb\u8bd1",
                        waitForLookup(
                                session,
                                "Datapack menu can still translate",
                                TextKind.SYSTEM_MESSAGE));
            }
        } finally {
            releaseForeground.countDown();
        }
    }

    private static void sharedQueueReservesForegroundCapacity() throws Exception {
        SharedQueueReserveProvider provider = new SharedQueueReserveProvider();
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(300), 1)) {
            coordinator.translate("Blocking world label", "auto", "zh-CN", TextKind.OTHER);
            assertTrue(provider.backgroundStarted.await(1, TimeUnit.SECONDS));
            for (int index = 0; index < 128; index++) {
                coordinator.translate(
                        "Queued world label " + index, "auto", "zh-CN", TextKind.OTHER);
            }
            java.util.concurrent.CompletableFuture<TranslationResult> chat = coordinator.translate(
                    "Chat still works", "auto", "zh-CN", TextKind.CHAT);
            provider.releaseBackground.countDown();
            assertEquals("聊天仍可用",
                    chat.get(1, TimeUnit.SECONDS).getTranslatedText());
        } finally {
            provider.releaseBackground.countDown();
        }
    }

    private static void titleLookupsNeverBlockRenderThread() throws Exception {
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "slow-title-test";
            }

            @Override
            public String translate(TranslationRequest request) throws Exception {
                Thread.sleep(600L);
                return "服务器正在重启";
            }
        };
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", 100, 1)) {
            long start = System.nanoTime();
            assertEquals("Server restarting", session.lookup("Server restarting", TextKind.TITLE));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsedMillis < 100L);
        }
    }

    private static void cacheHitsRenderImmediatelyAfterRestart() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-render-cache-");
        Path file = directory.resolve("cache.properties");
        CountingProvider firstProvider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                firstProvider, "auto", "zh-CN", new PersistentTranslationCache(file, 100), 1)) {
            session.lookup("Coins: 42", TextKind.TITLE);
            long deadline = System.currentTimeMillis() + 2_000L;
            String translated;
            do {
                Thread.sleep(10L);
                translated = session.lookup("Coins: 42", TextKind.TITLE);
            } while ("Coins: 42".equals(translated) && System.currentTimeMillis() < deadline);
            assertEquals("金币: 42", translated);
            assertEquals(1, firstProvider.calls.get());
        }

        CountingProvider secondProvider = new CountingProvider(false);
        try (RenderTranslationSession session = new RenderTranslationSession(
                secondProvider, "auto", "zh-CN", new PersistentTranslationCache(file, 100), 1)) {
            assertEquals("金币: 42", session.lookup("Coins: 42", TextKind.TITLE));
            assertEquals(0, secondProvider.calls.get());
        }
    }

    private static void dynamicHologramCacheHitsImmediatelyAfterRestart() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-hologram-cache-");
        Path file = directory.resolve("cache.properties");
        String source = "Your attack cooldown will not reset when you miss or switch weapons";
        String translated = "你的攻击冷却在你打空或切换武器时不会重置";
        RecordingProvider firstProvider = new RecordingProvider(translated);
        try (RenderTranslationSession session = new RenderTranslationSession(
                firstProvider, "auto", "zh-CN", new PersistentTranslationCache(file, 100), 1)) {
            session.setProtectedLiteralsSupplier(() ->
                    ProtectedLiteralsSnapshot.of(Arrays.asList("Steve_42")));
            assertEquals(translated, waitForCompleteLookup(session, source));
            assertEquals(1, firstProvider.calls.get());
        }

        RecordingProvider secondProvider = new RecordingProvider(translated);
        try (RenderTranslationSession session = new RenderTranslationSession(
                secondProvider, "auto", "zh-CN", new PersistentTranslationCache(file, 100), 1)) {
            session.setProtectedLiteralsSupplier(() ->
                    ProtectedLiteralsSnapshot.of(Arrays.asList("Steve_42")));
            assertEquals(translated, session.lookupComplete(source, TextKind.HOLOGRAM));
            assertEquals(0, secondProvider.calls.get());
        }
    }

    private static void dynamicProtectedNamesAvoidUnprotectedCache() throws Exception {
        TranslationCache cache = new TranslationCache(20);
        AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "dynamic-protected-cache-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                return request.getText().contains("__UT_")
                        ? "\u6b22\u8fce __UT_0__" : "\u6b22\u8fce \u53f2\u8482\u592b";
            }
        };
        String source = "Welcome Steve_42";
        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", cache, 1)) {
            assertEquals("\u6b22\u8fce \u53f2\u8482\u592b",
                    waitForLookup(session, source, TextKind.CHAT));
        }

        try (RenderTranslationSession session = new RenderTranslationSession(
                provider, "auto", "zh-CN", cache, 1)) {
            session.setProtectedLiteralsSupplier(() -> Arrays.asList("Steve_42"));
            assertEquals(source, session.lookup(source, TextKind.CHAT));
            assertEquals("\u6b22\u8fce Steve_42",
                    waitForLookup(session, source, TextKind.CHAT));
        }
        assertEquals(2, calls.get());
    }

    private static String waitForLookup(
            RenderTranslationSession session,
            String source,
            TextKind kind
    ) throws Exception {
        long deadline = System.currentTimeMillis() + 2_000L;
        String translated;
        do {
            Thread.sleep(10L);
            translated = session.lookup(source, kind);
        } while (source.equals(translated) && System.currentTimeMillis() < deadline);
        return translated;
    }

    private static void persistsOnlyHashedCacheKeys() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-test-");
        Path file = directory.resolve("cache.properties");
        PersistentTranslationCache first = new PersistentTranslationCache(file, 10);
        first.put("Server secret text", "\u670d\u52a1\u5668\u6587\u672c");
        String persisted = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(persisted.contains("Server secret text"));
        PersistentTranslationCache second = new PersistentTranslationCache(file, 10);
        assertEquals("\u670d\u52a1\u5668\u6587\u672c", second.get("Server secret text"));
    }

    private static void exportsAndReimportsCacheFile() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-export-");
        Path liveFile = directory.resolve("live-cache.properties");
        PersistentTranslationCache source = new PersistentTranslationCache(liveFile, 10);
        source.put("cache-key-one", "第一条译文");
        source.put("cache-key-two", "Second translated text");

        Path exportDirectory = directory.resolve("导出目录");
        Files.createDirectories(exportDirectory);
        Path exported = source.exportTo(exportDirectory);
        assertEquals(exportDirectory.resolve(TranslationCacheFile.FILE_NAME), exported);
        assertTrue(Files.isRegularFile(exported));
        assertTrue(Files.size(exported) > 0L);
        String content = new String(Files.readAllBytes(exported), StandardCharsets.UTF_8);
        assertTrue(content.contains("第一条译文"));
        assertTrue(content.contains("Second translated text"));

        Path importedFile = directory.resolve("imported-cache.properties");
        PersistentTranslationCache imported = new PersistentTranslationCache(importedFile, 10);
        assertEquals(2, imported.importFrom(exported));
        assertEquals("第一条译文", imported.get("cache-key-one"));
        assertEquals("Second translated text", imported.get("cache-key-two"));

        TranslationCache memory = new TranslationCache(10);
        assertEquals(2, memory.importFrom(exported));
        assertEquals("第一条译文", memory.get("cache-key-one"));
        assertEquals("Second translated text", memory.get("cache-key-two"));

        Path withoutExtension = directory.resolve("可读缓存");
        Path normalized = source.exportTo(withoutExtension);
        assertEquals(directory.resolve("可读缓存.properties"), normalized);
        assertTrue(Files.isRegularFile(normalized));
    }

    private static void protectsPlayerNames() {
        ProtectedText text = ProtectedText.parse(
                "Welcome Steve_42, balance 500", Arrays.asList("Steve_42"));
        assertEquals("Welcome __UT_0__, balance __UT_1__", text.getTemplate());
        assertEquals("\u6b22\u8fce Steve_42\uff0c\u4f59\u989d 500", text.restore("\u6b22\u8fce __UT_0__\uff0c\u4f59\u989d __UT_1__"));

        ProtectedText formatted = ProtectedText.parse(
                "\u00a7b[MVP+] Steve_42: Welcome", Arrays.asList("Steve_42"));
        assertEquals("__UT_0__[MVP+] __UT_1__: Welcome", formatted.getTemplate());
    }

    private static void protectsNetworkAddresses() {
        String original = "Join play.example.cn:25565, 203.0.113.7:25565, "
                + "[2001:db8::1]:25565 or localhost:5000";
        ProtectedText text = ProtectedText.parse(original);
        assertEquals("Join __UT_0__, __UT_1__, __UT_2__ or __UT_3__", text.getTemplate());
        assertEquals(original, text.restore("Join __UT_0__, __UT_1__, __UT_2__ or __UT_3__"));
    }

    private static void protectsInlineTextureCodes() throws Exception {
        String original = "[block/ladder]Climbables[block/ladder]";
        ProtectedText text = ProtectedText.parse(original);
        assertEquals("__UT_0__Climbables__UT_1__", text.getTemplate());
        assertEquals("[block/ladder]\u53ef\u6500\u722c\u65b9\u5757[block/ladder]",
                text.restore("__UT_0__\u53ef\u6500\u722c\u65b9\u5757__UT_1__"));

        RecordingProvider provider = new RecordingProvider("\u53ef\u6500\u722c\u65b9\u5757");
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(10), 1)) {
            TranslationResult result = coordinator.translate(
                    original, "auto", "zh-CN", TextKind.TOOLTIP)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("[block/ladder]\u53ef\u6500\u722c\u65b9\u5757[block/ladder]",
                    result.getTranslatedText());
            assertEquals("Climbables", provider.lastRequest.get());
        }

        String styled = "[huub/crosshair_attack_indicator_full@gui]"
                + "Melee attacks have +1 Hit Reach";
        ProtectedText styledText = ProtectedText.parse(styled);
        assertEquals("__UT_0__Melee attacks have +__UT_1__ Hit Reach",
                styledText.getTemplate());
        RecordingProvider styledProvider = new RecordingProvider(
                "\u8fd1\u6218\u653b\u51fb\u62e5\u6709 +__UT_0__ \u653b\u51fb\u8ddd\u79bb");
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                styledProvider, new TranslationCache(10), 1)) {
            TranslationResult result = coordinator.translate(
                    styled, "auto", "zh-CN", TextKind.CHAT).get(2, TimeUnit.SECONDS);
            assertEquals("[huub/crosshair_attack_indicator_full@gui]"
                    + "\u8fd1\u6218\u653b\u51fb\u62e5\u6709 +1 \u653b\u51fb\u8ddd\u79bb", result.getTranslatedText());
            assertEquals("Melee attacks have +__UT_0__ Hit Reach",
                    styledProvider.lastRequest.get());
            assertFalse(styledProvider.lastRequest.get().contains("huub/"));
        }
        assertThrows(() -> TranslationOutputValidator.requireDisplaySafe(
                styled, styled + "[huub/crosshair_attack_indicator_full@gui]"));
        assertEquals("[block/ladder]\u53ef\u6500\u722c\u65b9\u5757[block/ladder]",
                TranslationOutputValidator.requireDisplaySafe(
                        original, "[block/ladder][block/ladder]\u53ef\u6500\u722c\u65b9\u5757"));

        String sword = "[item/iron_sword@items]";
        String pickaxe = "[item/diamond_pickaxe@items]";
        String sourceRows = sword + "Attack\n" + pickaxe + "Cooldown";
        assertEquals(sword + "\u653b\u51fb\n" + pickaxe + "\u51b7\u5374",
                TranslationOutputValidator.requireDisplaySafe(
                        sourceRows, sword + pickaxe + "\u653b\u51fb\n\u51b7\u5374"));
        assertThrows(() -> TranslationOutputValidator.requireDisplaySafe(
                sourceRows, sword + pickaxe + "\u653b\u51fb\u51b7\u5374"));
        String restoredRows = sword + "\u653b\u51fb\n" + pickaxe + "\u51b7\u5374";
        assertEquals(restoredRows,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        sourceRows, restoredRows));
        assertThrows(() -> TranslationOutputValidator.requireRestoredDisplaySafe(
                sourceRows, sword + pickaxe + "\u653b\u51fb\n\u51b7\u5374"));

        String boundarySource = "\n " + pickaxe + "Total Spawners Mined: 8";
        String boundaryRestored = "\n" + pickaxe + "\u603b\u8ba1\u6316\u6398\u5237\u602a\u7b3c\uff1a8";
        assertEquals(boundaryRestored,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        boundarySource, boundaryRestored));
    }

    private static void translatesProtectedChatAsOneSentence() throws Exception {
        String original = "Welcome to 24-Hour Miracle: Apiary, a CTM map originally built in "
                + "24 hours on March 27, 2026!";
        RecordingProvider provider = new RecordingProvider(
                "\u6b22\u8fce\u6765\u5230__UT_0__\u5c0f\u65f6\u5947\u8ff9\uff1a\u517b\u8702\u573a\uff0c\u8fd9\u662f\u4e00\u5f20 CTM \u5730\u56fe\uff0c"
                        + "\u6700\u521d\u4e8e__UT_3__\u5e743\u6708__UT_2__\u65e5\u7528__UT_1__\u5c0f\u65f6\u5236\u4f5c\uff01");
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    original, "auto", "zh-CN", TextKind.CHAT).get(2, TimeUnit.SECONDS);
            assertEquals("\u6b22\u8fce\u6765\u523024\u5c0f\u65f6\u5947\u8ff9\uff1a\u517b\u8702\u573a\uff0c\u8fd9\u662f\u4e00\u5f20 CTM \u5730\u56fe\uff0c"
                    + "\u6700\u521d\u4e8e2026\u5e743\u670827\u65e5\u752824\u5c0f\u65f6\u5236\u4f5c\uff01", result.getTranslatedText());
            assertEquals("Welcome to __UT_0__-Hour Miracle: Apiary, a CTM map originally built in "
                    + "__UT_1__ hours on March __UT_2__, __UT_3__!", provider.lastRequest.get());
            assertEquals(1, provider.calls.get());
        }
    }

    private static void retriesStructuredChatWithAlternateTokens() throws Exception {
        String source = "{UT_STYLE_0_START}<Chengai77a6b>{UT_STYLE_0_END} "
                + "Your attack cooldown does not reset when you miss or switch weapons";
        String expected = "{UT_STYLE_0_START}<Chengai77a6b>{UT_STYLE_0_END} "
                + "你的攻击冷却在你打空或切换武器时不会重置";
        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "alternate-token-chat-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                requests.add(text);
                if (text.contains("__UT_")) {
                    return TranslationOutputValidator.requireValid(
                            text, "你的攻击冷却在你打空或切换武器时不会重置");
                }
                int sentence = text.indexOf("Your attack cooldown");
                assertTrue(sentence > 0);
                return TranslationOutputValidator.requireValid(
                        text,
                        text.substring(0, sentence)
                                + "你的攻击冷却在你打空或切换武器时不会重置",
                        request.getTargetLanguage());
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    source, "auto", "zh-CN", TextKind.CHAT,
                    Arrays.asList("Chengai77a6b"), true).get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals(expected, result.getTranslatedText());
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).contains("[[UTP_0]]"));
            assertFalse(requests.get(1).contains("__UT_"));
        }
    }

    private static void fallsBackForStyledChatAfterTokenFormatsFail() throws Exception {
        String sentence = "When alive, one should not fear; when dying, one should not be timid.";
        String source = "{UT_STYLE_0_START}<Chengai77a6b>{UT_STYLE_0_END} " + sentence;
        String translatedSentence = "活着时不应恐惧；临死时不应胆怯。";
        String expected = "{UT_STYLE_0_START}<Chengai77a6b>{UT_STYLE_0_END} "
                + translatedSentence;
        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "local-token-chat-fallback-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                requests.add(text);
                if (text.contains("__UT_") || text.contains("[[UTP_")) {
                    return translatedSentence;
                }
                assertEquals(sentence, text.trim());
                return translatedSentence;
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    source, "auto", "zh-CN", TextKind.CHAT,
                    Arrays.asList("Chengai77a6b"), true).get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals(expected, result.getTranslatedText());
            assertTrue(requests.contains(sentence));

            int calls = requests.size();
            TranslationResult cached = coordinator.cachedTranslation(
                    source, "auto", "zh-CN", TextKind.CHAT,
                    Arrays.asList("Chengai77a6b"), true);
            assertTrue(cached != null && cached.isTranslated());
            assertEquals(expected, cached.getTranslatedText());
            assertEquals(calls, requests.size());
        }
    }

    private static void acceptsTextMovedAcrossTextureStyleBoundaries() throws Exception {
        String texture = "[item/diamond_pickaxe@items]";
        String sentence = "Your charged attack remains ready";
        String translatedSentence = "\u84c4\u529b\u653b\u51fb\u4ecd\u5df2\u51c6\u5907\u5c31\u7eea";
        String source = "{UT_STYLE_0_START}" + texture
                + "{UT_STYLE_0_END}" + sentence;
        String expected = "{UT_STYLE_0_START}" + texture
                + translatedSentence + "{UT_STYLE_0_END}";
        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "texture-style-boundary-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                requests.add(text);
                if (text.contains("__UT_")) {
                    return "__UT_0__" + translatedSentence + "__UT_1__";
                }
                if (text.contains("[[UTP_")) {
                    return "[[UTP_0]]" + translatedSentence + "[[UTP_1]]";
                }
                assertEquals(sentence, text.trim());
                return translatedSentence;
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    source, "auto", "zh-CN", TextKind.CHAT)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals(expected, result.getTranslatedText());
            assertEquals(1, requests.size());

            int calls = requests.size();
            TranslationResult cached = coordinator.cachedTranslation(
                    source, "auto", "zh-CN", TextKind.CHAT,
                    java.util.Collections.<String>emptyList(), true);
            assertTrue(cached != null && cached.isTranslated());
            assertEquals(expected, cached.getTranslatedText());
            assertEquals(calls, requests.size());
        }
    }

    private static void preservesStyledTranslationRanges() {
        String source = "Click HERE or DOWNLOAD";
        String decorated = StyledTranslationTemplate.decorate(source, Arrays.asList(
                StyledTranslationTemplate.span(0, 6, 10),
                StyledTranslationTemplate.span(1, 14, 22)));
        ProtectedText protectedText = ProtectedText.parse(decorated);
        assertEquals(4, protectedText.getValues().size());
        assertFalse(protectedText.getTemplate().contains("UT_STYLE"));

        StyledTranslationTemplate.Parsed parsed = StyledTranslationTemplate.parse(
                "{UT_STYLE_1_START}\u4e0b\u8f7d{UT_STYLE_1_END}\uff0c\u6216\u70b9\u51fb"
                        + "{UT_STYLE_0_START}\u8fd9\u91cc{UT_STYLE_0_END}", 2);
        assertTrue(parsed != null);
        assertEquals("\u4e0b\u8f7d\uff0c\u6216\u70b9\u51fb\u8fd9\u91cc", parsed.text());
        assertEquals(1, parsed.spans().get(0).id());
        assertEquals("\u4e0b\u8f7d", parsed.text().substring(
                parsed.spans().get(0).start(), parsed.spans().get(0).end()));
        assertEquals(0, parsed.spans().get(1).id());
        assertEquals("\u8fd9\u91cc", parsed.text().substring(
                parsed.spans().get(1).start(), parsed.spans().get(1).end()));
        assertTrue(StyledTranslationTemplate.parse(
                "{UT_STYLE_0_START}A{UT_STYLE_1_START}B{UT_STYLE_0_END}C{UT_STYLE_1_END}", 2) == null);
        assertTrue(StyledTranslationTemplate.parse(
                "{UT_STYLE_0_START}A{UT_STYLE_0_END}", 2) == null);
        assertTrue(StyledTranslationTemplate.parse(
                "{UT_STYLE_0_START} {UT_STYLE_0_END}", 1) == null);

        String styledIcon = "{UT_STYLE_0_START}[item/diamond_spear@items]Attack{UT_STYLE_0_END}";
        assertEquals("{UT_STYLE_0_START}[item/diamond_spear@items]\u653b\u51fb{UT_STYLE_0_END}",
                TranslationOutputValidator.requireDisplaySafe(styledIcon,
                        "{UT_STYLE_0_START}\u653b\u51fb{UT_STYLE_0_END}[item/diamond_spear@items]"));
        String movedStyledText =
                "{UT_STYLE_0_START}[item/diamond_spear@items]{UT_STYLE_0_END}\u653b\u51fb";
        assertEquals(movedStyledText,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        styledIcon, movedStyledText));
        assertThrows(() -> TranslationOutputValidator.requireRestoredDisplaySafe(
                styledIcon,
                "{UT_STYLE_0_START}[item/diamond_spear@items]\u653b\u51fb"));

        String spriteOnly = "{UT_STYLE_0_START}[item/diamond_pickaxe@items]"
                + "{UT_STYLE_0_END}Your attack cooldown does not reset";
        String correctlyStyled = "{UT_STYLE_0_START}[item/diamond_pickaxe@items]"
                + "{UT_STYLE_0_END}\u4f60\u7684\u653b\u51fb\u51b7\u5374\u4e0d\u4f1a\u91cd\u7f6e";
        assertEquals(correctlyStyled,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        spriteOnly, correctlyStyled));
        String inheritedSpriteStyle =
                "{UT_STYLE_0_START}[item/diamond_pickaxe@items]"
                        + "\u4f60\u7684\u653b\u51fb\u51b7\u5374\u4e0d\u4f1a\u91cd\u7f6e"
                        + "{UT_STYLE_0_END}";
        assertEquals(inheritedSpriteStyle,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        spriteOnly, inheritedSpriteStyle));

        String movedTextureSource =
                "{UT_STYLE_0_START}[block/spawner]{UT_STYLE_0_END}"
                        + "{UT_STYLE_1_START}Spawner Milestones Unlocked:{UT_STYLE_1_END}";
        String movedTextureOutput =
                "{UT_STYLE_0_START}\u5237\u602a\u7b3c\u91cc\u7a0b\u7891{UT_STYLE_0_END}"
                        + "[block/spawner]"
                        + "{UT_STYLE_1_START}\u5df2\u89e3\u9501：{UT_STYLE_1_END}";
        assertEquals(movedTextureOutput,
                TranslationOutputValidator.requireRestoredDisplaySafe(
                        movedTextureSource, movedTextureOutput));
    }

    private static void translatesComplexStyledChatMenusLocally() throws Exception {
        StringBuilder source = new StringBuilder("Settings\n\nDifficulty: ");
        List<StyledTranslationTemplate.Span> spans =
                new ArrayList<StyledTranslationTemplate.Span>();
        appendStyledSpan(source, spans, "Easy");
        source.append(' ');
        appendStyledSpan(source, spans, "Normal");
        source.append(' ');
        appendStyledSpan(source, spans, "Hard");
        source.append("\n\nPlayer Glowing: ");
        appendStyledSpan(source, spans, "ON");
        source.append(' ');
        appendStyledSpan(source, spans, "OFF");
        source.append("\n\n");

        String[] labels = {"Sidebar", "Below Names", "Player List"};
        for (int row = 0; row < labels.length; row++) {
            for (int object = 0; object < 6; object++) {
                source.append("[ut_object/atlas_").append(row).append('_')
                        .append(object).append("@objects] ");
            }
            appendStyledSpan(source, spans, "X");
            source.append(" - ").append(labels[row]);
            if (row + 1 < labels.length) {
                source.append("\n\n");
            }
        }

        String template = StyledTranslationTemplate.decorate(source.toString(), spans);
        InlineTextureCode.TranslationPlan texturePlan =
                InlineTextureCode.prepareForTranslation(template);
        ProtectedText protectedText = ProtectedText.parse(texturePlan.request());
        assertFalse(StructuredTemplateTranslator.shouldUse(protectedText, TextKind.CHAT));
        assertTrue(StructuredTemplateTranslator.requiresLocalChatSegmentation(
                protectedText, TextKind.CHAT));

        CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "complex-styled-chat-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                String text = request.getText();
                requests.add(text);
                assertTrue(text.contains("__UT_"));
                assertFalse(InlineTextureCode.matcher(text).find());
                return text.replace("Settings", "\u8bbe\u7f6e")
                        .replace("Difficulty", "\u96be\u5ea6")
                        .replace("Easy", "\u7b80\u5355")
                        .replace("Normal", "\u666e\u901a")
                        .replace("Hard", "\u56f0\u96be")
                        .replace("Player Glowing", "\u73a9\u5bb6\u53d1\u5149")
                        .replace("ON", "\u5f00\u542f")
                        .replace("OFF", "\u5173\u95ed")
                        .replace("Sidebar", "\u4fa7\u8fb9\u680f")
                        .replace("Below Names", "\u540d\u79f0\u4e0b\u65b9")
                        .replace("Player List", "\u73a9\u5bb6\u5217\u8868");
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    template, "auto", "zh-CN", TextKind.CHAT).get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            String translated = result.getTranslatedText();
            assertFalse(translated.contains("__UT_"));
            assertTrue(translated.contains("\u8bbe\u7f6e"));
            assertTrue(translated.contains("\u73a9\u5bb6\u5217\u8868"));
            assertTrue(InlineTextureCode.hasSameSequence(template, translated));
            assertEquals(18, countInlineTextures(translated));
            assertEquals(VisualTextBoundaries.splitLines(template).size(),
                    VisualTextBoundaries.splitLines(translated).size());
            StyledTranslationTemplate.Parsed parsed =
                    StyledTranslationTemplate.parse(translated, spans.size());
            assertTrue(parsed != null);
            List<String> lines = VisualTextBoundaries.splitLines(translated);
            assertEquals(6, countInlineTextures(lines.get(6)));
            assertEquals(6, countInlineTextures(lines.get(8)));
            assertEquals(6, countInlineTextures(lines.get(10)));
            int calls = requests.size();
            assertTrue(calls > 0);
            assertTrue(calls <= 2);
            TranslationResult cached = coordinator.cachedTranslation(
                    template, "auto", "zh-CN", TextKind.CHAT,
                    java.util.Collections.<String>emptyList(), true);
            assertTrue(cached != null);
            assertEquals(translated, cached.getTranslatedText());
            assertEquals(calls, requests.size());
        }
    }

    private static void appendStyledSpan(
            StringBuilder output,
            List<StyledTranslationTemplate.Span> spans,
            String text
    ) {
        int start = output.length();
        output.append(text);
        spans.add(StyledTranslationTemplate.span(spans.size(), start, output.length()));
    }

    private static int countInlineTextures(String text) {
        int count = 0;
        java.util.regex.Matcher matcher = InlineTextureCode.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void skipsFullyProtectedText() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(10), 1)) {
            TranslationResult address = coordinator.translate(
                    "play.example.cn:25565", "auto", "zh-CN", TextKind.OTHER)
                    .get(2, TimeUnit.SECONDS);
            TranslationResult player = coordinator.translate(
                    "Steve_42", "auto", "zh-CN", TextKind.CHAT,
                    Arrays.asList("Steve_42")).get(2, TimeUnit.SECONDS);
            assertEquals("play.example.cn:25565", address.getTranslatedText());
            assertEquals("Steve_42", player.getTranslatedText());
            assertEquals(0, provider.calls.get());
        }
    }

    private static void neverSendsProtectedValuesToProvider() throws Exception {
        SegmentRecordingProvider provider = new SegmentRecordingProvider();
        String original = "Welcome Steve_42 at play.example.cn:25565 with 42 coins";
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult result = coordinator.translate(
                    original, "auto", "zh-CN", TextKind.CHAT,
                    Arrays.asList("Steve_42")).get(2, TimeUnit.SECONDS);
            assertTrue(result.getTranslatedText().contains("Steve_42"));
            assertTrue(result.getTranslatedText().contains("play.example.cn:25565"));
            assertTrue(result.getTranslatedText().contains("42"));
            String requests = provider.requests.toString();
            assertFalse(requests.contains("Steve_42"));
            assertFalse(requests.contains("play.example.cn"));
            assertFalse(requests.contains("42"));
            assertEquals("Welcome __UT_0__ at __UT_1__ with __UT_2__ coins\n", requests);
        }
    }

    private static void prefersGameGlossaryForAmbiguousTerms() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult bullet = coordinator.translate(
                    "bullet", "auto", "zh-CN", TextKind.ITEM_LORE)
                    .get(2, TimeUnit.SECONDS);
            TranslationResult titled = coordinator.translate(
                    "Bullet:", "auto", "zh-CN", TextKind.ITEM_LORE)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("\u5b50\u5f39", bullet.getTranslatedText());
            assertEquals("\u5b50\u5f39:", titled.getTranslatedText());
            assertEquals(0, provider.calls.get());
        }
    }

    private static void passesGameHintsAndContextToProvider() throws Exception {
        HintRecordingProvider provider = new HintRecordingProvider();
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult first = coordinator.translate(
                    "Magic wand", "auto", "zh-CN", TextKind.ITEM_LORE)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("\u9b54\u6756", first.getTranslatedText());
            assertTrue(provider.lastGlossary.get().contains("bullet=\u5b50\u5f39"));

            TranslationResult second = coordinator.translate(
                    "Open menu", "auto", "zh-CN", TextKind.ITEM_LORE)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("\u6253\u5f00\u83dc\u5355", second.getTranslatedText());
            assertTrue(provider.lastContext.get().contains("Magic wand=>\u9b54\u6756"));
        }
    }

    private static void prefersChinaDownloadSources() {
        assertEquals("modelscope.cn", LlamaCppOfflineProvider.DEFAULT_MODEL_CHINA_URI.getHost());
        assertEquals("huggingface.co", LlamaCppOfflineProvider.DEFAULT_MODEL_URI.getHost());
        OfflineEngineAsset engine = OfflineEngineAsset.current();
        assertEquals("gh-proxy.com", engine.downloadSources().get(0).getHost());
        assertEquals("github.com", engine.downloadSources().get(1).getHost());
    }

    private static void configuresWindowsOfflineRuntimePath() throws Exception {
        Path directory = Files.createTempDirectory("offline-process-path");
        Path server = Files.createDirectories(directory.resolve("engine"));
        Path javaBin = Files.createDirectories(directory.resolve("java-bin"));
        ProcessBuilder builder = new ProcessBuilder("offline-test");
        builder.environment().put("PATH", "existing-path");
        OfflineProcessSupport.prependWindowsLibraryPath(builder, server, javaBin);
        String expectedPrefix = server.toAbsolutePath().normalize().toString()
                + File.pathSeparator + javaBin.toAbsolutePath().normalize().toString()
                + File.pathSeparator;
        assertTrue(builder.environment().get("PATH").startsWith(expectedPrefix));
    }

    private static void usesRelativeOfflineModelPath() throws Exception {
        Path directory = Files.createTempDirectory("离线模型-").toAbsolutePath().normalize();
        Path model = directory.resolve("model.gguf");
        ProcessBuilder builder = new ProcessBuilder();
        assertEquals("model.gguf", OfflineProcessSupport.useRelativeModelPath(builder, model));
        assertEquals(directory.toFile(), builder.directory());
    }

    private static void reportsOfflineStartupDiagnostics() throws Exception {
        Path log = Files.createTempFile("offline-process", ".log");
        Files.write(log, "old output\n".getBytes(StandardCharsets.UTF_8));
        long offset = Files.size(log);
        Files.write(log, "missing model file\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
        assertEquals("missing model file", OfflineProcessSupport.readNewLogTail(log, offset));
        String missingDependency = OfflineProcessSupport.describeStartupExit(
                OfflineProcessSupport.WINDOWS_MISSING_DEPENDENCY_EXIT, "");
        assertTrue(missingDependency.contains("Visual C++"));
        assertTrue(missingDependency.contains("0xC0000135"));
        assertTrue(OfflineProcessSupport.describeStartupExit(2, "bad option")
                .contains("bad option"));
    }

    private static void protectsDynamicScoreboardValues() {
        ProtectedText text = ProtectedText.parse("\u00a7aCoins: 12,583 | https://example.org | 75%");
        assertEquals("__UT_0__Coins: __UT_1__ | __UT_2__ | __UT_3__", text.getTemplate());
        assertEquals("\u00a7a\u91d1\u5e01: 12,583 | https://example.org | 75%", text.restore("__UT_0__\u91d1\u5e01: __UT_1__ | __UT_2__ | __UT_3__"));
    }

    private static void preservesNumericQuantityAndPercentageSemantics() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> lastRequest = new AtomicReference<String>();
        final AtomicReference<String> lastNumericHint = new AtomicReference<String>();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "numeric-semantics-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                lastRequest.set(request.getText());
                lastNumericHint.set(request.getNumericHint());
                return "在这个世界里，你会找到 __UT_0__ 不同的试炼需要你完成，"
                        + "最后到达 __UT_1__ 这张地图！";
            }
        };
        String firstSource = "Dans ce monde, tu trouveras 6 épreuves différentes "
                + "que tu devras réussir, pour finir à 100% cette map !";
        String secondSource = "Dans ce monde, tu trouveras 8 épreuves différentes "
                + "que tu devras réussir, pour finir à 75% cette map !";
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(20), 1)) {
            TranslationResult first = coordinator.translate(
                    firstSource, "auto", "zh-CN", TextKind.CHAT)
                    .get(2, TimeUnit.SECONDS);
            TranslationResult second = coordinator.translate(
                    secondSource, "auto", "zh-CN", TextKind.CHAT)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("在这个世界里，你会找到 6个不同的试炼需要你完成，"
                    + "最后以100%完成这张地图！", first.getTranslatedText());
            assertEquals("在这个世界里，你会找到 8个不同的试炼需要你完成，"
                    + "最后以75%完成这张地图！", second.getTranslatedText());
            assertEquals(1, calls.get());
            assertEquals("Dans ce monde, tu trouveras __UT_0__ épreuves différentes "
                    + "que tu devras réussir, pour finir à __UT_1__ cette map !",
                    lastRequest.get());
            assertTrue(lastNumericHint.get().contains(
                    "__UT_0__=plain_number; role=count_or_measurement"));
            assertTrue(lastNumericHint.get().contains(
                    "__UT_1__=percentage; role=completion_percentage"));
            assertFalse(lastNumericHint.get().contains("100%"));
            assertFalse(lastNumericHint.get().contains("trouveras 6"));
            assertTrue(StructuredTemplateTranslator.shouldUse(
                    ProtectedText.parse("Find 6 players"), TextKind.ITEM_LORE));
            assertFalse(StructuredTemplateTranslator.shouldUse(
                    ProtectedText.parse("Coins: 100"), TextKind.SCOREBOARD_LINE));
        }
    }

    private static void repairsCachedStyledNumericGrammar() {
        String source = "{UT_STYLE_0_START}Dans ce monde, tu trouveras 6 épreuves "
                + "différentes, pour finir à 100% cette map !{UT_STYLE_0_END}";
        String cached = "{UT_STYLE_0_START}在这个世界里，你会找到 6 不同的试炼，"
                + "最后到达 100% 这张地图！{UT_STYLE_0_END}";
        assertEquals("{UT_STYLE_0_START}在这个世界里，你会找到 6个不同的试炼，"
                        + "最后以100%完成这张地图！{UT_STYLE_0_END}",
                LocalizedNumericGrammar.normalize(source, cached, "zh-CN"));
    }

    private static void avoidsFalseNumericClassifiers() {
        assertEquals("等级6难度", LocalizedNumericGrammar.normalize(
                "Level 6 difficulty", "等级6难度", "zh-CN"));
        assertEquals("持续6秒", LocalizedNumericGrammar.normalize(
                "Lasts 6 seconds", "持续6秒", "zh-CN"));
        assertEquals("伤害提高100%", LocalizedNumericGrammar.normalize(
                "Damage increased by 100%", "伤害提高100%", "zh-CN"));
        assertEquals("找到6名玩家", LocalizedNumericGrammar.normalize(
                "Find 6 players", "找到6 玩家", "zh-CN"));
        assertEquals("造成6点伤害", LocalizedNumericGrammar.normalize(
                "Deal 6 damage", "造成6 伤害", "zh-CN"));
    }

    private static void skipsAlreadyChineseAndNonTextValues() {
        assertFalse(LanguageHeuristics.shouldTranslate("\u91d1\u5e01\uff1a123", "zh-CN"));
        assertFalse(LanguageHeuristics.shouldTranslate("123 / 456", "zh-CN"));
        assertTrue(LanguageHeuristics.shouldTranslate("Coins: 123", "zh-CN"));
        assertTrue(LanguageHeuristics.shouldTranslate("欢迎 VIP", "zh-CN"));
    }

    private static void protectsExistingChineseInMixedText() throws Exception {
        ProtectedText protectedText = ProtectedText.parse(
                "Welcome 欢迎 VIP 服务器", java.util.Collections.<String>emptyList(), true);
        assertEquals("Welcome __UT_0__ VIP __UT_1__", protectedText.getTemplate());
        assertEquals("欢迎 欢迎 贵宾 服务器",
                protectedText.restore("欢迎 __UT_0__ 贵宾 __UT_1__"));

        RecordingProvider provider = new RecordingProvider();
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(10), 1)) {
            TranslationResult result = coordinator.translate(
                    "Welcome 欢迎", "auto", "zh-CN", TextKind.OTHER,
                    java.util.Collections.<String>emptyList(), true).get(2, TimeUnit.SECONDS);
            assertFalse(provider.lastRequest.get().contains("欢迎"));
            assertEquals("欢迎 欢迎", result.getTranslatedText());
        }
    }

    private static void stylesCompletedTranslations() {
        String styled = TranslationTextStyling.applyLegacyColor(
                "\u00a7aCoins \u00a7r42", TranslationTextColor.AQUA);
        assertEquals("\u00a7bCoins \u00a7r\u00a7b42\u00a7r", styled);
        assertEquals("Coins 42", TranslationTextStyling.stripLegacyFormatting(styled));
        assertEquals("Coins", TranslationTextStyling.applyLegacyColor(
                "Coins", TranslationTextColor.ORIGINAL));
        assertEquals("\u00a7d| \u00a7c金币 155", TranslationTextStyling.applyTranslatedStyle(
                "\u00a7d| \u00a7cCOINS 155", "\u00a7d| \u00a7c金币 155", TranslationTextColor.AQUA));
        assertEquals("\u00a7b金币 155\u00a7r", TranslationTextStyling.applyTranslatedStyle(
                "COINS 155", "金币 155", TranslationTextColor.AQUA));
        assertTrue(TranslationTextStyling.hasLegacyColor("\u00a7dINFORMATION"));
        assertFalse(TranslationTextStyling.hasLegacyColor("\u00a7lINFORMATION"));
    }

    private static void validatesSmallModelOutputs() {
        assertEquals("欢迎 __UT_0__", TranslationOutputValidator.requireValid(
                "Welcome __UT_0__", "\"欢迎 __UT_0__\""));
        assertEquals("欢迎 __UT_0__", TranslationOutputValidator.requireValid(
                "Welcome __UT_0__", "欢迎 __ut_00__"));
        assertEquals("欢迎 __UT_0__", TranslationOutputValidator.requireValid(
                "Welcome __UT_0__", "欢迎 [[utp_0]]"));
        assertEquals("欢迎 __UT_0__", TranslationOutputValidator.requireValid(
                "[[UTP_0]] Welcome", "欢迎 [[utp_0]]", "zh-CN"));
        assertEquals("[[UTP_0]] Welcome", TranslationOutputValidator.alternateProtectedTokens(
                "__UT_0__ Welcome"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Start", repeat("开始", 100)));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Welcome __UT_0__", "欢迎"));
        assertEquals("欢迎 __UT_1__ 和 __UT_0__", TranslationOutputValidator.requireValid(
                "Welcome __UT_0__ and __UT_1__", "欢迎 __UT_1__ 和 __UT_0__"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Welcome __UT_0__ and __UT_1__", "欢迎 __UT_0__ 和 __UT_0__"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Welcome", "Return only the translation. Welcome"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Welcome __UT_0__",
                "Minecraft server interface text from auto to zh-CN. Return only the translation. __UT_0__"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Welcome", "游戏服务器界面文本从自动翻译为中文，不要解释"));
        assertEquals("你是什么模型？请告诉我。", TranslationOutputValidator.requireValid(
                "What model are you? Please tell me.", "你是什么模型？请告诉我。"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Or download it by clicking", "Or download it by clicking", "zh-CN"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "Enable the resource pack", "Enable the redstone bullet.", "zh-CN"));
        assertEquals("或点击即可下载", TranslationOutputValidator.requireValid(
                "Or download it by clicking", "点击即可下载", "zh-CN"));
        assertEquals("或者点击下载", TranslationOutputValidator.requireValid(
                "Or download it by clicking", "或者点击下载", "zh-CN"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "What model are you? Please tell me.", "我是 Codex，基于 GPT-5 的模型。"));
        assertThrows(() -> TranslationOutputValidator.requireValid(
                "First of all, if you see", "First of all, if you see [[gun]]", "zh-CN"));
        assertThrows(() -> TranslationOutputValidator.requireDisplaySafe(
                "Welcome Steve", "欢迎 __UT_0__"));
        assertThrows(() -> TranslationOutputValidator.requireDisplaySafe(
                "Welcome Steve", "欢迎 [[utp_0]]"));
        assertThrows(() -> TranslationOutputValidator.requireDisplaySafe(
                "Welcome", "欢迎\n不要解释"));
        assertFalse(LanguageHeuristics.shouldTranslate(
                "Minecraft server interface text from auto to zh-CN", "zh-CN"));
        assertEquals("\u800c\u4e14.", GameTranslationHints.exactTranslation(
                "And.", "zh-CN"));
        assertEquals("\u8b66\u544a:", GameTranslationHints.exactTranslation(
                "WARNING:", "zh-CN"));
        assertEquals("AIaA\u8bbe\u65bd.", GameTranslationHints.localTranslation(
                "AIaA facility.", "zh-CN"));
        assertEquals("\u5df2\u83b7\u53d6 VHS", GameTranslationHints.localTranslation(
                "VHS Retrieved", "zh-CN"));
        assertEquals("\u5c0f\u5c4b\u65e0\u7ebf\u7535\u5929\u7ebf\u5df2\u5f00\u542f",
                GameTranslationHints.localTranslation(
                        "Cabin radio antenna ON", "zh-CN"));
        assertEquals(null, GameTranslationHints.localTranslation(
                "Magic chest", "zh-CN"));
    }

    private static void retriesRejectedProviderOutputs() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "validation-retry-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                calls.incrementAndGet();
                if ("Server notice".equals(request.getText())) {
                    return "\u670d\u52a1\u5668\u901a\u77e5";
                }
                return TranslationOutputValidator.requireValid(
                        request.getText(), request.getText(), request.getTargetLanguage());
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    "SERVER NOTICE", "auto", "zh-CN", TextKind.SUBTITLE)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals("\u670d\u52a1\u5668\u901a\u77e5", result.getTranslatedText());
            assertEquals(3, calls.get());
        }
    }

    private static void keepsValidationFallbackStatusHealthy() throws Exception {
        TranslationProvider primary = new TranslationProvider() {
            @Override
            public String id() {
                return "invalid-output-primary";
            }

            @Override
            public String translate(TranslationRequest request) {
                return TranslationOutputValidator.requireValid(
                        request.getText(), request.getText(), request.getTargetLanguage());
            }
        };
        TranslationProvider fallback = new TranslationProvider() {
            @Override
            public String id() {
                return "valid-output-fallback";
            }

            @Override
            public String translate(TranslationRequest request) {
                return "\u5df2\u4f7f\u7528\u56de\u9000\u8bd1\u6587";
            }
        };
        try (FallbackTranslationProvider provider =
                     new FallbackTranslationProvider(primary, fallback)) {
            assertEquals("\u5df2\u4f7f\u7528\u56de\u9000\u8bd1\u6587", provider.translate(
                    new TranslationRequest(
                            "Fallback text", "auto", "zh-CN", TextKind.CHAT)));
            assertEquals("\u4e3b\u7ffb\u8bd1\u670d\u52a1\u8fd0\u884c\u4e2d", provider.status());
        }
    }

    private static void retriesRejectedStructuredProviderOutputs() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TranslationProvider provider = new TranslationProvider() {
            @Override
            public String id() {
                return "structured-validation-retry-test";
            }

            @Override
            public String translate(TranslationRequest request) {
                if (calls.incrementAndGet() == 1) {
                    return TranslationOutputValidator.requireValid(
                            request.getText(), request.getText(), request.getTargetLanguage());
                }
                return request.getText().replace("Welcome", "\u6b22\u8fce");
            }
        };
        try (TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate(
                    "Welcome Steve", "auto", "zh-CN", TextKind.CHAT,
                    java.util.Collections.singletonList("Steve"), true)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isTranslated());
            assertFalse(result.isFailure());
            assertEquals("\u6b22\u8fce Steve", result.getTranslatedText());
            assertEquals(2, calls.get());
        }
    }

    private static void preservesRecentUserMessages() {
        RecentUserText recent = new RecentUserText();
        recent.remember("hello world");
        assertTrue(recent.shouldPreserve("hello world"));
        assertTrue(recent.shouldPreserve("<Player> hello world"));
        assertTrue(recent.shouldPreserve("Player: hello world"));
        assertTrue(recent.shouldPreserve("[MVP] Player » hello world"));
        assertTrue(recent.shouldPreserve("Player >> hello world"));
        assertFalse(recent.shouldPreserve("Server says hello"));
        recent.clear();
        assertFalse(recent.shouldPreserve("hello world"));
    }

    private static void classifiesPlayerChatMessages() {
        assertTrue(ChatMessageClassifier.looksLikePlayerChat(
                "<Chengai77a6b> Hello world"));
        assertTrue(ChatMessageClassifier.looksLikePlayerChat(
                "[MVP] <Player_42> Hello world"));
        assertFalse(ChatMessageClassifier.looksLikePlayerChat(
                "Difficulty: Easy Normal Hard"));
        assertFalse(ChatMessageClassifier.looksLikePlayerChat(
                "Welcome to the map"));
    }

    private static String repeat(String value, int count) {
        StringBuilder output = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            output.append(value);
        }
        return output.toString();
    }

    private static void cachesDynamicTemplates() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(provider, new TranslationCache(100), 2)) {
            TranslationResult first = coordinator.translate("Coins: 100", "auto", "zh-CN", TextKind.SCOREBOARD_LINE)
                    .get(2, TimeUnit.SECONDS);
            TranslationResult second = coordinator.translate("Coins: 200", "auto", "zh-CN", TextKind.SCOREBOARD_LINE)
                    .get(2, TimeUnit.SECONDS);
            assertEquals("\u91d1\u5e01: 100", first.getTranslatedText());
            assertEquals("\u91d1\u5e01: 200", second.getTranslatedText());
            assertEquals(1, provider.calls.get());
        }
    }

    private static void deduplicatesConcurrentRequests() throws Exception {
        CountingProvider provider = new CountingProvider(false);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(provider, new TranslationCache(100), 2)) {
            java.util.concurrent.CompletableFuture<TranslationResult> first =
                    coordinator.translate("Players online", "auto", "zh-CN", TextKind.PLAYER_LIST_HEADER);
            java.util.concurrent.CompletableFuture<TranslationResult> second =
                    coordinator.translate("Players online", "auto", "zh-CN", TextKind.PLAYER_LIST_HEADER);
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertEquals(1, provider.calls.get());
        }
    }

    private static void completesQueuedRequestsWhenClosed() throws Exception {
        BlockingProvider provider = new BlockingProvider();
        TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1);
        java.util.concurrent.CompletableFuture<TranslationResult> running =
                coordinator.translate("First queued translation", "auto", "zh-CN", TextKind.OTHER);
        java.util.concurrent.CompletableFuture<TranslationResult> queued =
                coordinator.translate("Second queued translation", "auto", "zh-CN", TextKind.OTHER);
        Thread.sleep(30L);
        coordinator.close();
        assertThrows(() -> running.get(1, TimeUnit.SECONDS));
        assertThrows(() -> queued.get(1, TimeUnit.SECONDS));
        provider.release.countDown();
    }

    private static void ignoresMalformedPersistentCache() throws Exception {
        Path directory = Files.createTempDirectory("universal-translator-malformed-cache-");
        Path file = directory.resolve("cache.properties");
        Files.write(file, "broken=\\u12".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PersistentTranslationCache cache = new PersistentTranslationCache(file, 10);
        assertEquals(0, cache.size());
        cache.put("fresh", "新值");
        assertEquals("新值", cache.get("fresh"));
    }

    private static void fallsBackToOriginalOnFailure() throws Exception {
        CountingProvider provider = new CountingProvider(true);
        try (TranslationCoordinator coordinator = new TranslationCoordinator(provider, new TranslationCache(100), 1)) {
            TranslationResult result = coordinator.translate("Server restarting", "auto", "zh-CN", TextKind.TITLE)
                    .get(2, TimeUnit.SECONDS);
            assertTrue(result.isFailure());
            assertEquals("Server restarting", result.getTranslatedText());
        }
    }

    private static final class UrgentBypassProvider implements TranslationProvider {
        private final CountDownLatch normalStarted = new CountDownLatch(2);
        private final CountDownLatch releaseNormal = new CountDownLatch(1);

        @Override
        public String id() {
            return "urgent-bypass-test";
        }

        @Override
        public String translate(TranslationRequest request) throws Exception {
            if (request.getKind() == TextKind.TITLE) {
                return "服务器正在重启";
            }
            normalStarted.countDown();
            releaseNormal.await(5L, TimeUnit.SECONDS);
            return request.getText();
        }
    }

    private static final class ForegroundBypassProvider implements TranslationProvider {
        private final CountDownLatch backgroundStarted = new CountDownLatch(2);
        private final CountDownLatch releaseBackground = new CountDownLatch(1);

        @Override
        public String id() {
            return "foreground-bypass-test";
        }

        @Override
        public String translate(TranslationRequest request) throws Exception {
            if (request.getKind() == TextKind.CHAT) {
                return "玩家已加入";
            }
            if (request.getKind() == TextKind.SYSTEM_MESSAGE) {
                return "目标已完成";
            }
            backgroundStarted.countDown();
            releaseBackground.await(5L, TimeUnit.SECONDS);
            return request.getText();
        }
    }

    private static final class SharedQueueReserveProvider implements TranslationProvider {
        private final CountDownLatch backgroundStarted = new CountDownLatch(1);
        private final CountDownLatch releaseBackground = new CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicBoolean firstBackground =
                new java.util.concurrent.atomic.AtomicBoolean(true);

        @Override
        public String id() {
            return "shared-queue-reserve-test";
        }

        @Override
        public String translate(TranslationRequest request) throws Exception {
            if (request.getKind() == TextKind.CHAT) {
                return "聊天仍可用";
            }
            if (firstBackground.compareAndSet(true, false)) {
                backgroundStarted.countDown();
                releaseBackground.await(5L, TimeUnit.SECONDS);
            }
            return request.getText();
        }
    }

    private static final class CountingProvider implements TranslationProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastRequest = new AtomicReference<String>();
        private final boolean fail;

        private CountingProvider(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String id() {
            return "test";
        }

        @Override
        public String translate(TranslationRequest request) throws Exception {
            calls.incrementAndGet();
            lastRequest.set(request.getText());
            if (fail) {
                throw new Exception("simulated outage");
            }
            Thread.sleep(30L);
            return request.getText()
                    .replace("Coins", "\u91d1\u5e01")
                    .replace("Players online", "\u5728\u7ebf\u73a9\u5bb6");
        }
    }

    private static final class BlockingProvider implements TranslationProvider {
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String id() {
            return "blocking-test";
        }

        @Override
        public String translate(TranslationRequest request) throws Exception {
            release.await(5L, TimeUnit.SECONDS);
            return request.getText();
        }
    }

    private static final class KindRecordingProvider implements TranslationProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<TextKind> lastKind = new AtomicReference<TextKind>();

        @Override
        public String id() {
            return "kind-recording-test";
        }

        @Override
        public String translate(TranslationRequest request) {
            calls.incrementAndGet();
            lastKind.set(request.getKind());
            return "译文";
        }
    }

    private static final class RecordingProvider implements TranslationProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastRequest = new AtomicReference<String>();
        private final String fixedResponse;

        private RecordingProvider() {
            this(null);
        }

        private RecordingProvider(String fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override
        public String id() {
            return "recording-test";
        }

        @Override
        public String translate(TranslationRequest request) {
            calls.incrementAndGet();
            lastRequest.set(request.getText());
            if (fixedResponse != null) {
                if (request.getKind() == TextKind.HOLOGRAM) {
                    String wrapped = wrapSingleHologramBlock(request.getText(), fixedResponse);
                    if (wrapped != null) {
                        return wrapped;
                    }
                }
                return fixedResponse;
            }
            return request.getText().replace("Welcome", "欢迎");
        }

        private static String wrapSingleHologramBlock(String request, String response) {
            if (request == null || !request.startsWith("__UT_")) {
                return null;
            }
            int firstEnd = request.indexOf("__", 5);
            int lastStart = request.lastIndexOf("__UT_");
            if (firstEnd < 0 || lastStart <= firstEnd
                    || request.indexOf("__UT_", firstEnd + 2) != lastStart) {
                return null;
            }
            int lastEnd = request.indexOf("__", lastStart + 5);
            if (lastEnd < 0 || lastEnd + 2 != request.length()) {
                return null;
            }
            return request.substring(0, firstEnd + 2) + response
                    + request.substring(lastStart, lastEnd + 2);
        }
    }

    private static final class HintRecordingProvider implements TranslationProvider {
        private final AtomicReference<String> lastGlossary = new AtomicReference<String>();
        private final AtomicReference<String> lastContext = new AtomicReference<String>();

        @Override
        public String id() {
            return "hint-recording-test";
        }

        @Override
        public String translate(TranslationRequest request) {
            lastGlossary.set(request.getGlossaryHint());
            lastContext.set(request.getContextHint());
            if ("Magic wand".equals(request.getText())) {
                return "\u9b54\u6756";
            }
            if ("Open menu".equals(request.getText())) {
                return "\u6253\u5f00\u83dc\u5355";
            }
            return request.getText();
        }
    }

    private static final class SegmentRecordingProvider implements TranslationProvider {
        private final StringBuilder requests = new StringBuilder();

        @Override
        public String id() {
            return "segment-recording-test";
        }

        @Override
        public synchronized String translate(TranslationRequest request) {
            requests.append(request.getText()).append('\n');
            return request.getText()
                    .replace("Welcome", "欢迎")
                    .replace("coins", "硬币");
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertThrows(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Expected an exception");
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

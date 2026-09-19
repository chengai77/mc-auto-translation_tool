package org.universaltranslator.core;

import org.universaltranslator.core.provider.LlamaCppOfflineProvider;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** 关闭钩子探测 */
public final class OfflineProviderShutdownProbe {
    private OfflineProviderShutdownProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected an installed offline root and optional model");
        }
        OfflineModel model = args.length == 2
                ? OfflineModel.fromConfig(args[1]) : OfflineModel.LITE;
        LlamaCppOfflineProvider provider = LlamaCppOfflineProvider.forModel(
                Paths.get(args[0]), false, model);
        TranslationCoordinator coordinator = new TranslationCoordinator(
                provider, new TranslationCache(100), 1);
        verifyTranslation(coordinator, "Open the chest", TextKind.TOOLTIP);
        verifyTranslation(coordinator, "Or download it by clicking", TextKind.HOLOGRAM);
        verifyTranslation(coordinator, "Oh.", TextKind.SUBTITLE);
        verifyTranslation(coordinator, "And.", TextKind.SUBTITLE);
        verifyTranslation(coordinator, "WARNING:", TextKind.SUBTITLE);
        verifyTranslation(coordinator, "AIaA facility.", TextKind.SUBTITLE);
        verifyTranslation(coordinator, "VHS Retrieved", TextKind.SUBTITLE);
        verifyTranslation(coordinator, "Cabin radio antenna ON", TextKind.SUBTITLE);
        String hub = verifyTranslation(
                coordinator, "Control Cloud Hub", TextKind.HOLOGRAM);
        if (TranslationOutputGuard.containsInstructionArtifact(hub)
                || hub.contains("翻译") || hub.contains("翻譯")) {
            throw new AssertionError("Offline model added translation commentary: " + hub);
        }
        String question = verifyTranslation(
                coordinator, "What model are you? Please tell me.", TextKind.CHAT);
        if (question.indexOf('?') < 0 && question.indexOf('\uff1f') < 0) {
            throw new AssertionError("Offline model answered a question instead of translating it");
        }
        // 依赖关闭钩子
    }

    private static String verifyTranslation(
            TranslationCoordinator coordinator, String source, TextKind kind) throws Exception {
        TranslationResult result = coordinator.translate(
                source, "en", "zh-CN", kind).get(2L, TimeUnit.MINUTES);
        if (result.isFailure()) {
            throw new AssertionError("Offline translation failed: " + result.getErrorMessage());
        }
        String translated = result.getTranslatedText();
        if (translated == null || translated.trim().isEmpty()
                || source.equals(translated.trim()) || !containsHan(translated)) {
            throw new AssertionError("Offline translation did not translate: " + source);
        }
        System.out.println(source + " -> " + translated);
        return translated;
    }

    private static boolean containsHan(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}

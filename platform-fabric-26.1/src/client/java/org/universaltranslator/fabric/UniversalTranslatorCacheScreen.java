package org.universaltranslator.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.universaltranslator.core.CacheFileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class UniversalTranslatorCacheScreen extends Screen {
    private final Screen parent;
    private String status = "";

    UniversalTranslatorCacheScreen(Screen parent) {
        super(Component.translatable("screen.universal_translator.cache.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(150, Math.min(240, width - 40));
        int left = (width - buttonWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.cache.import"), button -> chooseImport())
                .bounds(left, 55, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.cache.export"), button -> chooseExport())
                .bounds(left, 100, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.cache.clear"), button -> confirmClear())
                .bounds(left, 145, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_translator.back"), button -> onClose())
                .bounds(left, height - 28, buttonWidth, 20).build());
    }

    private void chooseImport() {
        chooseFile(false, file -> runAsync(
                () -> FabricTranslationRuntime.importCache(file.toPath()),
                count -> Component.translatable("screen.universal_translator.cache.import_success", count)));
    }

    private void chooseExport() {
        chooseFile(true, target -> runAsync(() -> {
            Path exported = FabricTranslationRuntime.exportCache(target.toPath());
            if (!Files.isRegularFile(exported) || Files.size(exported) == 0L) {
                throw new java.io.IOException("Cache file was not created");
            }
            return exported;
        }, exported -> Component.translatable("screen.universal_translator.cache.export_success",
                exported.getFileName().toString())));
    }

    private void confirmClear() {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (minecraft == null) return;
            if (confirmed) {
                minecraft.setScreen(this);
                runAsync(() -> { FabricTranslationRuntime.clearCacheFile(); return 0; },
                        ignored -> Component.translatable("screen.universal_translator.cache.clear_success"));
            } else minecraft.setScreen(this);
        }, Component.translatable("screen.universal_translator.cache.confirm_title"),
                Component.translatable("screen.universal_translator.cache.confirm_clear")));
    }

    private void chooseFile(boolean save, Consumer<File> callback) {
        CompletableFuture.supplyAsync(() -> chooseNativeFile(save)).whenComplete((selected, error) -> {
            if (error != null) return;
            if (selected != null && minecraft != null) {
                minecraft.execute(() -> callback.accept(selected));
            }
        });
    }

    private File chooseNativeFile(boolean save) {
        String title = Component.translatable(
                "screen.universal_translator.cache.choose_file").getString();
        return save ? CacheFileChooser.chooseExportFile(title)
                : CacheFileChooser.chooseImportFile(title);
    }


    private <T> void runAsync(CacheTask<T> task, StatusFactory<T> success) {
        status = Component.translatable("screen.universal_translator.cache.working").getString();
        CompletableFuture.supplyAsync(() -> {
            try { return task.run(); } catch (Exception exception) { throw new CacheOperationException(exception); }
        }).whenComplete((value, error) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> status = error == null ? success.create(value).getString()
                    : Component.translatable("screen.universal_translator.cache.failed", message(error)).getString());
        });
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, this.title, width / 2, 20, 0xFFFFFF);
        if (!status.isEmpty()) graphics.centeredText(this.font, Component.literal(status), width / 2, height - 48, 0xD0D0D0);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private interface CacheTask<T> { T run() throws Exception; }
    private interface StatusFactory<T> { Component create(T value); }
    private static final class CacheOperationException extends RuntimeException {
        CacheOperationException(Exception cause) { super(cause); }
    }
}

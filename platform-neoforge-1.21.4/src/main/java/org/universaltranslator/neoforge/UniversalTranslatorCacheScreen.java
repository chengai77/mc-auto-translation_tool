package org.universaltranslator.neoforge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.universaltranslator.core.CacheFileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class UniversalTranslatorCacheScreen extends Screen implements LocalTranslationScreen {
    private final Screen parent;
    private String status = "";

    UniversalTranslatorCacheScreen(Screen parent) {
        super(Text.translatable("screen.universal_translator.cache.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(150, Math.min(240, width - 40));
        int left = (width - buttonWidth) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.cache.import"), button -> chooseImport())
                .dimensions(left, 55, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.cache.export"), button -> chooseExport())
                .dimensions(left, 100, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.cache.clear"), button -> confirmClear())
                .dimensions(left, 145, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_translator.back"), button -> close())
                .dimensions(left, height - 28, buttonWidth, 20).build());
    }

    private void chooseImport() {
        chooseFile(false, file -> runAsync(
                () -> NeoForgeTranslationRuntime.importCache(file.toPath()),
                count -> Text.translatable("screen.universal_translator.cache.import_success", count)));
    }

    private void chooseExport() {
        chooseFile(true, target -> runAsync(() -> {
            Path exported = NeoForgeTranslationRuntime.exportCache(target.toPath());
            if (!Files.isRegularFile(exported) || Files.size(exported) == 0L) {
                throw new java.io.IOException("Cache file was not created");
            }
            return exported;
        }, exported -> Text.translatable("screen.universal_translator.cache.export_success",
                exported.getFileName().toString())));
    }

    private void confirmClear() {
        if (client != null) {
            client.setScreen(new ConfirmScreen(confirmed -> {
                if (client == null) return;
                if (confirmed) {
                    client.setScreen(this);
                    runAsync(() -> {
                        NeoForgeTranslationRuntime.clearCacheFile();
                        return 0;
                    }, ignored -> Text.translatable("screen.universal_translator.cache.clear_success"));
                } else {
                    client.setScreen(this);
                }
            }, Text.translatable("screen.universal_translator.cache.confirm_title"),
                    Text.translatable("screen.universal_translator.cache.confirm_clear")));
        }
    }

    private void chooseFile(boolean save, Consumer<File> callback) {
        CompletableFuture.supplyAsync(() -> chooseNativeFile(save)).whenComplete((selected, error) -> {
            if (error != null) {
                return;
            }
            if (selected != null && client != null) {
                client.execute(() -> callback.accept(selected));
            }
        });
    }

    private File chooseNativeFile(boolean save) {
        String title = Text.translatable(
                "screen.universal_translator.cache.choose_file").getString();
        return save ? CacheFileChooser.chooseExportFile(title)
                : CacheFileChooser.chooseImportFile(title);
    }

    private <T> void runAsync(CacheTask<T> task, StatusFactory<T> success) {
        status = Text.translatable("screen.universal_translator.cache.working").getString();
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.run();
            } catch (Exception exception) {
                throw new CacheOperationException(exception);
            }
        }).whenComplete((value, error) -> {
            if (client == null) return;
            client.execute(() -> status = error == null
                    ? success.create(value).getString()
                    : Text.translatable("screen.universal_translator.cache.failed", message(error)).getString());
        });
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TranslatorUiText.drawCentered(context, textRenderer, title,
                width / 2, 20, 0xFFFFFF, width - 20);
        if (!status.isEmpty()) {
            TranslatorUiText.drawCentered(context, textRenderer, Text.literal(status),
                    width / 2, height - 48, 0xD0D0D0, width - 20);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private interface CacheTask<T> {
        T run() throws Exception;
    }

    private interface StatusFactory<T> {
        Text create(T value);
    }

    private static final class CacheOperationException extends RuntimeException {
        CacheOperationException(Exception cause) { super(cause); }
    }
}


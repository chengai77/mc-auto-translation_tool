package org.universaltranslator.fabric;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
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
        super(new TranslatableText("screen.universal_translator.cache.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(150, Math.min(240, width - 40));
        int left = (width - buttonWidth) / 2;
        addButton(TranslatorUiText.button(left, 55, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.cache.import"), button -> chooseImport()));
        addButton(TranslatorUiText.button(left, 100, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.cache.export"), button -> chooseExport()));
        addButton(TranslatorUiText.button(left, 145, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.cache.clear"), button -> confirmClear()));
        addButton(TranslatorUiText.button(left, height - 28, buttonWidth, 20,
                new TranslatableText("screen.universal_translator.back"), button -> onClose()));
    }

    private void chooseImport() {
        chooseFile(false, file -> runAsync(
                () -> FabricTranslationRuntime.importCache(file.toPath()),
                count -> new TranslatableText("screen.universal_translator.cache.import_success", count)));
    }

    private void chooseExport() {
        chooseFile(true, target -> runAsync(() -> {
            Path exported = FabricTranslationRuntime.exportCache(target.toPath());
            if (!Files.isRegularFile(exported) || Files.size(exported) == 0L) {
                throw new java.io.IOException("Cache file was not created");
            }
            return exported;
        }, exported -> new TranslatableText("screen.universal_translator.cache.export_success",
                exported.getFileName().toString())));
    }

    private void confirmClear() {
        if (client != null) {
            client.openScreen(new ConfirmScreen(confirmed -> {
                if (client == null) return;
                if (confirmed) {
                    client.openScreen(this);
                    runAsync(() -> {
                        FabricTranslationRuntime.clearCacheFile();
                        return 0;
                    }, ignored -> new TranslatableText("screen.universal_translator.cache.clear_success"));
                } else {
                    client.openScreen(this);
                }
            }, new TranslatableText("screen.universal_translator.cache.confirm_title"),
                    new TranslatableText("screen.universal_translator.cache.confirm_clear")));
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
        String title = new TranslatableText(
                "screen.universal_translator.cache.choose_file").getString();
        return save ? CacheFileChooser.chooseExportFile(title)
                : CacheFileChooser.chooseImportFile(title);
    }

    private <T> void runAsync(CacheTask<T> task, StatusFactory<T> success) {
        status = new TranslatableText("screen.universal_translator.cache.working").getString();
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
                    : new TranslatableText("screen.universal_translator.cache.failed", message(error)).getString());
        });
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        TranslatorUiText.drawCentered(matrices, textRenderer, title,
                width / 2, 20, 0xFFFFFF, width - 20);
        if (!status.isEmpty()) {
            TranslatorUiText.drawCentered(matrices, textRenderer, new LiteralText(status),
                    width / 2, height - 48, 0xD0D0D0, width - 20);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (client != null) client.openScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
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

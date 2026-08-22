package org.universaltranslator.forge.legacy;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.universaltranslator.core.CacheFileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class LegacyCacheScreen extends GuiScreen {
    private static final int IMPORT = 1;
    private static final int EXPORT = 2;
    private static final int CLEAR = 3;
    private static final int BACK = 4;
    private final GuiScreen parent;
    private FontRenderer renderer;
    private String status = "";

    LegacyCacheScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        renderer = LegacyVersionAccess.fontRenderer();
        int buttonWidth = Math.max(150, Math.min(240, width - 40));
        int left = (width - buttonWidth) / 2;
        buttonList.add(new GuiButton(IMPORT, left, 55, buttonWidth, 20, tr("screen.universal_translator.cache.import")));
        buttonList.add(new GuiButton(EXPORT, left, 100, buttonWidth, 20, tr("screen.universal_translator.cache.export")));
        buttonList.add(new GuiButton(CLEAR, left, 145, buttonWidth, 20, tr("screen.universal_translator.cache.clear")));
        buttonList.add(new GuiButton(BACK, left, height - 28, buttonWidth, 20, tr("screen.universal_translator.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == IMPORT) {
            chooseFile(false, file -> runAsync(
                    () -> LegacyTranslationRuntime.importCache(file.toPath()),
                    count -> tr("screen.universal_translator.cache.import_success", count)));
        } else if (button.id == EXPORT) {
            chooseFile(true, target -> runAsync(() -> {
                Path exported = LegacyTranslationRuntime.exportCache(target.toPath());
                if (!Files.isRegularFile(exported) || Files.size(exported) == 0L) {
                    throw new IOException("Cache file was not created");
                }
                return exported;
            }, exported -> tr("screen.universal_translator.cache.export_success",
                    exported.getFileName().toString())));
        } else if (button.id == CLEAR) {
            mc.displayGuiScreen(new LegacyCacheConfirmScreen(this));
        } else if (button.id == BACK) {
            mc.displayGuiScreen(parent);
        }
    }

    private void chooseFile(boolean save, Consumer<File> callback) {
        CompletableFuture.supplyAsync(() -> chooseNativeFile(save)).whenComplete((selected, error) -> {
            if (error != null) {
                return;
            }
            if (selected != null && mc != null) {
                mc.addScheduledTask(() -> callback.accept(selected));
            }
        });
    }

    private File chooseNativeFile(boolean save) {
        String title = tr("screen.universal_translator.cache.choose_file");
        return save ? CacheFileChooser.chooseExportFile(title)
                : CacheFileChooser.chooseImportFile(title);
    }

    private <T> void runAsync(CacheTask<T> task, StatusFactory<T> success) {
        status = tr("screen.universal_translator.cache.working");
        CompletableFuture.supplyAsync(() -> {
            try { return task.run(); } catch (Exception exception) { throw new CacheOperationException(exception); }
        }).whenComplete((value, error) -> {
            mc.addScheduledTask(() -> status = error == null ? success.create(value)
                    : tr("screen.universal_translator.cache.failed", message(error)));
        });
    }

    void clearCache() {
        runAsync(() -> { LegacyTranslationRuntime.clearCacheFile(); return 0; },
                ignored -> tr("screen.universal_translator.cache.clear_success"));
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(renderer, tr("screen.universal_translator.cache.title"), width / 2, 20, 0xFFFFFF);
        if (!status.isEmpty()) drawCenteredString(renderer, status, width / 2, height - 48, 0xD0D0D0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static String tr(String key, Object... arguments) { return I18n.format(key, arguments); }
    private interface CacheTask<T> { T run() throws Exception; }
    private interface StatusFactory<T> { String create(T value); }
    private static final class CacheOperationException extends RuntimeException {
        CacheOperationException(Exception cause) { super(cause); }
    }

    private static final class LegacyCacheConfirmScreen extends GuiScreen {
        private final LegacyCacheScreen parent;
        private LegacyCacheConfirmScreen(LegacyCacheScreen parent) { this.parent = parent; }

        @Override
        public void initGui() {
            buttonList.clear();
            int buttonWidth = Math.max(90, Math.min(140, width / 2 - 12));
            int left = width / 2 - buttonWidth - 4;
            buttonList.add(new GuiButton(1, left, height / 2 + 12, buttonWidth, 20, tr("gui.yes")));
            buttonList.add(new GuiButton(2, width / 2 + 4, height / 2 + 12, buttonWidth, 20, tr("gui.no")));
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == 1) {
                mc.displayGuiScreen(parent);
                parent.clearCache();
            } else if (button.id == 2) {
                mc.displayGuiScreen(parent);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            FontRenderer font = LegacyVersionAccess.fontRenderer();
            drawCenteredString(font, tr("screen.universal_translator.cache.confirm_title"), width / 2, height / 2 - 32, 0xFFFFFF);
            drawCenteredString(font, tr("screen.universal_translator.cache.confirm_clear"), width / 2, height / 2 - 12, 0xFF5555);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }
}

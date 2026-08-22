package org.universaltranslator.core;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 缓存文件选择器 */
public final class CacheFileChooser {
    private CacheFileChooser() {
    }

    public static File chooseImportFile(String title) {
        return choose(false, title);
    }

    public static File chooseExportFile(String title) {
        return choose(true, title);
    }

    private static File choose(boolean save, String title) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            File selected = chooseWindows(save, title);
            if (selected != null) {
                return selected;
            }
        }
        return chooseSwing(save, title);
    }

    private static File chooseWindows(boolean save, String title) {
        String safeTitle = escapePowerShell(title);
        String dialog = save
                ? "$d=New-Object System.Windows.Forms.SaveFileDialog;"
                + "$d.Title='" + safeTitle + "';$d.FileName='" + TranslationCacheFile.FILE_NAME + "';"
                + "$d.DefaultExt='properties';$d.AddExtension=$true;$d.OverwritePrompt=$true;"
                + "$d.Filter='Properties files (*.properties)|*.properties|All files (*.*)|*.*';"
                + "if($d.ShowDialog() -eq 'OK'){[Console]::Write($d.FileName)}"
                : "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                + "$d.Title='" + safeTitle + "';$d.CheckFileExists=$true;$d.Multiselect=$false;"
                + "$d.Filter='Properties files (*.properties)|*.properties|All files (*.*)|*.*';"
                + "if($d.ShowDialog() -eq 'OK'){[Console]::Write($d.FileName)}";
        String script = "$utf8=New-Object System.Text.UTF8Encoding($false);"
                + "[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8;"
                + "Add-Type -AssemblyName System.Windows.Forms;" + dialog;
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                    "-Command", script).redirectErrorStream(true).start();
            String output = readUtf8(process.getInputStream()).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                return null;
            }
            File selected = new File(output);
            if (!save && !selected.isFile()) {
                return null;
            }
            return selected;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static File chooseSwing(final boolean save, final String title) {
        final File[] selected = new File[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle(title == null ? "" : title);
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    chooser.setFileFilter(new FileNameExtensionFilter(
                            "Properties files (*.properties)", "properties"));
                    if (save) {
                        chooser.setSelectedFile(new File(TranslationCacheFile.FILE_NAME));
                    }
                    int result = save ? chooser.showSaveDialog(null) : chooser.showOpenDialog(null);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        selected[0] = chooser.getSelectedFile();
                    }
                }
            });
        } catch (Exception ignored) {
            return null;
        }
        return selected[0];
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String escapePowerShell(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}

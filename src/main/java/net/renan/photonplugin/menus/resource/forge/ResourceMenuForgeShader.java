package net.renan.photonplugin.menus.resource.forge;

import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.dialogs.file.ExtensionFilter;
import net.mcreator.ui.dialogs.file.FileChooserType;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.Log;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommon;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public class ResourceMenuForgeShader {
    private static final String SHADER_FSH_EXTENSION = ".fsh";
    private static final String SHADER_JSON_EXTENSION = ".json";

    private ResourceMenuForgeShader() {}

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {

        ResourceMenuCommon.FXBrowserPanel browser = new ResourceMenuCommon.FXBrowserPanel(List.of(targetDirGetter), SHADER_FSH_EXTENSION,
                Set.of(SHADER_FSH_EXTENSION, SHADER_JSON_EXTENSION),
                file -> {
                    try {
                        if (file.getName().toLowerCase().endsWith(SHADER_JSON_EXTENSION)) {
                            try { return scaleIconTo(UIRES.get("64px_photon.json"), 64); }
                            catch (Exception ignored) {}
                        }
                        return scaleIconTo(UIRES.get("64px_photon.fsh"), 64);
                    } catch (Exception ignored) { return null; }
                }
        );

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildShaderImportButton(mcreator, browser, targetDirGetter));

        toolBar.add(buildCloneButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildDeleteButtonForBrowser(mcreator, browser));
        toolBar.add(buildExportButtonForBrowser(mcreator, browser));
        toolBar.add(buildRenameButtonForBrowser(mcreator, browser));
        toolBar.add(buildMoveButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildCreateFolderButtonForBrowser(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter);

        return panel;
    }

    private static File[] showShaderImportDialog(MCreator mcreator) {
        File[] selected = FileDialogs.getFileChooserDialog(mcreator, FileChooserType.OPEN, true, null,
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.shader.import.filter.fsh"), "*" + SHADER_FSH_EXTENSION),
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.shader.import.filter.json"), "*" + SHADER_JSON_EXTENSION),
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.shader.import.filter.both"),
                        "*" + SHADER_FSH_EXTENSION, "*" + SHADER_JSON_EXTENSION));
        return selected != null ? selected : new File[0];
    }

    private static JButton buildShaderImportButton(MCreator mcreator, ResourceMenuCommon.FXBrowserPanel browser,
                                                   Supplier<File> targetDirGetter) {
        JButton button = createToolbarButton(L10N.t("plugin.photon.resourcemenu.import"), "16px_photon.import");
        button.addActionListener(e -> {
            File[] selectedSources = showShaderImportDialog(mcreator);
            if (selectedSources.length == 0) return;

            File navigatedDir = browser.getCurrentDir();
            File baseDir = targetDirGetter.get();
            File targetDir = (navigatedDir != null && navigatedDir.isDirectory()) ? navigatedDir : baseDir;
            if (!targetDir.exists()) targetDir.mkdirs();

            boolean strictFsh = requiresStrictNaming(SHADER_FSH_EXTENSION);
            boolean strictJson = requiresStrictNaming(SHADER_JSON_EXTENSION);

            int conflictCount = (int) java.util.Arrays.stream(selectedSources)
                    .filter(src -> new File(targetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver overwriteResolver = new BulkConflictResolver(conflictCount, selectedSources.length >= 2);

            int invalidNameCount = (int) java.util.Arrays.stream(selectedSources)
                    .filter(src -> {
                        boolean strict = src.getName().toLowerCase().endsWith(SHADER_JSON_EXTENSION) ? strictJson : strictFsh;
                        return strict && !isValidStrictFileName(src.getName());
                    })
                    .count();
            InvalidNameResolver nameResolver = new InvalidNameResolver(invalidNameCount);

            List<File> importedFiles = new ArrayList<>();
            Exception[] failure = new Exception[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File source : selectedSources) {
                        boolean isJson = source.getName().toLowerCase().endsWith(SHADER_JSON_EXTENSION);
                        boolean strict = isJson ? strictJson : strictFsh;

                        String fileName = source.getName();
                        if (strict) {
                            fileName = nameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }

                        File dest = new File(targetDir, fileName);
                        ConflictResolution resolution = overwriteResolver.resolve(mcreator, dest);
                        if (resolution.action() == ACTION_CANCEL) break;
                        if (resolution.action() == ACTION_SKIP) continue;
                        dest = resolution.targetFile();

                        if (dest.exists()) dest.delete();
                        FileIO.copyFile(source, dest);

                        importedFiles.add(dest);
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                try {
                    if (failure[0] != null) {
                        Log.error("Failed to import shader files into '" + targetDir.getAbsolutePath() + "'", failure[0]);
                        JOptionPane.showMessageDialog(mcreator,
                                L10N.t("plugin.photon.resourcemenu.import.failed") + failure[0].getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    } else if (!importedFiles.isEmpty()) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, importedFiles.size() + " "
                                + L10N.t("plugin.photon.resourcemenu.action.imported")
                                + " \u2192 \"" + browser.getFolderName(targetDir) + "\""
                                + autoRenameNotice(overwriteResolver));
                    } else {
                        JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    }
                } finally {
                    browser.endInternalMutation();
                }
            });
        });
        return button;
    }
}
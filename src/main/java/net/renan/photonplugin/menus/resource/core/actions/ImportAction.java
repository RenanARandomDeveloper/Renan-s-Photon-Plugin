package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class ImportAction {

    private ImportAction() {
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension,
                "plugin.photon.resourcemenu.import", "16px_photon.import", null);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, BiConsumer<File, List<File>> onImported) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension,
                "plugin.photon.resourcemenu.import", "16px_photon.import", onImported);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, String menuKey, String iconKey) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension, menuKey, iconKey, null);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, String menuKey, String iconKey,
                                            BiConsumer<File, List<File>> onImported) {
        JButton button = MenuToolbars.createToolbarButton(L10N.t(menuKey), iconKey);
        button.addActionListener(e -> {
            File[] sourceFiles = FileDialogs.getMultiOpenDialog(mcreator, new String[]{extension});
            if (sourceFiles == null || sourceFiles.length == 0) return;

            File targetDir;
            try {
                File navigatedDir = browser.getCurrentDir();
                targetDir = (navigatedDir != null && navigatedDir.isDirectory()) ? navigatedDir : targetDirGetter.get();
                if (!targetDir.exists()) targetDir.mkdirs();
            } catch (Exception ex) {
                MenuLog.error("Failed to resolve import target directory", ex);
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            final File finalTargetDir = targetDir;
            boolean strict = FileNameRules.requiresStrictNaming(extension);
            int conflictCount = (int) Arrays.stream(sourceFiles)
                    .filter(src -> new File(finalTargetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver overwriteResolver = new BulkConflictResolver(conflictCount, sourceFiles.length >= 2);
            int invalidNameCount = strict
                    ? (int) Arrays.stream(sourceFiles)
                    .filter(src -> !FileNameRules.isValidStrictFileName(src.getName()))
                    .count()
                    : 0;
            InvalidNameResolver nameResolver = new InvalidNameResolver(invalidNameCount);
            List<File> importedFiles = new ArrayList<>();
            Exception[] failure = new Exception[1];

            browser.beginInternalMutation();
            EdtSupport.runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File source : sourceFiles) {
                        String fileName = source.getName();
                        if (strict) {
                            fileName = nameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }
                        File destFile = new File(finalTargetDir, fileName);
                        ConflictResolution resolution = overwriteResolver.resolve(mcreator, destFile);
                        if (resolution.action() == ConflictResolution.ACTION_CANCEL) break;
                        if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                        destFile = resolution.targetFile();
                        FileOps.copyReplacing(source, destFile);
                        importedFiles.add(destFile);
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                try {
                    if (!importedFiles.isEmpty()) {
                        if (onImported != null) onImported.accept(finalTargetDir, importedFiles);
                        browser.refresh();
                    }
                    if (failure[0] != null) {
                        MenuLog.error("Failed to import files into '" + finalTargetDir.getAbsolutePath() + "'", failure[0]);
                        JOptionPane.showMessageDialog(mcreator,
                                L10N.t("plugin.photon.resourcemenu.import.failed") + failure[0].getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    } else if (!importedFiles.isEmpty()) {
                        JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(importedFiles.size(), 0) + " " + L10N.t("plugin.photon.resourcemenu.action.imported")
                                + " \u2192 \"" + finalTargetDir.getName() + "\"" + BulkConflictResolver.autoRenameNotice(overwriteResolver));
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

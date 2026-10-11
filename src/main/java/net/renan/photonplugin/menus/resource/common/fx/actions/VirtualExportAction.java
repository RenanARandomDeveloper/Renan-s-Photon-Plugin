package net.renan.photonplugin.menus.resource.common.fx.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFXBrowserPanel;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.support.DirectoryDialogs;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver.checkOverwrite;
import static net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution.ACTION_CANCEL;
import static net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution.ACTION_SKIP;
import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.runFileTaskInBackground;
import static net.renan.photonplugin.menus.resource.core.support.MenuMessages.buildItemLabel;

public final class VirtualExportAction {

    private VirtualExportAction() {
    }

    public static JButton build(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.export.files"), "16px_photon.export");
        btn.addActionListener(e -> {
            List<File> fileTargets = browser.getSelectedFiles();
            List<File> folderTargets = VirtualBrowserSupport.selectedVirtualFolders(browser, store);
            if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File dest = DirectoryDialogs.chooseDirectory(mcreator);
            if (dest == null) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }

            int folderConflicts = 0;
            for (File folder : folderTargets) {
                if (new File(dest, browser.getFolderName(folder)).exists()) folderConflicts++;
            }
            int conflictCount = BulkConflictResolver.countConflicts(fileTargets, dest) + folderConflicts;
            BulkConflictResolver resolver = (conflictCount > 0) ? new BulkConflictResolver(conflictCount) : null;

            Map<File, LinkedHashMap<String, List<File>>> folderSubtrees = new LinkedHashMap<>();
            Map<File, String> folderNames = new LinkedHashMap<>();
            for (File folder : folderTargets) {
                folderSubtrees.put(folder, store.snapshotSubtree(folder));
                folderNames.put(folder, browser.getFolderName(folder));
            }

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            Exception[] failure = new Exception[1];

            runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File f : fileTargets) {
                        if (cancelAllHolder[0]) break;
                        File destFile = new File(dest, f.getName());
                        ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFile) : checkOverwrite(mcreator, destFile);
                        if (resolution.action() == ACTION_CANCEL) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        if (resolution.action() == ACTION_SKIP) continue;
                        destFile = resolution.targetFile();
                        FileOps.copyReplacing(f, destFile);
                        counts[0]++;
                    }

                    for (File folder : folderTargets) {
                        if (cancelAllHolder[0]) break;
                        String folderName = folderNames.get(folder);
                        File destFolder = new File(dest, folderName);
                        if (destFolder.exists()) {
                            ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFolder) : checkOverwrite(mcreator, destFolder);
                            if (resolution.action() == ACTION_CANCEL) {
                                cancelAllHolder[0] = true;
                                break;
                            }
                            if (resolution.action() == ACTION_SKIP) continue;
                            destFolder = resolution.targetFile();
                        }
                        File targetFolder = destFolder;
                        Map<String, List<File>> subtree = folderSubtrees.get(folder);
                        if (containsSources(targetFolder, subtree)) {
                            throw new IOException("Destination folder contains files being exported: " + folderName);
                        }
                        FileOps.replaceDirectory(targetFolder, () -> exportSubtree(targetFolder, subtree));
                        counts[1]++;
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                if (failure[0] != null) {
                    MenuLog.error("Failed to export selected FX resources", failure[0]);
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.export.failed") + failure[0].getMessage(),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int total = counts[0] + counts[1];
                if (total > 0) {
                    JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.exported") + BulkConflictResolver.autoRenameNotice(resolver));
                } else if (!cancelAllHolder[0]) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            });
        });
        return btn;
    }

    private static void exportSubtree(File destFolderRoot, Map<String, List<File>> subtree) throws IOException {
        for (Map.Entry<String, List<File>> branch : subtree.entrySet()) {
            String relative = branch.getKey();
            File targetDir = relative.isEmpty()
                    ? destFolderRoot
                    : new File(destFolderRoot, relative.replace(VirtualFolderStore.SEP, File.separator));
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("Cannot create directory: " + targetDir.getAbsolutePath());
            }
            for (File f : branch.getValue()) {
                FileOps.copyReplacing(f, new File(targetDir, f.getName()));
            }
        }
    }

    private static boolean containsSources(File destFolder, Map<String, List<File>> subtree) {
        for (List<File> files : subtree.values()) {
            for (File f : files) {
                if (FileOps.isInside(f, destFolder)) return true;
            }
        }
        return false;
    }
}
package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.support.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ExportAction {

    private ExportAction() {
    }

    public static JButton buildExportButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.export.files"), "16px_photon.export");
        btn.addActionListener(e -> {
            List<File> fileTargets = browser.getSelectedFiles();
            List<File> folderTargets = browser.getSelectedFolders().stream()
                    .filter(File::isDirectory)
                    .collect(java.util.stream.Collectors.toList());
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

            int conflictCount = BulkConflictResolver.countConflicts(fileTargets, dest)
                    + BulkConflictResolver.countConflicts(folderTargets, dest);
            BulkConflictResolver resolver = (conflictCount > 0) ? new BulkConflictResolver(conflictCount) : null;

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            Exception[] failure = new Exception[1];

            EdtSupport.runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File f : fileTargets) {
                        if (cancelAllHolder[0]) break;
                        File destFile = new File(dest, f.getName());
                        ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFile) : BulkConflictResolver.checkOverwrite(mcreator, destFile);
                        if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                        destFile = resolution.targetFile();
                        FileOps.copyReplacing(f, destFile);
                        counts[0]++;
                    }

                    for (File folder : folderTargets) {
                        if (cancelAllHolder[0]) break;
                        File destFolder = new File(dest, folder.getName());
                        if (FileOps.isInside(dest, folder)) {
                            throw new IOException("Destination is inside the exported folder: " + folder.getName());
                        }
                        if (destFolder.exists()) {
                            ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFolder) : BulkConflictResolver.checkOverwrite(mcreator, destFolder);
                            if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                                cancelAllHolder[0] = true;
                                break;
                            }
                            if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                            destFolder = resolution.targetFile();
                        }
                        if (FileOps.isSameFile(folder, destFolder)) {
                            counts[1]++;
                            continue;
                        }
                        if (FileOps.isInside(folder, destFolder)) {
                            throw new IOException("Destination contains the exported folder: " + folder.getName());
                        }
                        boolean recursive = true;
                        boolean hasSubDirs = false;
                        File[] children = folder.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                if (child.isDirectory()) {
                                    hasSubDirs = true;
                                    break;
                                }
                            }
                        }
                        if (hasSubDirs) {
                            int choice = EdtSupport.onEdtChoice(() -> JOptionPane.showConfirmDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.export.folder.has.subfolders", browser.getFolderName(folder)),
                                    L10N.t("plugin.photon.resourcemenu.operation.question"),
                                    JOptionPane.YES_NO_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE));
                            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                                cancelAllHolder[0] = true;
                                break;
                            }
                            recursive = (choice == JOptionPane.YES_OPTION);
                        }
                        File targetFolder = destFolder;
                        boolean fullCopy = recursive;
                        FileOps.replaceDirectory(targetFolder, () -> {
                            if (fullCopy) FileOps.copyFolderContents(folder, targetFolder);
                            else FileOps.copyFolderContentsShallow(folder, targetFolder);
                        });
                        counts[1]++;
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                if (failure[0] != null) {
                    MenuLog.error("Failed to export selected files/folders", failure[0]);
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.export.failed") + failure[0].getMessage(),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int total = counts[0] + counts[1];
                if (total > 0) {
                    JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.exported") + BulkConflictResolver.autoRenameNotice(resolver));
                } else if (!cancelAllHolder[0]) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            });
        });
        return btn;
    }
}
package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class MoveAction {

    private MoveAction() {
    }

    public static JButton buildMoveButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.move.files"), "16px_photon.move");
        btn.addActionListener(e -> {
            List<File> selFiles = browser.getSelectedFiles();
            List<File> selFolders = browser.getSelectedFolders();
            if (selFiles.isEmpty() && selFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            final List<File> itemsToMove;
            if (!selFiles.isEmpty() && !selFolders.isEmpty()) {
                String optFiles = L10N.t("plugin.photon.resourcemenu.move.type.files") + " (" + selFiles.size() + ")";
                String optFolders = L10N.t("plugin.photon.resourcemenu.move.type.folders") + " (" + selFolders.size() + ")";
                String optBoth = L10N.t("plugin.photon.resourcemenu.move.type.both");
                Object[] options = {optFiles, optFolders, optBoth};
                int typeChoice = JOptionPane.showOptionDialog(
                        mcreator, L10N.t("plugin.photon.resourcemenu.move.type.question"),
                        L10N.t("plugin.photon.resourcemenu.move.target.title"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, options, options[0]);
                if (typeChoice == JOptionPane.CLOSED_OPTION) return;
                if (typeChoice == 0) {
                    itemsToMove = new ArrayList<>(selFiles);
                } else if (typeChoice == 1) {
                    itemsToMove = new ArrayList<>(selFolders);
                } else {
                    itemsToMove = new ArrayList<>(selFiles);
                    itemsToMove.addAll(selFolders);
                }
            } else if (!selFiles.isEmpty()) {
                itemsToMove = new ArrayList<>(selFiles);
            } else {
                itemsToMove = new ArrayList<>(selFolders);
            }

            if (itemsToMove.isEmpty()) return;
            File baseDir = targetDirGetter.get();
            if (!baseDir.exists()) {
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.move.no.base.dir"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            Set<String> excludedPaths = new HashSet<>();
            for (File f : itemsToMove) excludedPaths.add(f.getAbsolutePath());
            File destDir = FolderPicker.showFolderPickerDialog(mcreator, baseDir, baseDir.getName(), excludedPaths);
            if (destDir == null) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }
            if (!destDir.exists()) destDir.mkdirs();

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            int conflictCount = BulkConflictResolver.countConflicts(itemsToMove, destDir);
            BulkConflictResolver resolver = new BulkConflictResolver(conflictCount);

            browser.beginInternalMutation();
            EdtSupport.runFileTaskInBackground(mcreator, () -> {
                for (File f : itemsToMove) {
                    if (cancelAllHolder[0]) break;
                    boolean isDir = f.isDirectory();
                    File dest = new File(destDir, f.getName());
                    if (FileOps.isSamePathExact(dest, f)) continue;
                    if (dest.exists() && !FileOps.isSamePathIgnoreCase(dest, f)) {
                        ConflictResolution resolution = resolver.resolve(mcreator, dest);
                        if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                        dest = resolution.targetFile();
                        if (dest.exists()) {
                            boolean removed = isDir ? FileOps.deleteRecursively(dest) : dest.delete();
                            if (!removed) {
                                MenuLog.warn("Could not remove conflicting destination before move: " + dest.getAbsolutePath());
                                continue;
                            }
                        }
                    }
                    if (FileOps.renameFile(f, dest)) {
                        if (isDir) counts[1]++;
                        else counts[0]++;
                    }
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                                + " \u2192 \"" + browser.getFolderName(destDir) + "\"" + BulkConflictResolver.autoRenameNotice(resolver));
                    } else if (!cancelAllHolder[0]) {
                        JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    }
                } finally {
                    browser.endInternalMutation();
                }
            });
        });
        return btn;
    }
}

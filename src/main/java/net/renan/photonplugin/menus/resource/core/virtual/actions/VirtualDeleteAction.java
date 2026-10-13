package net.renan.photonplugin.menus.resource.core.virtual.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.actions.DeleteAction;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserPanel;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualResourceProfile;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.onEdtVoid;
import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.runFileTaskInBackground;
import static net.renan.photonplugin.menus.resource.core.support.MenuMessages.*;

public final class VirtualDeleteAction {

    private VirtualDeleteAction() {
    }

    public static JButton build(MCreator mcreator, VirtualBrowserPanel browser, VirtualFolderStore store,
                                VirtualResourceProfile profile) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.delete.files"), "16px_photon.delete");
        btn.addActionListener(e -> performDeleteSelected(mcreator, browser, store, profile));
        DeleteAction.bindDeleteKey(mcreator, browser, () -> performDeleteSelected(mcreator, browser, store, profile));
        return btn;
    }

    private static void performDeleteSelected(MCreator mcreator, VirtualBrowserPanel browser, VirtualFolderStore store,
                                              VirtualResourceProfile profile) {
        List<File> fileTargets = browser.getSelectedFiles();
        List<File> folderTargets = VirtualBrowserSupport.selectedVirtualFolders(browser, store);
        if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean cancelAll = false;
        boolean deleteConfirmedFiles = false;

        if (!fileTargets.isEmpty()) {
            String msg = buildDeleteConfirmationMessage(browser, fileTargets, folderTargets);
            int choice = JOptionPane.showConfirmDialog(
                    mcreator, msg,
                    L10N.t("plugin.photon.resourcemenu.operation.warning"),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                cancelAll = true;
            } else if (choice == JOptionPane.YES_OPTION) {
                deleteConfirmedFiles = true;
            }
        }

        List<VirtualDeleteFolderDecision> decisions = new ArrayList<>();
        for (int fi = 0; fi < folderTargets.size() && !cancelAll; fi++) {
            File folder = folderTargets.get(fi);
            List<File> contents = store.getAllFilesInSubtree(folder);
            boolean hasSubfolders = store.hasDescendantFolders(folder);
            boolean isEmpty = contents.isEmpty() && !hasSubfolders;

            if (isEmpty) {
                int choice = JOptionPane.showConfirmDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.delete.folder.empty.confirm", browser.getFolderName(folder)),
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    cancelAll = true;
                } else if (choice == JOptionPane.YES_OPTION) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, false, contents));
                }
            } else {
                boolean hasSkip = folderTargets.size() > 1;
                String optMoveContents = L10N.t("plugin.photon.resourcemenu.delete.folder.option.move.contents");
                String optDeleteAll = L10N.t("plugin.photon.resourcemenu.delete.folder.option.delete.all");
                String optSkip = L10N.t("plugin.photon.resourcemenu.delete.folder.option.skip");
                String optCancelAll = L10N.t("plugin.photon.resourcemenu.delete.folder.option.cancel");

                Object[] options = hasSkip
                        ? new Object[]{optMoveContents, optDeleteAll, optSkip, optCancelAll}
                        : new Object[]{optMoveContents, optDeleteAll, optCancelAll};

                int cancelOptIdx = options.length - 1;
                String progressPrefix = hasSkip ? "(" + (fi + 1) + " / " + folderTargets.size() + ")  " : "";

                String folderMsg = buildFolderContentsPreviewMessage(progressPrefix, browser.getFolderName(folder), contents);

                int action = JOptionPane.showOptionDialog(
                        mcreator, folderMsg,
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[options.length - 1]);

                if (action == JOptionPane.CLOSED_OPTION || action == cancelOptIdx) {
                    cancelAll = true;
                } else if (action == 0) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, false, contents));
                } else if (action == 1) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, true, contents));
                }
            }
        }

        if (!deleteConfirmedFiles && decisions.isEmpty()) {
            if (!cancelAll) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            }
            return;
        }

        final boolean doDeleteFiles = deleteConfirmedFiles;
        final boolean wasCancelled = cancelAll;
        int[] counts = new int[2];

        browser.beginInternalMutation();
        runFileTaskInBackground(mcreator, () -> {
            if (doDeleteFiles) {
                List<File> actuallyDeleted = new ArrayList<>();
                for (File f : fileTargets) {
                    if (f.delete()) {
                        counts[0]++;
                        actuallyDeleted.add(f);
                    }
                }
                store.removeFiles(actuallyDeleted);
                profile.entriesRemoved(mcreator.getWorkspace().getWorkspaceFolder(), actuallyDeleted);
            }

            for (VirtualDeleteFolderDecision decision : decisions) {
                File folder = decision.folder();
                if (!decision.deleteAll()) {
                    store.deleteFolder(folder);
                    counts[1]++;
                    continue;
                }
                List<File> actuallyDeleted = new ArrayList<>();
                boolean allDeleted = true;
                for (File f : decision.contents()) {
                    if (f.delete()) {
                        actuallyDeleted.add(f);
                    } else {
                        allDeleted = false;
                    }
                }
                if (!actuallyDeleted.isEmpty()) {
                    store.removeFiles(actuallyDeleted);
                    profile.entriesRemoved(mcreator.getWorkspace().getWorkspaceFolder(), actuallyDeleted);
                }
                if (allDeleted) {
                    store.deleteFolderAndFiles(folder);
                    counts[1]++;
                } else {
                    onEdtVoid(() -> JOptionPane.showMessageDialog(
                            mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.delete.error") + "\n" + browser.getFolderName(folder),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                }
            }
        }, () -> {
            try {
                if (counts[0] + counts[1] > 0) {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.deleted"));
                } else if (!wasCancelled) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
    }

    private record VirtualDeleteFolderDecision(File folder, boolean deleteAll, List<File> contents) {
    }
}

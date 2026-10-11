package net.renan.photonplugin.menus.resource.common.fx.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFXBrowserPanel;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;
import java.util.List;

import static net.renan.photonplugin.menus.resource.core.actions.RenameAction.RenameOutcome;
import static net.renan.photonplugin.menus.resource.core.actions.RenameAction.promptAndRenameFile;
import static net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver.autoRenameNotice;
import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.*;
import static net.renan.photonplugin.menus.resource.core.support.MenuMessages.buildItemLabel;

public final class VirtualRenameAction {

    private VirtualRenameAction() {
    }

    public static JButton build(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.rename.file"), "16px_photon.rename");
        btn.addActionListener(e -> {
            List<File> fileItems = browser.getSelectedFiles();
            List<File> folderItems = VirtualBrowserSupport.selectedVirtualFolders(browser, store);

            if (fileItems.isEmpty() && folderItems.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int total = fileItems.size() + folderItems.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);
            int[] counts = new int[2];
            int[] indexHolder = new int[1];
            boolean[] cancelAllHolder = new boolean[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                for (File item : fileItems) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    RenameOutcome outcome = promptAndRenameFile(mcreator, item, progress, nameResolver, resolver);
                    if (outcome.isCancelled()) {
                        cancelAllHolder[0] = true;
                        break;
                    }
                    if (outcome.isRenamed()) {
                        store.renameFile(item, outcome.dest());
                        FXListManager.renameEntry(mcreator.getWorkspace().getWorkspaceFolder(), item, outcome.dest());
                        counts[0]++;
                    }
                }

                for (File folder : folderItems) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String fullPath = store.virtualDirName(folder);
                    String parent = store.parentOf(fullPath);
                    String currentName = store.lastSegmentOf(fullPath);
                    String newName = promptRenameVirtualFolderName(mcreator, store, parent, currentName, progress, nameResolver);
                    if (newName == null) {
                        cancelAllHolder[0] = true;
                        break;
                    }
                    if (newName.equals(currentName)) continue;
                    if (store.renameFolder(fullPath, newName)) counts[1]++;
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " "
                                + L10N.t("plugin.photon.resourcemenu.action.renamed") + autoRenameNotice(resolver));
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

    static String promptRenameVirtualFolderName(MCreator mcreator, VirtualFolderStore store, String parent, String currentName,
                                                String progress, InvalidNameResolver nameResolver) {
        String prefill = currentName;
        while (true) {
            String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message") + "\n"
                    + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + currentName;
            String currentPrefill = prefill;
            String newName = onEdt(() -> JOptionPane.showInputDialog(mcreator, hint, currentPrefill));
            if (newName == null) return null;
            newName = newName.trim();
            if (newName.equals(currentName)) return newName;
            if (newName.isEmpty()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }

            newName = nameResolver.resolve(mcreator, newName, progress, false);
            if (newName == null) continue;
            if (newName.equals(currentName)) return newName;
            String prospectivePath = store.childPath(parent, newName);
            if (store.folderExists(prospectivePath)) {
                String finalNewName = newName;
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                prefill = newName;
                continue;
            }
            return newName;
        }
    }
}

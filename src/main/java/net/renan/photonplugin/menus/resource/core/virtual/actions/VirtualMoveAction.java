package net.renan.photonplugin.menus.resource.core.virtual.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserPanel;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualFolderStore;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.FolderPicker.FolderPickerNode;
import static net.renan.photonplugin.menus.resource.core.actions.FolderPicker.showFolderPickerDialog;
import static net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver.autoRenameNotice;
import static net.renan.photonplugin.menus.resource.core.support.MenuMessages.buildItemLabel;

public final class VirtualMoveAction {

    private VirtualMoveAction() {
    }

    public static JButton build(MCreator mcreator, VirtualBrowserPanel browser, VirtualFolderStore store,
                                Supplier<File> rootDirGetter) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.move.files"), "16px_photon.move");
        btn.addActionListener(e -> {
            List<File> selFiles = browser.getSelectedFiles();
            List<File> selFolders = VirtualBrowserSupport.selectedVirtualFolders(browser, store);
            if (selFiles.isEmpty() && selFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File currentDir = browser.getCurrentDir();
            Set<File> excludeDirs = new HashSet<>(selFolders);
            File rootDir = rootDirGetter.get();
            DefaultMutableTreeNode rootNode = buildVirtualFolderTree(rootDir, rootDir.getName(), store, excludeDirs);

            File destDir = showFolderPickerDialog(mcreator, rootNode, rootDirGetter.get());
            if (destDir == null) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }

            BulkConflictResolver resolver = new BulkConflictResolver(selFiles.size());
            int movedFiles;
            int movedFolders = 0;
            browser.beginInternalMutation();
            try {
                movedFiles = selFiles.isEmpty() ? 0 : browser.performMove(selFiles, destDir, resolver);

                for (File folder : selFolders) {
                    String destParent = store.isVirtualDir(destDir) ? store.virtualDirName(destDir) : "";
                    String originalName = store.lastSegmentOf(store.virtualDirName(folder));
                    String finalName = store.moveFolderIntoReturningName(store.virtualDirName(folder), destParent);
                    if (finalName != null) {
                        movedFolders++;
                        if (!finalName.equals(originalName)) resolver.noteAutoRename(originalName, finalName);
                    }
                }
                if (movedFiles + movedFolders > 0) browser.refresh();
            } finally {
                browser.endInternalMutation();
            }

            if (movedFiles + movedFolders > 0) {
                JOptionPane.showMessageDialog(mcreator, buildItemLabel(movedFiles, movedFolders) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                        + " \u2192 \"" + browser.getFolderName(destDir) + "\"" + autoRenameNotice(resolver));
            } else {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            }
        });
        return btn;
    }

    private static DefaultMutableTreeNode buildVirtualFolderTree(File rootDir, String rootLabel,
                                                                 VirtualFolderStore store, Set<File> excludeVirtualDirs) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FolderPickerNode(rootDir, rootLabel));
        addVirtualFolderTreeChildren(rootNode, null, store, excludeVirtualDirs);
        return rootNode;
    }

    private static void addVirtualFolderTreeChildren(DefaultMutableTreeNode parentNode, File parentVirtualDir,
                                                     VirtualFolderStore store, Set<File> excludeVirtualDirs) {
        for (File vDir : store.listVirtualDirs(parentVirtualDir)) {
            if (excludeVirtualDirs.contains(vDir)) continue;
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    new FolderPickerNode(vDir, store.lastSegmentOf(store.virtualDirName(vDir))));
            parentNode.add(node);
            addVirtualFolderTreeChildren(node, vDir, store, excludeVirtualDirs);
        }
    }
}

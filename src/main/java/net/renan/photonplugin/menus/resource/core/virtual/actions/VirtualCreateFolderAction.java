package net.renan.photonplugin.menus.resource.core.virtual.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualFolderStore;

import javax.swing.*;
import java.io.File;

public final class VirtualCreateFolderAction {

    private VirtualCreateFolderAction() {
    }

    public static JButton build(MCreator mcreator, FXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.create.folder"), "16px_photon.create_folder");
        btn.addActionListener(e -> {
            File currentDir = browser.getCurrentDir();
            String parentPath = store.isVirtualDir(currentDir) ? store.virtualDirName(currentDir) : "";
            InvalidNameResolver nameResolver = new InvalidNameResolver(1);
            while (true) {
                String name = JOptionPane.showInputDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.folder.name.message"),
                        L10N.t("plugin.photon.resourcemenu.folder.create.title"),
                        JOptionPane.QUESTION_MESSAGE);
                if (name == null) return;
                name = name.trim();
                if (name.isEmpty()) return;

                name = nameResolver.resolve(mcreator, name, false);
                if (name == null) continue;
                name = VirtualFolderStore.sanitizeSegmentName(name);
                if (name.isEmpty()) continue;
                String fullPath = store.childPath(parentPath, name);
                if (store.folderExists(fullPath)) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                store.createFolder(fullPath);
                browser.refresh();
                return;
            }
        });
        return btn;
    }
}

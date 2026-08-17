package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;

public final class CreateFolderAction {

    private CreateFolderAction() {
    }

    public static JButton buildCreateFolderButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.create.folder"), "16px_photon.create_folder");
        btn.addActionListener(e -> {
            browser.beginInternalMutation();
            try {
                File targetDir = browser.getCurrentDir();
                if (targetDir == null) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.select.root.first"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.create.error"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String folderName = JOptionPane.showInputDialog(mcreator, L10N.t("plugin.photon.resourcemenu.folder.name"));
                if (folderName == null || folderName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    return;
                }
                folderName = folderName.trim();

                folderName = new InvalidNameResolver(1).resolve(mcreator, folderName, false);
                if (folderName == null) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    return;
                }
                File newFolder = new File(targetDir, folderName);
                if (newFolder.exists()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.WARNING_MESSAGE);
                } else if (!newFolder.mkdirs()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.create.error"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, "\"" + folderName + "\" " + L10N.t("plugin.photon.resourcemenu.action.created"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
        return btn;
    }
}

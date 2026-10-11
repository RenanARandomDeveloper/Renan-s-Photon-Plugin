package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.MainMenuBar;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.log.Log;

import javax.swing.*;

public final class MenuBar {
    private static final String MENU_NAME = "PhotonPluginMenu";

    private MenuBar() {
    }

    public static void install(MCreator mcreator, String componentLabel, String componentVersion, String libLabel,
                               String libVersion) {
        JMenuBar menuBar = mcreator.getMainMenuBar();
        if (menuBar == null) {
            Log.warn("Could not find MCreator's menu bar, Photon Menu was not added.");
            return;
        }

        uninstall(mcreator);

        JMenu photonMenu = buildMenu(mcreator, componentLabel, componentVersion, libLabel, libVersion);

        int insertIndex = resolveInsertIndex(menuBar);

        menuBar.add(photonMenu, insertIndex);
        refresh(menuBar);

        Log.info("Photon Menu added to the workspace toolbar.");
    }

    public static void uninstall(MCreator mcreator) {
        JMenuBar menuBar = mcreator.getMainMenuBar();
        if (menuBar == null) {
            return;
        }

        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && MENU_NAME.equals(menu.getName())) {
                menuBar.remove(menu);
                refresh(menuBar);
                Log.info("Photon Menu removed from the workspace toolbar.");
                break;
            }
        }
    }

    private static int resolveInsertIndex(JMenuBar menuBar) {
        int menuCount = menuBar.getMenuCount();
        return menuCount > 0 ? menuCount - 1 : menuCount;
    }

    private static void refresh(JMenuBar menuBar) {
        if (menuBar instanceof MainMenuBar mainMenuBar) {
            mainMenuBar.refreshMenuBar();
        } else {
            menuBar.revalidate();
            menuBar.repaint();
        }
    }

    private static JMenu buildMenu(MCreator mcreator, String componentLabel, String componentVersion,
                                   String libLabel, String libVersion) {
        JMenu photonMenu = new JMenu(L10N.t("plugin.photon.workspacemenu.title"));
        photonMenu.setName(MENU_NAME);
        photonMenu.setIcon(loadIcon("32px_photon.photonplugin"));

        JMenuItem logItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.log"));
        logItem.setToolTipText(L10N.t("plugin.photon.workspacemenu.log.tooltip"));
        logItem.setIcon(loadIcon("32px_photon.log"));
        logItem.addActionListener(e -> LogViewerDialog.show(mcreator));

        JMenuItem backupItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.backup.title"));
        backupItem.setToolTipText(L10N.t("plugin.photon.workspacemenu.backup.tooltip"));
        backupItem.setIcon(loadIcon("32px_photon.backup"));
        backupItem.addActionListener(e -> BackupMenuDialog.show(mcreator));

        JMenuItem aboutItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.about"));
        aboutItem.setToolTipText(L10N.t("plugin.photon.workspacemenu.about.tooltip"));
        aboutItem.setIcon(loadIcon("32px_photon.about"));
        aboutItem.addActionListener(
                e -> AboutDialog.show(mcreator, componentLabel, componentVersion, libLabel, libVersion));

        photonMenu.add(logItem);
        photonMenu.add(backupItem);
        photonMenu.addSeparator();
        photonMenu.add(aboutItem);

        return photonMenu;
    }

    private static Icon loadIcon(String resourceKey) {
        try {
            return IconUtils.scaleTo(resourceKey, UIRES.get(resourceKey), 32);
        } catch (Exception ex) {
            Log.warn("Could not load Photon Menu icon '" + resourceKey + "': " + ex.getMessage());
            return null;
        }
    }
}
package net.renan.photonplugin.menus.resource.neo;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.common.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ResourceMenusNeo {
    private static final String TARGET_DIR_FX = "run/ldlib2/assets/photon/fx";
    private static final String TARGET_DIR_FXPACK = "run/photon/fxpacks";
    private static final String TARGET_DIR_FXPROJ = "run/ldlib2/assets/fx_projects";
    private static final String TARGET_DIR_TEXTURE = "run/ldlib2/assets/ldlib2/textures";
    private static final String TARGET_DIR_MODEL = "run/ldlib2/assets/ldlib2/models";
    private static final String TARGET_DIR_RESOURCE = "run/ldlib2/assets/ldlib2/resources/";
    private static JPanel photonPanel;

    public static void setupMenu(MCreator mc) {
        photonPanel = ResourceMenuCommon.setupResourceTab(mc, photonPanel, L10N.t("plugin.photon.resourcemenu.tab.photon"),
                ResourceMenusNeo::createCombinedPanel);
    }

    public static void removeMenu(MCreator mc) {
        ResourceMenuCommon.removeResourceTab(mc, photonPanel);
        photonPanel = null;
    }

    private static JPanel createCombinedPanel(MCreator mc) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fx"),
                ResourceMenuCommonFX.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FX)));

        tabs.add(L10N.t("plugin.photon.resourcemenu.tab.fxpack"),
                ResourceMenuNeoFXPack.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPACK)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fxproj"),
                ResourceMenuCommonFXProject.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPROJ)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.model"),
                ResourceMenuNeoModel.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.textures"),
                ResourceMenuCommonTexture.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_TEXTURE)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.resource"),
                ResourceMenuCommonResource.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_RESOURCE)));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(tabs, BorderLayout.CENTER);
        return wrapper;
    }
}
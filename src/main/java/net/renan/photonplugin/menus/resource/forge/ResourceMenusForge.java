package net.renan.photonplugin.menus.resource.forge;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommon;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommonFX;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommonFXProject;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommonResource;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommonTexture;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ResourceMenusForge {
    private static final String TARGET_DIR_FX = "run/ldlib/assets/photon/fx";
    private static final String TARGET_DIR_FXPROJ = "run/ldlib/projects/fxproj";
    private static final String TARGET_DIR_TEXTURE = "run/ldlib/assets/ldlib/textures";
    private static final String TARGET_DIR_RESOURCE = "run/ldlib/assets/resources";
    private static final String TARGET_DIR_MODEL_OBJ = "run/ldlib/assets/ldlib/models/obj";
    private static final String TARGET_DIR_MODEL_JSON = "run/ldlib/assets/ldlib/models/block";
    private static final String TARGET_DIR_SHADER = "run/ldlib/assets/ldlib/shaders/core";
    private static JPanel photonPanel;

    public static void setupMenu(MCreator mc) {
        photonPanel = ResourceMenuCommon.setupResourceTab(mc, photonPanel, L10N.t("plugin.photon.resourcemenu.tab.photon"),
                ResourceMenusForge::createCombinedPanel);
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

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fxproj"),
                ResourceMenuCommonFXProject.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPROJ)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.mesh.model"),
                ResourceMenuForgeModel.createPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL_OBJ),
                        () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL_JSON)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.textures"),
                ResourceMenuCommonTexture.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_TEXTURE)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.shader"),
                ResourceMenuForgeShader.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_SHADER)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.resource"),
                ResourceMenuCommonResource.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_RESOURCE), true));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(tabs, BorderLayout.CENTER);
        return wrapper;
    }
}
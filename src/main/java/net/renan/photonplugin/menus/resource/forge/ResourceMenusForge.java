package net.renan.photonplugin.menus.resource.forge;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.common.ResourceMenuFXProject;
import net.renan.photonplugin.menus.resource.common.ResourceMenuResource;
import net.renan.photonplugin.menus.resource.common.ResourceMenuShader;
import net.renan.photonplugin.menus.resource.common.ResourceMenuTexture;
import net.renan.photonplugin.menus.resource.common.fx.ResourceMenuFX;
import net.renan.photonplugin.menus.resource.core.tabs.ResourceTabManager;
import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

public class ResourceMenusForge {
    private static final String TARGET_DIR_FX = "run/ldlib/assets/photon/fx";
    private static final String TARGET_DIR_FXPROJ = "run/ldlib/projects/fxproj";
    private static final String TARGET_DIR_TEXTURE = "run/ldlib/assets/ldlib/textures";
    private static final String TARGET_DIR_RESOURCE = "run/ldlib/assets/resources";
    private static final String TARGET_DIR_MODEL_OBJ = "run/ldlib/assets/ldlib/models/obj";
    private static final String TARGET_DIR_MODEL_JSON = "run/ldlib/assets/ldlib/models/block";
    private static final String TARGET_DIR_SHADER = "run/ldlib/assets/ldlib/shaders/core";
    private static final Map<MCreator, JPanel> PANELS = new WeakHashMap<>();

    public static void setupMenu(MCreator mc) {
        JPanel existing;
        synchronized (PANELS) {
            existing = PANELS.get(mc);
        }
        JPanel updated = ResourceTabManager.setupResourceTab(mc, existing,
                L10N.t("plugin.photon.resourcemenu.tab.photon"), ResourceMenusForge::createCombinedPanel);
        synchronized (PANELS) {
            PANELS.put(mc, updated);
        }
    }

    public static void removeMenu(MCreator mc) {
        JPanel existing;
        synchronized (PANELS) {
            existing = PANELS.remove(mc);
        }
        ResourceTabManager.removeResourceTab(mc, existing);
    }

    private static JPanel createCombinedPanel(MCreator mc) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fx"),
                ResourceMenuFX.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FX), true,
                        PreviewMode.FORGE));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fxproj"),
                ResourceMenuFXProject.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPROJ), true,
                        PreviewMode.FORGE));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.mesh.model"),
                ResourceMenuForgeModel.createPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL_OBJ),
                        () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL_JSON)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.textures"),
                ResourceMenuTexture.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_TEXTURE)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.shader"),
                ResourceMenuShader.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_SHADER)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.resource"),
                ResourceMenuResource.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_RESOURCE), true));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(tabs, BorderLayout.CENTER);
        return wrapper;
    }
}
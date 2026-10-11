package net.renan.photonplugin.menus.resource.neo;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.common.ResourceMenuFXProject;
import net.renan.photonplugin.menus.resource.common.ResourceMenuResource;
import net.renan.photonplugin.menus.resource.common.ResourceMenuTexture;
import net.renan.photonplugin.menus.resource.common.fx.ResourceMenuFX;
import net.renan.photonplugin.menus.resource.core.tabs.ResourceTabManager;
import net.renan.photonplugin.menus.resource.neo.fxpacks.ResourceMenuNeoFXPack;
import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public class ResourceMenusNeo {
    private static final String TARGET_DIR_FX = "run/ldlib2/assets/photon/fx";
    private static final String TARGET_DIR_FXPACK = "run/photon/fxpacks";
    private static final String TARGET_DIR_FXPROJ = "run/ldlib2/assets/fx_projects";
    private static final String TARGET_DIR_TEXTURE = "run/ldlib2/assets/ldlib2/textures";
    private static final String TARGET_DIR_MODEL = "run/ldlib2/assets/ldlib2/models";
    private static final String TARGET_DIR_RESOURCE = "run/ldlib2/assets/ldlib2/resources/";
    private static final Map<MCreator, WeakReference<JPanel>> PANELS = new WeakHashMap<>();

    public static void setupMenu(MCreator mc) {
        JPanel existing;
        synchronized (PANELS) {
            WeakReference<JPanel> reference = PANELS.get(mc);
            existing = reference != null ? reference.get() : null;
        }
        JPanel updated = ResourceTabManager.setupResourceTab(mc, existing,
                L10N.t("plugin.photon.resourcemenu.tab.photon"), ResourceMenusNeo::createCombinedPanel);
        synchronized (PANELS) {
            PANELS.put(mc, new WeakReference<>(updated));
        }
    }

    public static void removeMenu(MCreator mc) {
        JPanel existing;
        synchronized (PANELS) {
            WeakReference<JPanel> reference = PANELS.remove(mc);
            existing = reference != null ? reference.get() : null;
        }
        ResourceTabManager.removeResourceTab(mc, existing);
    }

    private static JPanel createCombinedPanel(MCreator mc) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fx"),
                ResourceMenuFX.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FX), true,
                        PreviewMode.NEOFORGE));

        tabs.add(L10N.t("plugin.photon.resourcemenu.tab.fxpack"),
                ResourceMenuNeoFXPack.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPACK), true));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.fxproj"),
                ResourceMenuFXProject.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_FXPROJ), true,
                        PreviewMode.NEOFORGE));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.model"),
                ResourceMenuNeoModel.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_MODEL)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.textures"),
                ResourceMenuTexture.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_TEXTURE)));

        tabs.addTab(L10N.t("plugin.photon.resourcemenu.tab.resource"),
                ResourceMenuResource.createFXPanel(mc, () -> new File(mc.getWorkspace().getWorkspaceFolder(), TARGET_DIR_RESOURCE)));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(tabs, BorderLayout.CENTER);
        return wrapper;
    }
}
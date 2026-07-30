package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.PluginMetadata;

public final class WorkspaceMenuForge {
    private static final String YAML_FLAVOR_KEY = "forge-1.20.1";
    private static final String PHOTON_LABEL = "Photon API";
    private static final String LDLIB_LABEL = "LDLib API";

    private WorkspaceMenuForge() {
    }

    public static void setupMenu(MCreator mcreator) {
        PluginMetadata.ApiVersions apiVersions = PluginMetadata.getApiVersions(YAML_FLAVOR_KEY);
        WorkspaceMenuCommon.setupMenu(mcreator, PHOTON_LABEL, apiVersions.photonVersion(),
                LDLIB_LABEL, apiVersions.ldLibVersion());
    }

    public static void removeMenu(MCreator mcreator) {
        WorkspaceMenuCommon.removeMenu(mcreator);
    }
}

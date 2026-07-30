package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.PluginMetadata;

public final class WorkspaceMenuNeo {
    private static final String YAML_FLAVOR_KEY = "neoforge-1.21.1";
    private static final String PHOTON_LABEL = "Photon2 API";
    private static final String LDLIB_LABEL = "LDLib2 API";

    private WorkspaceMenuNeo() {
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

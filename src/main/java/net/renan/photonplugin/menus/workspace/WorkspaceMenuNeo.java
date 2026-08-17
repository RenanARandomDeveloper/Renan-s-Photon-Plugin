package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.PluginMetadata;
import net.renan.photonplugin.menus.workspace.core.MenuBar;

public final class WorkspaceMenuNeo {
    private static final String YAML_FLAVOR_KEY_1_21_1 = "neoforge-1.21.1";
    private static final String YAML_FLAVOR_KEY_26_1_2 = "neoforge-26.1.2";
    private static final String PHOTON_LABEL = "Photon2 API";
    private static final String LDLIB_LABEL = "LDLib2 API";

    private WorkspaceMenuNeo() {
    }

    public static void setupMenu(MCreator mcreator) {
        String yamlFlavorKey = resolveYamlFlavorKey(mcreator);
        PluginMetadata.ApiVersions apiVersions = PluginMetadata.getApiVersions(yamlFlavorKey);
        MenuBar.install(mcreator, PHOTON_LABEL, apiVersions.photonVersion(),
                LDLIB_LABEL, apiVersions.ldLibVersion());
    }

    public static void removeMenu(MCreator mcreator) {
        MenuBar.uninstall(mcreator);
    }

    private static String resolveYamlFlavorKey(MCreator mcreator) {
        String generatorName = mcreator.getGenerator().getGeneratorName();

        if (generatorName != null && generatorName.contains("26.1.2")) {
            return YAML_FLAVOR_KEY_26_1_2;
        }

        return YAML_FLAVOR_KEY_1_21_1;
    }
}
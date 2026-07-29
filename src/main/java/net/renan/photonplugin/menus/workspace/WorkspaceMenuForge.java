package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;

public final class WorkspaceMenuForge {

    private static final String PHOTON_LABEL = "Photon API";
    private static final String PHOTON_VERSION = "1.1.17";
    private static final String LDLIB_LABEL = "LDLib API";
    private static final String LDLIB_VERSION = "1.0.52";

    private WorkspaceMenuForge() {
    }

    public static void setupMenu(MCreator mcreator) {
        WorkspaceMenuCommon.setupMenu(mcreator, PHOTON_LABEL, PHOTON_VERSION, LDLIB_LABEL, LDLIB_VERSION);
    }

    public static void removeMenu(MCreator mcreator) {
        WorkspaceMenuCommon.removeMenu(mcreator);
    }
}
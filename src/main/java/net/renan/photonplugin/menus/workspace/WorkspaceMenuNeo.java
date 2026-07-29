package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;

public final class WorkspaceMenuNeo {

    private static final String PHOTON_LABEL = "Photon2 API";
    private static final String PHOTON_VERSION = "2.2.1";
    private static final String LDLIB_LABEL = "LDLib2 API";
    private static final String LDLIB_VERSION = "2.2.30";

    private WorkspaceMenuNeo() {
    }

    public static void setupMenu(MCreator mcreator) {
        WorkspaceMenuCommon.setupMenu(mcreator, PHOTON_LABEL, PHOTON_VERSION, LDLIB_LABEL, LDLIB_VERSION);
    }

    public static void removeMenu(MCreator mcreator) {
        WorkspaceMenuCommon.removeMenu(mcreator);
    }
}
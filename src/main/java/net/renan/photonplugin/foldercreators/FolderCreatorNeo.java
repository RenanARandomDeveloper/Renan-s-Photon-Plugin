package net.renan.photonplugin.foldercreators;

import java.io.File;

public class FolderCreatorNeo extends FolderCreatorCommon {

    private static final String[] STRUCTURE = {
            "assets/photon/fx",
            "assets/ldlib2/models/",
            "assets/ldlib2/textures",
            "assets/ldlib2/shaders/core",
            "assets/ldlib2/resources/global",
            "assets/fx_projects"
    };

    public static void createStructure(File workspaceRoot) {
        new FolderCreatorNeo().buildStructure(workspaceRoot);
    }

    @Override
    protected String getBaseRelativePath() {
        return "run/ldlib2";
    }

    @Override
    protected String[] getFolderStructure() {
        return STRUCTURE;
    }
}
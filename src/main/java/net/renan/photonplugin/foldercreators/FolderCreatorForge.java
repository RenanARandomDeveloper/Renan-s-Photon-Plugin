package net.renan.photonplugin.foldercreators;

import java.io.File;

public class FolderCreatorForge extends FolderCreatorCommon {

    private static final String[] STRUCTURE = {
            "assets/photon/fx",
            "assets/ldlib/textures",
            "assets/resources/colors",
            "assets/resources/curves",
            "assets/resources/gradients",
            "assets/resources/material",
            "assets/resources/mesh",
            "projects/fxproj",
            "assets/ldlib/shaders/core",
            "assets/ldlib/models/block",
            "assets/ldlib/models/obj",
    };

    public static void createStructure(File workspaceRoot) {
        new FolderCreatorForge().buildStructure(workspaceRoot);
    }

    @Override
    protected String getBaseRelativePath() {
        return "run/ldlib";
    }

    @Override
    protected String[] getFolderStructure() {
        return STRUCTURE;
    }
}
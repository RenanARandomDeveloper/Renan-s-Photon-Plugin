package net.renan.photonplugin.copiers;

import java.io.File;
import java.nio.file.Path;

public class CopyFilesToAssetsFolderForge extends CopyFilesToAssetsFolderCommon {
    private static final String PROJECTS_STAGING_FOLDER = "projects";
    private static final CopyFilesToAssetsFolderForge INSTANCE = new CopyFilesToAssetsFolderForge();
    private CopyFilesToAssetsFolderForge() {}
    public static void startSync(File workspaceRoot) {
        INSTANCE.start(workspaceRoot);
    }
    public static void stopSync() {
        INSTANCE.stop();
    }

    @Override
    protected String getSourceRelativePath() {
        return "run/ldlib";
    }

    @Override
    protected boolean shouldExclude(Path path) {
        return isNamed(path, PROJECTS_STAGING_FOLDER);
    }
}
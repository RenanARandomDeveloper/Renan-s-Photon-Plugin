package net.renan.photonplugin.copiers;

import java.io.File;
import java.nio.file.Path;

public class CopyFilesToAssetsFolderNeo extends CopyFilesToAssetsFolderCommon {
    private static final String FX_STAGING_FOLDER = "fx_projects";
    private static final String EDITOR_LAYOUTS_FOLDER = "editor_layouts";
    private static final CopyFilesToAssetsFolderNeo INSTANCE = new CopyFilesToAssetsFolderNeo();
    private CopyFilesToAssetsFolderNeo() {}
    public static void startSync(File workspaceRoot) {
        INSTANCE.start(workspaceRoot);
    }
    public static void stopSync() {
        INSTANCE.stop();
    }

    @Override
    protected String getSourceRelativePath() {
        return "run/ldlib2";
    }

    @Override
    protected boolean shouldExclude(Path path) {
        return isNamed(path, FX_STAGING_FOLDER) || isNamed(path, EDITOR_LAYOUTS_FOLDER);
    }
}
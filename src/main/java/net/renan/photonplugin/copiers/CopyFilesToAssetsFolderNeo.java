package net.renan.photonplugin.copiers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CopyFilesToAssetsFolderNeo extends CopyFilesToAssetsFolderCommon {
    private static final String FX_STAGING_FOLDER = "fx_projects";
    private static final String EDITOR_LAYOUTS_FOLDER = "editor_layouts";

    private static final Map<String, CopyFilesToAssetsFolderNeo> INSTANCES = new ConcurrentHashMap<>();

    private CopyFilesToAssetsFolderNeo() {
    }

    private static String instanceKey(File workspaceRoot) {
        try {
            return workspaceRoot.getCanonicalPath();
        } catch (IOException e) {
            return workspaceRoot.getAbsolutePath();
        }
    }

    public static void startSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            return;
        }
        CopyFilesToAssetsFolderNeo instance = INSTANCES.computeIfAbsent(instanceKey(workspaceRoot),
                key -> new CopyFilesToAssetsFolderNeo());
        instance.start(workspaceRoot);
    }

    public static void stopSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            return;
        }
        CopyFilesToAssetsFolderNeo instance = INSTANCES.remove(instanceKey(workspaceRoot));
        if (instance != null) {
            instance.stop();
        }
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

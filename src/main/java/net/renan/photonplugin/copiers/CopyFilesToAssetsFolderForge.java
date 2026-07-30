package net.renan.photonplugin.copiers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CopyFilesToAssetsFolderForge extends CopyFilesToAssetsFolderCommon {
    private static final String PROJECTS_STAGING_FOLDER = "projects";

    private static final Map<String, CopyFilesToAssetsFolderForge> INSTANCES = new ConcurrentHashMap<>();

    private CopyFilesToAssetsFolderForge() {
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
        CopyFilesToAssetsFolderForge instance = INSTANCES.computeIfAbsent(instanceKey(workspaceRoot),
                key -> new CopyFilesToAssetsFolderForge());
        instance.start(workspaceRoot);
    }

    public static void stopSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            return;
        }
        CopyFilesToAssetsFolderForge instance = INSTANCES.remove(instanceKey(workspaceRoot));
        if (instance != null) {
            instance.stop();
        }
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

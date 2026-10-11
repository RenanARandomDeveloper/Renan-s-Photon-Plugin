package net.renan.photonplugin.copiers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CopyFilesToAssetsFolderNeo {
    private static final String FX_STAGING_FOLDER = "fx_projects";
    private static final String EDITOR_LAYOUTS_FOLDER = "editor_layouts";
    private static final String LDLIB2_SOURCE = "run/ldlib2";
    private static final String PHOTON_SOURCE = "run/photon";
    private static final String PHOTON_TARGET = "src/main/resources/assets/photon";

    private static final Map<String, List<Syncer>> INSTANCES = new ConcurrentHashMap<>();

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
        List<Syncer> syncers = INSTANCES.computeIfAbsent(instanceKey(workspaceRoot),
                key -> List.of(
                        new Syncer(LDLIB2_SOURCE, null),
                        new Syncer(PHOTON_SOURCE, PHOTON_TARGET)
                ));
        for (Syncer syncer : syncers) {
            syncer.start(workspaceRoot);
        }
    }

    public static void stopSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            return;
        }
        List<Syncer> syncers = INSTANCES.remove(instanceKey(workspaceRoot));
        if (syncers != null) {
            for (Syncer syncer : syncers) {
                syncer.stop();
            }
        }
    }

    private static final class Syncer extends CopyFilesToAssetsFolder {
        private final String sourceRelativePath;
        private final String targetRelativePath;

        private Syncer(String sourceRelativePath, String targetRelativePath) {
            this.sourceRelativePath = sourceRelativePath;
            this.targetRelativePath = targetRelativePath;
        }

        @Override
        protected String getSourceRelativePath() {
            return sourceRelativePath;
        }

        @Override
        protected String getTargetRelativePath() {
            return targetRelativePath != null ? targetRelativePath : super.getTargetRelativePath();
        }

        @Override
        protected boolean shouldExclude(Path path) {
            return isNamed(path, FX_STAGING_FOLDER) || isNamed(path, EDITOR_LAYOUTS_FOLDER);
        }
    }
}
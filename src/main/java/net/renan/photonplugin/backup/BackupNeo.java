package net.renan.photonplugin.backup;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.Log;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class BackupNeo {

    private BackupNeo() {
    }

    public static void setup(MCreator mcreator) {
        File workspaceFolder = resolveWorkspaceFolder(mcreator);
        if (workspaceFolder == null) {
            Log.warn("Could not resolve workspace folder, Photon backups were not started.");
            return;
        }

        BackupCommon backup = BackupCommon.forWorkspace(workspaceFolder);
        backup.startAutoBackup(getWatchedRoots(workspaceFolder));
    }

    public static void shutdown(MCreator mcreator) {
        File workspaceFolder = resolveWorkspaceFolder(mcreator);
        if (workspaceFolder == null) {
            return;
        }
        BackupCommon.shutdownWorkspace(workspaceFolder);
    }

    private static List<Path> getWatchedRoots(File workspaceFolder) {
        File runFolder = new File(workspaceFolder, "run");
        return List.of(
                new File(runFolder, "ldlib2").toPath(),
                new File(runFolder, "photon").toPath()
        );
    }

    private static File resolveWorkspaceFolder(MCreator mcreator) {
        try {
            return mcreator.getFolderManager().getWorkspaceFolder();
        } catch (Exception e) {
            Log.error("Failed to resolve workspace folder for backups.", e);
            return null;
        }
    }
}

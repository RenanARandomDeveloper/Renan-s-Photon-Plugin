package net.renan.photonplugin.backup.core;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.log.Log;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BackupModuleSupport {
    private BackupModuleSupport() {
    }

    public static void setup(MCreator mcreator, String... extraRunSubfolders) {
        File workspaceFolder = WorkspaceFolders.resolve(mcreator);
        if (workspaceFolder == null) {
            Log.warn("Could not resolve workspace folder, Photon backups were not started.");
            return;
        }

        BackupService backup = BackupService.forWorkspace(workspaceFolder);
        backup.startAutoBackup(getWatchedRoots(workspaceFolder, extraRunSubfolders));
    }

    public static void shutdown(MCreator mcreator) {
        File workspaceFolder = WorkspaceFolders.resolve(mcreator);
        if (workspaceFolder == null) {
            return;
        }
        BackupService.shutdownWorkspace(workspaceFolder);
    }

    private static List<Path> getWatchedRoots(File workspaceFolder, String[] extraRunSubfolders) {
        File runFolder = new File(workspaceFolder, "run");
        Set<Path> roots = new LinkedHashSet<>();
        for (String subfolder : extraRunSubfolders) {
            roots.add(new File(runFolder, subfolder).toPath());
        }
        roots.add(new File(runFolder, "photon").toPath());

        return List.copyOf(roots);
    }
}

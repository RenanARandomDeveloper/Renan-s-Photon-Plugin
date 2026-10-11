package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class BackupRetention {
    private BackupRetention() {
    }

    public static void compressStaleFolders(File automaticFolder) {
        if (!automaticFolder.isDirectory()) {
            return;
        }

        String todayName = BackupNaming.todayAutomaticFolderName();

        File[] entries = automaticFolder.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }

        for (File dir : entries) {
            if (dir.getName().equalsIgnoreCase(todayName)) {
                continue;
            }
            compressAndDeleteSource(dir);
        }
    }

    private static void compressAndDeleteSource(File sourceDir) {
        File zipFile = new File(sourceDir.getParentFile(), sourceDir.getName() + ".zip");
        try {
            ZipUtils.compressDirectoryToZip(sourceDir.toPath(), zipFile);
        } catch (IOException e) {
            Log.error("Failed to compress backup folder " + sourceDir.getAbsolutePath(), e);
            return;
        }

        try {
            FileTreeUtils.deleteRecursively(sourceDir.toPath());
        } catch (IOException e) {
            Log.warn("Failed to delete original backup folder after compression: %s",
                    sourceDir.getAbsolutePath());
        }
    }

    public static void enforceLimit(File folder, int maxBackups, String activeNameToKeep) {
        if (!folder.isDirectory()) {
            return;
        }

        File[] entries = folder.listFiles((dir, name) -> BackupNaming.matches(name));
        if (entries == null || entries.length <= maxBackups) {
            return;
        }

        Arrays.sort(entries, Comparator.comparing(BackupNaming::getBackupCreationDate));

        List<File> deletable = new ArrayList<>();
        boolean activePresent = false;
        for (File entry : entries) {
            String baseName = entry.getName().replaceAll("(?i)\\.zip$", "");
            if (baseName.equalsIgnoreCase(activeNameToKeep)) {
                activePresent = true;
            } else {
                deletable.add(entry);
            }
        }

        int allowedNonActive = activePresent ? maxBackups - 1 : maxBackups;
        int amountToDelete = deletable.size() - allowedNonActive;

        int deleted = 0;
        for (File entry : deletable) {
            if (deleted >= amountToDelete) {
                break;
            }
            if (deleteEntry(entry)) {
                deleted++;
            }
        }
    }

    private static boolean deleteEntry(File entry) {
        try {
            if (entry.isDirectory()) {
                FileTreeUtils.deleteRecursively(entry.toPath());
            } else if (!entry.delete()) {
                Log.warn("Failed to delete old backup: %s", entry.getAbsolutePath());
                return false;
            }
            Log.info("Deleted old backup: %s", entry.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.warn("Failed to delete old backup '%s': %s", entry.getAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
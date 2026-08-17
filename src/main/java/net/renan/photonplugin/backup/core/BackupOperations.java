package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.backup.core.BackupService.BackupType;
import net.renan.photonplugin.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

public final class BackupOperations {
    private BackupOperations() {
    }

    public static File createBackup(BackupType type, File typeFolder, List<Path> sourceRoots) throws IOException {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IOException("No source folders configured for backup.");
        }

        LocalDateTime now = LocalDateTime.now();
        File targetDir = (type == BackupType.AUTOMATIC)
                ? new File(typeFolder, BackupNaming.todayAutomaticFolderName())
                : uniqueManualTargetDir(typeFolder, BackupNaming.timeFolderName(now));

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create backup folder: " + targetDir.getAbsolutePath());
        }

        boolean anySourceCopied = false;
        for (Path root : sourceRoots) {
            if (root == null || !Files.exists(root)) {
                Log.warn("Backup source '%s' does not exist, skipping.", root);
                continue;
            }
            String rootName = root.getFileName() != null ? root.getFileName().toString() : "root";
            Path destRoot = new File(targetDir, rootName).toPath();
            FileTreeUtils.copyDirectory(root, destRoot);
            anySourceCopied = true;
        }

        if (!anySourceCopied) {
            Log.warn("No valid backup sources were found; empty backup created at %s",
                    targetDir.getAbsolutePath());
        }

        Log.info("%s backup created at %s", type == BackupType.AUTOMATIC ? "Automatic" : "Manual",
                targetDir.getAbsolutePath());

        if (type != BackupType.MANUAL) {
            return targetDir;
        }

        File zipped = null;
        try {
            File candidate = new File(targetDir.getParentFile(), targetDir.getName() + ".zip");
            ZipUtils.compressDirectoryToZip(targetDir.toPath(), candidate);
            zipped = candidate;
        } catch (IOException e) {
            Log.error("Failed to compress backup folder " + targetDir.getAbsolutePath(), e);
        }
        if (zipped == null) {
            return targetDir;
        }
        FileTreeUtils.deleteRecursively(targetDir.toPath());
        return zipped;
    }

    private static File uniqueManualTargetDir(File typeFolder, String baseName) {
        File candidate = new File(typeFolder, baseName);
        if (!candidate.exists() && !new File(typeFolder, baseName + ".zip").exists()) {
            return candidate;
        }
        int suffix = 2;
        while (true) {
            String name = baseName + "-" + suffix;
            File next = new File(typeFolder, name);
            if (!next.exists() && !new File(typeFolder, name + ".zip").exists()) {
                return next;
            }
            suffix++;
        }
    }

    public static void restoreBackup(File backupSource, List<Path> watchedRoots) throws IOException {
        if (backupSource == null || !backupSource.exists()) {
            throw new IOException("Backup not found: " + backupSource);
        }
        if (watchedRoots == null || watchedRoots.isEmpty()) {
            throw new IOException("No watched folders configured; cannot restore.");
        }

        if (backupSource.isDirectory()) {
            restoreFromDirectory(backupSource, watchedRoots);
        } else {
            restoreFromZip(backupSource, watchedRoots);
        }
        Log.info("Restored backup from %s", backupSource.getAbsolutePath());
    }

    private static void restoreFromDirectory(File backupDir, List<Path> roots) throws IOException {
        File[] entries = backupDir.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            Path destination = resolveRestoreDestination(entry.getName(), roots);
            if (destination == null) {
                Log.warn("No watched folder matches backup entry '%s', skipping.", entry.getName());
                continue;
            }
            FileTreeUtils.copyDirectory(entry.toPath(), destination);
        }
    }

    private static void restoreFromZip(File zipFile, List<Path> roots) throws IOException {
        Path tempDir = Files.createTempDirectory("photon-restore-");
        try {
            ZipUtils.extractZip(zipFile, tempDir);
            restoreFromDirectory(tempDir.toFile(), roots);
        } finally {
            FileTreeUtils.deleteRecursively(tempDir);
        }
    }

    private static Path resolveRestoreDestination(String rootName, List<Path> roots) {
        for (Path root : roots) {
            if (root.getFileName() != null && root.getFileName().toString().equals(rootName)) {
                return root;
            }
        }
        return null;
    }

    public static void exportBackups(List<File> selectedBackups, File destination) throws IOException {
        if (selectedBackups == null || selectedBackups.isEmpty()) {
            throw new IOException("No backups selected for export.");
        }
        if (destination == null) {
            throw new IOException("No destination file provided for export.");
        }

        List<File> validBackups = new ArrayList<>();
        for (File selected : selectedBackups) {
            if (selected != null && selected.exists()) {
                validBackups.add(selected);
            }
        }
        if (validBackups.isEmpty()) {
            throw new IOException("None of the selected backups exist.");
        }

        try (FileOutputStream fos = new FileOutputStream(destination);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            for (File selected : validBackups) {
                String baseName = selected.getName().replaceAll("(?i)\\.zip$", "");

                if (selected.isDirectory()) {
                    ZipUtils.appendDirectoryToZip(zos, selected.toPath(), baseName);
                } else {
                    ZipUtils.appendZipEntries(zos, selected, baseName);
                }
            }
        }
    }

    public static int deleteBackups(List<File> selectedBackups) throws IOException {
        if (selectedBackups == null || selectedBackups.isEmpty()) {
            throw new IOException("No backups selected for deletion.");
        }

        int deletedCount = 0;
        IOException firstFailure = null;

        for (File selected : selectedBackups) {
            if (selected == null || !selected.exists()) {
                continue;
            }
            try {
                if (selected.isDirectory()) {
                    FileTreeUtils.deleteRecursively(selected.toPath());
                } else if (!selected.delete()) {
                    throw new IOException("Failed to delete file: " + selected.getAbsolutePath());
                }
                Log.info("Deleted backup: %s", selected.getAbsolutePath());
                deletedCount++;
            } catch (IOException e) {
                Log.error("Failed to delete backup " + selected.getAbsolutePath(), e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (deletedCount == 0 && firstFailure != null) {
            throw firstFailure;
        }
        return deletedCount;
    }
}

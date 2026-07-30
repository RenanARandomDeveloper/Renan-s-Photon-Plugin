package net.renan.photonplugin.backup;

import net.renan.photonplugin.Log;
import net.renan.photonplugin.WatchService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupCommon {

    public enum BackupType {
        MANUAL("manual"),
        AUTOMATIC("automatically");

        private final String folderName;

        BackupType(String folderName) {
            this.folderName = folderName;
        }

        public String getFolderName() {
            return folderName;
        }
    }

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Pattern BACKUP_NAME_PATTERN =
            Pattern.compile("backup_(\\d{4}-\\d{2}-\\d{2}(?:_\\d{2}-\\d{2}-\\d{2})?)(?:\\.zip)?",
                    Pattern.CASE_INSENSITIVE);

    private static final int MAX_BACKUPS_PER_TYPE = 10;
    private static final long DEBOUNCE_MILLIS = 3000L;

    private static final Map<String, BackupCommon> INSTANCES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService DEBOUNCE_EXECUTOR =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "Photon-Backup-Debounce");
                thread.setDaemon(true);
                return thread;
            });

    private final File workspaceFolder;
    private final File backupsRootFolder;
    private final File manualFolder;
    private final File automaticFolder;
    private final WatchService watchService = new WatchService();
    private volatile List<Path> watchedRoots = List.of();
    private volatile ScheduledFuture<?> pendingAutomaticBackup;

    private BackupCommon(File workspaceFolder) {
        this.workspaceFolder = workspaceFolder;

        File photonFolder = new File(workspaceFolder, ".photon");
        this.backupsRootFolder = new File(photonFolder, "backups");
        this.manualFolder = new File(backupsRootFolder, "manual");
        this.automaticFolder = new File(backupsRootFolder, "automatically");

        if (!manualFolder.exists() && !manualFolder.mkdirs()) {
            Log.error("Failed to create manual backups folder at " + manualFolder.getAbsolutePath());
        }
        if (!automaticFolder.exists() && !automaticFolder.mkdirs()) {
            Log.error("Failed to create automatic backups folder at " + automaticFolder.getAbsolutePath());
        }

        compressStaleAutomaticBackups();
        enforceBackupLimit(BackupType.MANUAL);
        enforceBackupLimit(BackupType.AUTOMATIC);
    }

    public static BackupCommon forWorkspace(File workspaceFolder) {
        if (workspaceFolder == null) {
            throw new IllegalArgumentException("workspaceFolder must not be null");
        }
        return INSTANCES.computeIfAbsent(instanceKey(workspaceFolder), key -> new BackupCommon(workspaceFolder));
    }

    public static void shutdownWorkspace(File workspaceFolder) {
        String key = instanceKey(workspaceFolder);
        if (key == null) {
            return;
        }
        BackupCommon instance = INSTANCES.remove(key);
        if (instance != null) {
            instance.stopAutoBackup();
        }
    }

    private static String instanceKey(File workspaceFolder) {
        if (workspaceFolder == null) {
            return null;
        }
        try {
            return workspaceFolder.getCanonicalPath();
        } catch (IOException e) {
            return workspaceFolder.getAbsolutePath();
        }
    }

    public synchronized void startAutoBackup(List<Path> rootsToWatch) {
        stopAutoBackup();

        if (rootsToWatch == null || rootsToWatch.isEmpty()) {
            return;
        }

        watchedRoots = List.copyOf(rootsToWatch);

        Predicate<Path> exclusion = this::isExcludedFromWatch;
        watchService.start(watchedRoots, exclusion, (watchedRoot, changedPath, kind) -> {
            Log.bindWorkspace(workspaceFolder);
            scheduleAutomaticBackup();
        });

        Log.info("Photon backup watch service started for: %s", watchedRoots);

        scheduleAutomaticBackup();
    }

    public synchronized void stopAutoBackup() {
        watchService.stop();

        if (pendingAutomaticBackup != null) {
            pendingAutomaticBackup.cancel(false);
            pendingAutomaticBackup = null;
        }
    }

    public boolean isAutoBackupRunning() {
        return watchService.isRunning();
    }

    private boolean isExcludedFromWatch(Path path) {
        if (path == null) {
            return false;
        }
        String pathString = path.toString();
        return path.startsWith(backupsRootFolder.toPath())
                || pathString.contains(File.separator + ".photon" + File.separator)
                || pathString.endsWith(File.separator + ".photon");
    }

    private synchronized void scheduleAutomaticBackup() {
        if (pendingAutomaticBackup != null && !pendingAutomaticBackup.isDone()) {
            pendingAutomaticBackup.cancel(false);
        }
        pendingAutomaticBackup = DEBOUNCE_EXECUTOR.schedule(this::runAutomaticBackup,
                DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void runAutomaticBackup() {
        Log.bindWorkspace(workspaceFolder);
        try {
            performBackup(BackupType.AUTOMATIC, watchedRoots);
        } catch (IOException e) {
            Log.error("Failed to create automatic backup.", e);
        }
    }

    public File createManualBackup() throws IOException {
        return createManualBackup(watchedRoots);
    }

    public synchronized File createManualBackup(List<Path> sourceRoots) throws IOException {
        return performBackup(BackupType.MANUAL, sourceRoots);
    }

    public synchronized void restoreBackup(File backup) throws IOException {
        if (backup == null || !backup.exists()) {
            throw new IOException("Backup not found: " + backup);
        }
        if (watchedRoots.isEmpty()) {
            throw new IOException("No watched folders configured; cannot restore.");
        }

        List<Path> rootsToResume = watchedRoots;
        stopAutoBackup();
        try {
            if (backup.isDirectory()) {
                restoreFromDirectory(backup, rootsToResume);
            } else {
                restoreFromZip(backup, rootsToResume);
            }
            Log.info("Restored backup from %s", backup.getAbsolutePath());
        } finally {
            startAutoBackup(rootsToResume);
        }
    }

    private void restoreFromDirectory(File backupDir, List<Path> roots) throws IOException {
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
            copyDirectory(entry.toPath(), destination);
        }
    }

    private void restoreFromZip(File zipFile, List<Path> roots) throws IOException {
        Path tempDir = Files.createTempDirectory("photon-restore-");
        try {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = tempDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                        continue;
                    }
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                    zis.closeEntry();
                }
            }
            restoreFromDirectory(tempDir.toFile(), roots);
        } finally {
            deleteRecursively(tempDir);
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

    private synchronized File performBackup(BackupType type, List<Path> sourceRoots) throws IOException {
        File typeFolder = folderFor(type);
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IOException("No source folders configured for backup.");
        }

        if (type == BackupType.AUTOMATIC) {
            compressStaleAutomaticBackups();
        }

        LocalDateTime now = LocalDateTime.now();
        String folderName = type == BackupType.AUTOMATIC
                ? "backup_" + now.format(DAY_FORMAT)
                : "backup_" + now.format(TIME_FORMAT);

        File targetDir = new File(typeFolder, folderName);
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
            copyDirectory(root, destRoot);
            anySourceCopied = true;
        }

        if (!anySourceCopied) {
            Log.warn("No valid backup sources were found; empty backup created at %s",
                    targetDir.getAbsolutePath());
        }

        Log.info("%s backup created at %s", type == BackupType.AUTOMATIC ? "Automatic" : "Manual",
                targetDir.getAbsolutePath());

        File result = targetDir;
        if (type == BackupType.MANUAL) {
            File zipped = compressBackupFolder(targetDir, true);
            if (zipped != null) {
                result = zipped;
            }
        }

        enforceBackupLimit(type);
        return result;
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        if (Files.exists(destination)) {
            deleteRecursively(destination);
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to copy '%s' during backup: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void compressStaleAutomaticBackups() {
        if (!automaticFolder.isDirectory()) {
            return;
        }

        String todayName = "backup_" + LocalDate.now().format(DAY_FORMAT);

        File[] entries = automaticFolder.listFiles(File::isDirectory);
        if (entries == null) {
            return;
        }

        for (File dir : entries) {
            if (dir.getName().equalsIgnoreCase(todayName)) {
                continue;
            }
            compressBackupFolder(dir, true);
        }
    }

    private static File compressBackupFolder(File sourceDir, boolean deleteSource) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return null;
        }

        File zipFile = new File(sourceDir.getParentFile(), sourceDir.getName() + ".zip");
        Path basePath = sourceDir.toPath();

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = basePath.relativize(file).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Log.error("Failed to compress backup folder " + sourceDir.getAbsolutePath(), e);
            zipFile.delete();
            return null;
        }

        if (deleteSource) {
            try {
                deleteRecursively(basePath);
            } catch (IOException e) {
                Log.warn("Failed to delete original backup folder after compression: %s",
                        sourceDir.getAbsolutePath());
            }
        }

        return zipFile;
    }

    private void enforceBackupLimit(BackupType type) {
        File folder = folderFor(type);
        if (!folder.isDirectory()) {
            return;
        }

        File[] entries = folder.listFiles((dir, name) -> BACKUP_NAME_PATTERN.matcher(name).matches());
        if (entries == null || entries.length <= MAX_BACKUPS_PER_TYPE) {
            return;
        }

        String activeName = type == BackupType.AUTOMATIC ? "backup_" + LocalDate.now().format(DAY_FORMAT) : null;

        Arrays.sort(entries, Comparator.comparing(BackupCommon::getBackupCreationDate));

        int amountToDelete = entries.length - MAX_BACKUPS_PER_TYPE;
        int deleted = 0;
        for (File entry : entries) {
            if (deleted >= amountToDelete) {
                break;
            }
            String baseName = entry.getName().replaceAll("(?i)\\.zip$", "");
            if (activeName != null && baseName.equalsIgnoreCase(activeName)) {
                continue;
            }
            if (deleteEntry(entry)) {
                deleted++;
            }
        }
    }

    private static boolean deleteEntry(File entry) {
        try {
            if (entry.isDirectory()) {
                deleteRecursively(entry.toPath());
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

    public static LocalDateTime getBackupCreationDate(File file) {
        if (file != null) {
            Matcher matcher = BACKUP_NAME_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                String raw = matcher.group(1);
                try {
                    if (raw.length() > 10) {
                        return LocalDateTime.parse(raw, TIME_FORMAT);
                    }
                    return LocalDate.parse(raw, DAY_FORMAT).atStartOfDay();
                } catch (Exception ignored) {
                }
            }
        }

        long lastModified = file != null ? file.lastModified() : System.currentTimeMillis();
        return LocalDateTime.ofEpochSecond(lastModified / 1000, 0,
                java.time.ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now()));
    }

    public List<File> listAvailableBackups(BackupType type) {
        List<File> result = new ArrayList<>();
        File folder = folderFor(type);
        if (!folder.isDirectory()) {
            return result;
        }

        File[] entries = folder.listFiles((dir, name) -> BACKUP_NAME_PATTERN.matcher(name).matches());
        if (entries == null) {
            return result;
        }

        result.addAll(Arrays.asList(entries));
        result.sort(Comparator.comparing(BackupCommon::getBackupCreationDate).reversed());
        return result;
    }

    public synchronized void exportBackups(List<File> selectedBackups, File destination) throws IOException {
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
                    Path basePath = selected.toPath();
                    Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String entryName = baseName + "/"
                                    + basePath.relativize(file).toString().replace(File.separatorChar, '/');
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(file, zos);
                            zos.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(selected))) {
                        ZipEntry entry;
                        byte[] buffer = new byte[8192];
                        while ((entry = zis.getNextEntry()) != null) {
                            zos.putNextEntry(new ZipEntry(baseName + "/" + entry.getName()));
                            int read;
                            while ((read = zis.read(buffer)) != -1) {
                                zos.write(buffer, 0, read);
                            }
                            zos.closeEntry();
                        }
                    }
                }
            }
        }
    }

    private File folderFor(BackupType type) {
        return type == BackupType.MANUAL ? manualFolder : automaticFolder;
    }

    public File getBackupsRootFolder() {
        return backupsRootFolder;
    }

    public File getManualFolder() {
        return manualFolder;
    }

    public File getAutomaticFolder() {
        return automaticFolder;
    }

    public File getWorkspaceFolder() {
        return workspaceFolder;
    }
}

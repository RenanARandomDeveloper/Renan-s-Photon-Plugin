package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.log.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BackupService {
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

    private static final int DEFAULT_MAX_BACKUPS = 10;

    private static final Map<BackupType, Integer> MAX_BACKUPS_PER_TYPE = new EnumMap<>(BackupType.class);
    static {
        MAX_BACKUPS_PER_TYPE.put(BackupType.MANUAL, DEFAULT_MAX_BACKUPS);
        MAX_BACKUPS_PER_TYPE.put(BackupType.AUTOMATIC, DEFAULT_MAX_BACKUPS);
    }

    private static final Map<String, BackupService> INSTANCES = new ConcurrentHashMap<>();

    private final File workspaceFolder;
    private final File backupsRootFolder;
    private final File manualFolder;
    private final File automaticFolder;
    private final BackupWatcher watcher;

    private BackupService(File workspaceFolder) {
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

        this.watcher = new BackupWatcher(backupsRootFolder,
                () -> Log.bindWorkspace(workspaceFolder),
                this::runAutomaticBackup);

        BackupRetention.compressStaleFolders(automaticFolder);
        enforceBackupLimit(BackupType.MANUAL);
        enforceBackupLimit(BackupType.AUTOMATIC);
    }

    public static BackupService forWorkspace(File workspaceFolder) {
        if (workspaceFolder == null) {
            throw new IllegalArgumentException("workspaceFolder must not be null");
        }
        return INSTANCES.computeIfAbsent(instanceKey(workspaceFolder), key -> new BackupService(workspaceFolder));
    }

    public static void shutdownWorkspace(File workspaceFolder) {
        String key = instanceKey(workspaceFolder);
        if (key == null) {
            return;
        }
        BackupService instance = INSTANCES.remove(key);
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

    public void startAutoBackup(List<Path> rootsToWatch) {
        watcher.start(rootsToWatch, !hasTodayAutomaticBackup());
    }

    private boolean hasTodayAutomaticBackup() {
        String todayName = BackupNaming.todayAutomaticFolderName();
        return new File(automaticFolder, todayName).exists()
                || new File(automaticFolder, todayName + ".zip").exists();
    }

    public void stopAutoBackup() {
        watcher.stop();
    }

    public boolean isAutoBackupRunning() {
        return watcher.isRunning();
    }

    private void runAutomaticBackup() {
        Log.bindWorkspace(workspaceFolder);
        try {
            performBackup(BackupType.AUTOMATIC, watcher.getWatchedRoots());
        } catch (IOException e) {
            Log.error("Failed to create automatic backup.", e);
        }
    }

    public File createManualBackup() throws IOException {
        return createManualBackup(watcher.getWatchedRoots());
    }

    public synchronized File createManualBackup(List<Path> sourceRoots) throws IOException {
        return performBackup(BackupType.MANUAL, sourceRoots);
    }

    private synchronized File performBackup(BackupType type, List<Path> sourceRoots) throws IOException {
        if (type == BackupType.AUTOMATIC) {
            BackupRetention.compressStaleFolders(automaticFolder);
        }

        File result = BackupOperations.createBackup(type, folderFor(type), sourceRoots);
        enforceBackupLimit(type);
        return result;
    }

    public synchronized void restoreBackup(File backup) throws IOException {
        List<Path> rootsToResume = watcher.getWatchedRoots();
        stopAutoBackup();
        try {
            BackupOperations.restoreBackup(backup, rootsToResume);
        } finally {
            startAutoBackup(rootsToResume);
        }
    }

    public List<File> listAvailableBackups(BackupType type) {
        List<File> result = new ArrayList<>();
        File folder = folderFor(type);
        if (!folder.isDirectory()) {
            return result;
        }

        File[] entries = folder.listFiles((dir, name) -> BackupNaming.matches(name));
        if (entries == null) {
            return result;
        }

        result.addAll(Arrays.asList(entries));
        result.sort(Comparator.comparing(BackupNaming::getBackupCreationDate).reversed());
        return result;
    }

    public synchronized void exportBackups(List<File> selectedBackups, File destination) throws IOException {
        BackupOperations.exportBackups(selectedBackups, destination);
    }

    public synchronized int deleteBackups(List<File> selectedBackups) throws IOException {
        return BackupOperations.deleteBackups(selectedBackups);
    }

    public static LocalDateTime getBackupCreationDate(File file) {
        return BackupNaming.getBackupCreationDate(file);
    }

    private void enforceBackupLimit(BackupType type) {
        String activeName = type == BackupType.AUTOMATIC ? BackupNaming.todayAutomaticFolderName() : null;
        int limit = MAX_BACKUPS_PER_TYPE.getOrDefault(type, DEFAULT_MAX_BACKUPS);
        BackupRetention.enforceLimit(folderFor(type), limit, activeName);
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

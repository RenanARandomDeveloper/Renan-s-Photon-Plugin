package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.log.Log;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class BackupWatcher {
    private static final long DEBOUNCE_MILLIS = 3000L;
    private static final ScheduledExecutorService DEBOUNCE_EXECUTOR =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "Photon-Backup-Debounce");
                thread.setDaemon(true);
                return thread;
            });

    private final File backupsRootFolder;
    private final Runnable onChangeDetected;
    private final Runnable onDebouncedBackup;
    private final WatchService watchService = new WatchService();

    private volatile List<Path> watchedRoots = List.of();
    private volatile ScheduledFuture<?> pendingBackup;

    public BackupWatcher(File backupsRootFolder, Runnable onChangeDetected, Runnable onDebouncedBackup) {
        this.backupsRootFolder = backupsRootFolder;
        this.onChangeDetected = onChangeDetected;
        this.onDebouncedBackup = onDebouncedBackup;
    }

    public synchronized void start(List<Path> rootsToWatch, boolean triggerInitialBackup) {
        stop();

        if (rootsToWatch == null || rootsToWatch.isEmpty()) {
            return;
        }

        watchedRoots = List.copyOf(rootsToWatch);

        Predicate<Path> exclusion = this::isExcludedFromWatch;
        watchService.start(watchedRoots, exclusion, (watchedRoot, changedPath, kind) -> {
            onChangeDetected.run();
            scheduleBackup();
        });

        Log.info("Photon backup watch service started for: %s", watchedRoots);

        if (triggerInitialBackup) {
            scheduleBackup();
        }
    }

    public synchronized void stop() {
        watchService.stop();

        if (pendingBackup != null) {
            pendingBackup.cancel(false);
            pendingBackup = null;
        }
    }

    public boolean isRunning() {
        return watchService.isRunning();
    }

    public List<Path> getWatchedRoots() {
        return watchedRoots;
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

    private synchronized void scheduleBackup() {
        if (pendingBackup != null && !pendingBackup.isDone()) {
            pendingBackup.cancel(false);
        }
        pendingBackup = DEBOUNCE_EXECUTOR.schedule(onDebouncedBackup, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }
}

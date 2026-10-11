package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.log.Log;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Predicate;

public final class BackupWatcher {
    private static final long DEBOUNCE_MILLIS = 3000L;
    private static final long MAX_DEBOUNCE_WAIT_MILLIS = 30_000L;
    private static final long SHUTDOWN_GRACE_MILLIS = 15_000L;
    private static final ScheduledExecutorService DEBOUNCE_EXECUTOR =
            Executors.newScheduledThreadPool(4, runnable -> {
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
    private volatile long debounceWindowStartedAt = 0L;

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

    public void stop() {
        stop(false);
    }

    public synchronized void stop(boolean awaitInFlightBackup) {
        watchService.stop();

        if (pendingBackup != null) {
            ScheduledFuture<?> future = pendingBackup;
            boolean cancelled = future.cancel(false);
            if (!cancelled && awaitInFlightBackup) {
                try {
                    future.get(SHUTDOWN_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    Log.warn("A backup for '%s' was still running after the shutdown grace period; "
                            + "it may still be writing to disk.", backupsRootFolder);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | CancellationException e) {
                    Log.error("Error occurred while waiting for in-flight backup to finish.", e);
                }
            }
            pendingBackup = null;
        }
        debounceWindowStartedAt = 0L;
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
        long now = System.currentTimeMillis();
        if (pendingBackup != null && !pendingBackup.isDone()) {
            pendingBackup.cancel(false);
        }
        if (debounceWindowStartedAt == 0L) {
            debounceWindowStartedAt = now;
        }

        long desiredFireAt = now + DEBOUNCE_MILLIS;
        long latestAllowedFireAt = debounceWindowStartedAt + MAX_DEBOUNCE_WAIT_MILLIS;
        long delay = Math.max(0L, Math.min(desiredFireAt, latestAllowedFireAt) - now);

        pendingBackup = DEBOUNCE_EXECUTOR.schedule(() -> {
            debounceWindowStartedAt = 0L;
            onDebouncedBackup.run();
        }, delay, TimeUnit.MILLISECONDS);
    }
}
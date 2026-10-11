package net.renan.photonplugin.menus.resource.core.support;

import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.naming.StrictNameEnforcer;

import javax.swing.*;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DirectoryWatcher {

    private static final int REFRESH_DEBOUNCE_MILLIS = 200;
    private static final int REFRESH_RETRY_WHILE_SUPPRESSED_MILLIS = 250;
    private static final int UNLOCK_ATTEMPTS = 12;
    private static final long UNLOCK_DELAY_MILLIS = 250L;

    private DirectoryWatcher() {
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        attachDirectoryWatcher(panel, browser, targetDirGetter, null);
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                              WatchService.ChangeListener extraListener) {
        File dir = targetDirGetter.get();
        if (dir == null) return;
        if (!dir.exists() && !dir.mkdirs()) {
            MenuLog.warn("Could not create watched directory: " + dir.getAbsolutePath());
        }

        Controller controller = new Controller(panel, browser, dir, extraListener);
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (panel.isDisplayable()) {
                controller.activate();
            } else {
                controller.deactivate();
            }
        });
        if (panel.isDisplayable()) {
            controller.activate();
        }
    }

    private static boolean isIgnored(Path path) {
        Path name = path.getFileName();
        if (name == null) return false;
        File file = new File(name.toString());
        return FileOps.isOwnTemporary(file) || FileOps.isSystemJunk(file);
    }

    private static final class Controller {

        private final JPanel panel;
        private final FXBrowserPanel browser;
        private final File dir;
        private final WatchService.ChangeListener extraListener;
        private final WatchService watchService = new WatchService();
        private final ThreadPoolExecutor worker;
        private final Timer refreshTimer;
        private volatile boolean active = false;
        private Future<?> startTask;

        Controller(JPanel panel, FXBrowserPanel browser, File dir, WatchService.ChangeListener extraListener) {
            this.panel = panel;
            this.browser = browser;
            this.dir = dir;
            this.extraListener = extraListener;
            this.worker = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "Photon-DirectoryWatcher");
                thread.setDaemon(true);
                return thread;
            });
            this.worker.allowCoreThreadTimeOut(true);
            this.refreshTimer = new Timer(REFRESH_DEBOUNCE_MILLIS, e -> flushRefresh());
            this.refreshTimer.setRepeats(false);
        }

        synchronized void activate() {
            if (active) return;
            active = true;
            startTask = worker.submit(this::startWatching);
        }

        synchronized void deactivate() {
            if (!active) return;
            active = false;
            if (startTask != null) {
                startTask.cancel(true);
                startTask = null;
            }
            worker.submit(watchService::stop);
            SwingUtilities.invokeLater(refreshTimer::stop);
        }

        private void startWatching() {
            try {
                FileOps.recoverRenameTemps(dir);
                StrictNameEnforcer.validateExistingFileNamesBlocking(panel, dir, browser.getExtension());
            } catch (RuntimeException ex) {
                MenuLog.error("Initial name validation failed for '" + dir.getAbsolutePath() + "'", ex);
            }
            if (!active || Thread.currentThread().isInterrupted()) return;
            try {
                watchService.start(List.of(dir.toPath()), DirectoryWatcher::isIgnored, this::onChange);
            } catch (RuntimeException ex) {
                MenuLog.error("Could not start the directory watcher for '" + dir.getAbsolutePath() + "'", ex);
                return;
            }
            requestRefresh();
        }

        private void onChange(Path root, Path changed, WatchService.ChangeKind kind) {
            File changedFile = changed.toFile();
            if (kind == WatchService.ChangeKind.CREATE && FileNameRules.requiresStrictNaming(browser.getExtension())) {
                if (changedFile.isDirectory()) {
                    validateDirectory(changedFile);
                } else if (changedFile.isFile() && FileNameRules.hasExtension(changedFile, browser.getExtension())
                        && enforceFileName(changedFile)) {
                    requestRefresh();
                    return;
                }
            }

            if (extraListener != null) extraListener.onChange(root, changed, kind);
            requestRefresh();
        }

        private boolean enforceFileName(File changedFile) {
            FileNameParts parts = FileNameParts.of(changedFile);
            boolean nameAlreadyValid = FileNameRules.isValidStrictFileName(parts.baseName())
                    && parts.extension().equals(browser.getExtension());
            if (nameAlreadyValid) return false;

            FileOps.waitUntilUnlocked(changedFile, UNLOCK_ATTEMPTS, UNLOCK_DELAY_MILLIS);
            File result;
            browser.beginInternalMutation();
            try {
                result = StrictNameEnforcer.enforceValidNameBlocking(panel, changedFile, browser.getExtension());
            } finally {
                browser.endInternalMutation();
            }
            return !FileOps.isSamePathExact(result, changedFile);
        }

        private void validateDirectory(File newDir) {
            browser.beginInternalMutation();
            try {
                StrictNameEnforcer.validateExistingFileNamesBlocking(panel, newDir, browser.getExtension());
            } finally {
                browser.endInternalMutation();
            }
        }

        private void requestRefresh() {
            SwingUtilities.invokeLater(() -> {
                if (!active) return;
                refreshTimer.setInitialDelay(REFRESH_DEBOUNCE_MILLIS);
                refreshTimer.restart();
            });
        }

        private void flushRefresh() {
            if (!active) return;
            if (browser.isWatcherRefreshSuppressed()) {
                refreshTimer.setInitialDelay(REFRESH_RETRY_WHILE_SUPPRESSED_MILLIS);
                refreshTimer.restart();
                return;
            }
            browser.refresh();
        }
    }
}

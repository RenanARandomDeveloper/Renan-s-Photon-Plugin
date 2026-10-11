package net.renan.photonplugin.workspaceclass;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.log.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Predicate;

public final class WorkspaceClassGenerationGuard {

    private static final long LIVENESS_CHECK_INTERVAL_SECONDS = 3L;
    private static final long DELETE_DEBOUNCE_MILLIS = 400L;

    private static final ConcurrentHashMap<String, WatchEntry> ACTIVE_WATCHERS = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService DEBOUNCE_EXECUTOR = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Photon-ClassGenWatcher-Debounce");
        thread.setDaemon(true);
        return thread;
    });

    private static final ScheduledExecutorService LIVENESS_CHECKER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Photon-ClassGenWatcher-LivenessCheck");
        thread.setDaemon(true);
        return thread;
    });

    static {
        LIVENESS_CHECKER.scheduleWithFixedDelay(WorkspaceClassGenerationGuard::pruneClosedWorkspaces,
                LIVENESS_CHECK_INTERVAL_SECONDS, LIVENESS_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private WorkspaceClassGenerationGuard() {
    }

    public static void startWatching(MCreator mcreator, Runnable generatorCall) {
        String key = WorkspacePaths.sessionKey(mcreator);
        if (key == null) {
            return;
        }

        Workspace workspace = mcreator.getWorkspace();
        WorkspaceSettings settings = workspace == null ? null : workspace.getWorkspaceSettings();
        if (settings == null) {
            return;
        }

        if (!isPhotonDependency(settings)) {
            stopWatching(mcreator);
            return;
        }

        Path targetPackageDir;
        try {
            targetPackageDir = WorkspacePaths.resolveTargetPackageDir(workspace, settings);
        } catch (WorkspacePaths.InvalidWorkspacePathException e) {
            Log.error(e.getMessage());
            return;
        }
        if (targetPackageDir == null) {
            return;
        }

        Path watchedParent = targetPackageDir.getParent();
        if (watchedParent == null) {
            return;
        }

        ACTIVE_WATCHERS.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.stop();
            }

            WatchEntry entry = new WatchEntry(mcreator, targetPackageDir, generatorCall);
            Predicate<Path> exclusion = path ->
                    !path.equals(watchedParent)
                            && !path.equals(targetPackageDir)
                            && !path.startsWith(targetPackageDir);

            entry.handleMissing();

            entry.watchService.start(List.of(watchedParent), exclusion, (root, changed, kind) -> {
                if (kind == WatchService.ChangeKind.OVERFLOW) {
                    entry.scheduleHandleMissing();
                    return;
                }
                if (kind == WatchService.ChangeKind.DELETE
                        && (changed.equals(targetPackageDir) || changed.startsWith(targetPackageDir))) {
                    entry.scheduleHandleMissing();
                }
            });

            return entry;
        });
    }

    public static void stopWatching(MCreator mcreator) {
        String key = WorkspacePaths.sessionKey(mcreator);
        if (key == null) {
            return;
        }

        ACTIVE_WATCHERS.computeIfPresent(key, (k, entry) -> {
            entry.stop();
            return null;
        });
    }

    private static void pruneClosedWorkspaces() {
        ACTIVE_WATCHERS.entrySet().removeIf(mapEntry -> {
            WatchEntry entry = mapEntry.getValue();
            if (entry.mcreator.getWorkspace() != null) {
                return false;
            }
            entry.stop();
            return true;
        });
    }

    private static boolean isPhotonDependency(WorkspaceSettings settings) {
        return settings.getMCreatorDependencies() != null && settings.getMCreatorDependencies().contains("photon_plugin");
    }

    private static boolean allFilesPresent(Path targetPackageDir) {
        if (Files.notExists(targetPackageDir)) {
            return false;
        }

        for (String baseClassName : WorkspaceConstants.getBaseClassNames()) {
            if (Files.notExists(targetPackageDir.resolve(WorkspaceConstants.getJavaFileName(baseClassName)))) {
                return false;
            }
        }

        return true;
    }

    private static final class WatchEntry {

        private final MCreator mcreator;
        private final Path targetPackageDir;
        private final Runnable generatorCall;
        private final WatchService watchService = new WatchService();
        private volatile ScheduledFuture<?> pendingCheck;

        WatchEntry(MCreator mcreator, Path targetPackageDir, Runnable generatorCall) {
            this.mcreator = mcreator;
            this.targetPackageDir = targetPackageDir;
            this.generatorCall = generatorCall;
        }

        void scheduleHandleMissing() {
            ScheduledFuture<?> previous = pendingCheck;
            if (previous != null) {
                previous.cancel(false);
            }
            pendingCheck = DEBOUNCE_EXECUTOR.schedule(this::handleMissing, DELETE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }

        void stop() {
            ScheduledFuture<?> previous = pendingCheck;
            if (previous != null) {
                previous.cancel(false);
            }
            watchService.stop();
        }

        void handleMissing() {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return;
            }

            WorkspaceSettings settings = workspace.getWorkspaceSettings();
            if (settings == null || !isPhotonDependency(settings)) {
                return;
            }

            if (allFilesPresent(targetPackageDir)) {
                return;
            }

            Log.warn("Detected photon_plugin folder removal (likely a concurrent workspace task); regenerating.");
            generatorCall.run();
        }
    }
}
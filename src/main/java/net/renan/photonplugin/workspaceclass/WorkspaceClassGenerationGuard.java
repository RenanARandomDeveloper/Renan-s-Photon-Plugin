package net.renan.photonplugin.workspaceclass;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.renan.photonplugin.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkspaceClassGenerationGuard {

    private static final long RETRY_BACKOFF_MS = 1000L;

    private static final ConcurrentHashMap<String, Watcher> ACTIVE_WATCHERS = new ConcurrentHashMap<>();

    private WorkspaceClassGenerationGuard() {
    }

    public static void startWatching(MCreator mcreator, Runnable generatorCall) {
        String key = sessionKey(mcreator);
        if (key == null) {
            return;
        }

        Workspace workspace = mcreator.getWorkspace();
        WorkspaceSettings settings = workspace == null ? null : workspace.getWorkspaceSettings();
        if (settings == null) {
            return;
        }

        Path targetPackageDir = resolveTargetPackageDir(workspace, settings);
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

            Watcher watcher = new Watcher(mcreator, targetPackageDir, watchedParent, generatorCall);
            watcher.start();
            return watcher;
        });
    }

    public static void stopWatching(MCreator mcreator) {
        String key = sessionKey(mcreator);
        if (key == null) {
            return;
        }

        ACTIVE_WATCHERS.computeIfPresent(key, (k, watcher) -> {
            watcher.stop();
            return null;
        });
    }

    private static boolean isPhotonDependency(WorkspaceSettings settings) {
        return settings.getMCreatorDependencies() != null && settings.getMCreatorDependencies().contains("photon_plugin");
    }

    private static Path resolveTargetPackageDir(Workspace workspace, WorkspaceSettings settings) {
        String basePackage = settings.getModElementsPackage();
        if (basePackage == null || basePackage.isBlank()) {
            return null;
        }

        File workspaceFolderFile = workspace.getWorkspaceFolder();
        if (workspaceFolderFile == null) {
            return null;
        }

        return workspaceFolderFile.toPath().normalize()
                .resolve("src/main/java")
                .resolve(basePackage.replace('.', '/'))
                .resolve(WorkspaceConstants.TARGET_SUB_PACKAGE)
                .normalize();
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

    private static String sessionKey(MCreator mcreator) {
        if (mcreator == null) {
            return null;
        }

        File workspaceFolder = mcreator.getWorkspaceFolder();
        if (workspaceFolder == null) {
            return null;
        }

        try {
            return workspaceFolder.getCanonicalPath();
        } catch (IOException e) {
            return workspaceFolder.getAbsolutePath();
        }
    }

    private static final class Watcher {

        private final MCreator mcreator;
        private final Path targetPackageDir;
        private final Path watchedParent;
        private final Runnable generatorCall;
        private volatile boolean running = true;
        private volatile WatchService currentService;
        private Thread thread;

        Watcher(MCreator mcreator, Path targetPackageDir, Path watchedParent, Runnable generatorCall) {
            this.mcreator = mcreator;
            this.targetPackageDir = targetPackageDir;
            this.watchedParent = watchedParent;
            this.generatorCall = generatorCall;
        }

        void start() {
            thread = new Thread(this::run, "Photon-ClassGenWatcher");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            WatchService service = currentService;
            if (service != null) {
                try {
                    service.close();
                } catch (IOException ignored) {
                }
            }
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void run() {
            try {
                handleMissing();

                while (running && isWorkspaceStillOpen()) {
                    try {
                        watchOnce();
                    } catch (IOException e) {
                        Log.warn("Photon class-generation watcher hit an I/O error, retrying: " + e.getMessage());
                    } catch (RuntimeException e) {
                        Log.warn("Photon class-generation watcher hit an unexpected error, retrying: " + e.getMessage());
                    }

                    if (running) {
                        Thread.sleep(RETRY_BACKOFF_MS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void watchOnce() throws IOException, InterruptedException {
            if (Files.notExists(watchedParent)) {
                Files.createDirectories(watchedParent);
            }

            try (WatchService service = FileSystems.getDefault().newWatchService()) {
                currentService = service;
                watchedParent.register(service, StandardWatchEventKinds.ENTRY_DELETE);

                while (running) {
                    WatchKey key;
                    try {
                        key = service.take();
                    } catch (ClosedWatchServiceException e) {
                        return;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            handleMissing();
                            continue;
                        }

                        Object context = event.context();
                        if (!(context instanceof Path)) {
                            continue;
                        }

                        Path changedName = (Path) context;
                        if (!changedName.getFileName().equals(targetPackageDir.getFileName())) {
                            continue;
                        }

                        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            handleMissing();
                        }
                    }

                    if (!key.reset()) {
                        return;
                    }
                }
            }
        }

        private void handleMissing() {
            if (!isDependencyStillActive()) {
                return;
            }

            if (allFilesPresent(targetPackageDir)) {
                return;
            }

            Log.warn("Detected photon_plugin folder removal (likely a concurrent workspace task); regenerating.");
            generatorCall.run();
        }

        private boolean isDependencyStillActive() {
            Workspace workspace = mcreator.getWorkspace();
            if (workspace == null) {
                return false;
            }

            WorkspaceSettings settings = workspace.getWorkspaceSettings();
            return settings != null && isPhotonDependency(settings);
        }

        private boolean isWorkspaceStillOpen() {
            return mcreator.getWorkspace() != null;
        }
    }
}
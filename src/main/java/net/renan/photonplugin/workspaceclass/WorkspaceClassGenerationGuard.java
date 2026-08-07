package net.renan.photonplugin.workspaceclass;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.renan.photonplugin.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class WorkspaceClassGenerationGuard {

    private static final long[] RECHECK_DELAYS_MS = {3000L, 8000L};

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Photon-ClassGenGuard");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<String, ScheduledFuture<?>[]> PENDING_CHECKS = new ConcurrentHashMap<>();

    private WorkspaceClassGenerationGuard() {
    }

    public static void scheduleVerification(MCreator mcreator, Runnable generatorCall) {
        String key = sessionKey(mcreator);
        if (key == null) {
            return;
        }

        cancelPending(key);

        ScheduledFuture<?>[] futures = new ScheduledFuture<?>[RECHECK_DELAYS_MS.length];
        PENDING_CHECKS.put(key, futures);

        long cumulativeDelay = 0L;
        for (int i = 0; i < RECHECK_DELAYS_MS.length; i++) {
            cumulativeDelay += RECHECK_DELAYS_MS[i];
            futures[i] = SCHEDULER.schedule(() -> verifyOnce(mcreator, generatorCall), cumulativeDelay, TimeUnit.MILLISECONDS);
        }
    }

    public static void cancel(MCreator mcreator) {
        String key = sessionKey(mcreator);
        if (key != null) {
            cancelPending(key);
        }
    }

    private static void cancelPending(String key) {
        ScheduledFuture<?>[] previous = PENDING_CHECKS.remove(key);
        if (previous == null) {
            return;
        }

        for (ScheduledFuture<?> future : previous) {
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private static void verifyOnce(MCreator mcreator, Runnable generatorCall) {
        if (mcreator == null) {
            return;
        }

        Workspace workspace = mcreator.getWorkspace();
        if (workspace == null) {
            return;
        }

        WorkspaceSettings settings = workspace.getWorkspaceSettings();
        if (settings == null || !isPhotonDependency(settings)) {
            return;
        }

        Path targetPackageDir = resolveTargetPackageDir(workspace, settings);
        if (targetPackageDir != null && allFilesPresent(targetPackageDir)) {
            return;
        }

        Log.warn("Detected missing photon_plugin classes after generation (likely removed by a concurrent workspace task); regenerating.");
        generatorCall.run();
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
}
package net.renan.photonplugin.fixers;

import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.log.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class AssetPlacement {
    private static final Map<String, AssetPlacement> INSTANCES = new ConcurrentHashMap<>();
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final String ASSETS_RELATIVE_PATH = "run/ldlib2/assets";
    private static final String FXPACKS_RELATIVE_PATH = "run/photon/fxpacks";
    private static final String FX_PROJECTS_TARGET_RELATIVE_PATH = "fx_projects";
    private static final String FX_TARGET_RELATIVE_PATH = "photon/fx";
    private static final String TEXTURES_TARGET_RELATIVE_PATH = "ldlib2/textures";
    private static final String RESOURCES_TARGET_RELATIVE_PATH = "ldlib2/resources";
    private static final String MODELS_TARGET_RELATIVE_PATH = "ldlib2/models";
    private static final String SHADERS_RELATIVE_PATH = "ldlib2/shaders/core";
    private static final String JSON_EXTENSION = "json";

    private static final long DEBOUNCE_MS = 400;
    private static final long RETRY_BASE_DELAY_MS = 500;
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_CONFLICT_SUFFIX = 10_000;
    private static final long STOP_AWAIT_MS = 2000;
    private final File workspaceRoot;
    private final WatchService watchService = new WatchService();
    private final Path assetsRoot;
    private final Path fxProjectsDir;
    private final Path fxDir;
    private final Path fxpacksDir;
    private final Path texturesDir;
    private final Path resourcesDir;
    private final Path modelsDir;
    private final Path shadersDir;
    private final Map<String, Rule> rulesByExtension;
    private final List<Path> flatDirs;
    private final ScheduledThreadPoolExecutor executor;
    private final Map<TaskKey, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    private AssetPlacement(File workspaceRoot) {
        this.workspaceRoot = workspaceRoot;

        Path root = workspaceRoot.toPath().toAbsolutePath().normalize();
        this.assetsRoot = root.resolve(ASSETS_RELATIVE_PATH);
        this.fxpacksDir = root.resolve(FXPACKS_RELATIVE_PATH);
        this.fxProjectsDir = assetsRoot.resolve(FX_PROJECTS_TARGET_RELATIVE_PATH);
        this.fxDir = assetsRoot.resolve(FX_TARGET_RELATIVE_PATH);
        this.texturesDir = assetsRoot.resolve(TEXTURES_TARGET_RELATIVE_PATH);
        this.resourcesDir = assetsRoot.resolve(RESOURCES_TARGET_RELATIVE_PATH);
        this.modelsDir = assetsRoot.resolve(MODELS_TARGET_RELATIVE_PATH);
        this.shadersDir = assetsRoot.resolve(SHADERS_RELATIVE_PATH);
        this.flatDirs = List.of(fxDir, fxpacksDir);

        Map<String, Rule> rules = new HashMap<>();
        rules.put("fx", new Rule("effect (.fx)", fxDir, false));
        rules.put("fxproj", new Rule("effect project (.fxproj)", fxProjectsDir, true));
        rules.put("fxpack", new Rule("effect pack (.fxpack)", fxpacksDir, false));
        rules.put("png", new Rule("texture (.png)", texturesDir, true));
        rules.put("nbt", new Rule("resource (.nbt)", resourcesDir, true));
        rules.put("obj", new Rule("model (.obj)", modelsDir, true));
        rules.put("mtl", new Rule("model material (.mtl)", modelsDir, true));
        rules.put("glb", new Rule("model (.glb)", modelsDir, true));
        rules.put("gltf", new Rule("model (.gltf)", modelsDir, true));
        rules.put(JSON_EXTENSION, new Rule("model (.json)", modelsDir, true));
        this.rulesByExtension = Map.copyOf(rules);

        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "Photon-AssetPlacement-" + workspaceRoot.getName());
            thread.setDaemon(true);
            return thread;
        });
        ex.setRemoveOnCancelPolicy(true);
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.executor = ex;
    }

    private static String instanceKey(File workspaceRoot) {
        try {
            return workspaceRoot.getCanonicalPath();
        } catch (IOException e) {
            return workspaceRoot.getAbsolutePath();
        }
    }

    public static void startSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            Log.error("Cannot start AssetPlacement: workspaceRoot is null.");
            return;
        }

        String key = instanceKey(workspaceRoot);
        synchronized (LIFECYCLE_LOCK) {
            AssetPlacement previous = INSTANCES.remove(key);
            if (previous != null) {
                previous.stop();
            }

            AssetPlacement instance = new AssetPlacement(workspaceRoot);
            if (instance.start()) {
                INSTANCES.put(key, instance);
            }
        }
    }

    public static void stopSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            return;
        }
        synchronized (LIFECYCLE_LOCK) {
            AssetPlacement instance = INSTANCES.remove(instanceKey(workspaceRoot));
            if (instance != null) {
                instance.stop();
            }
        }
    }

    private static String extensionOf(Path file) {
        Path name = file.getFileName();
        if (name == null) {
            return "";
        }
        String text = name.toString();
        int dot = text.lastIndexOf('.');
        if (dot < 0 || dot == text.length() - 1) {
            return "";
        }
        return text.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static long retryDelayMs(int attempt) {
        return RETRY_BASE_DELAY_MS << attempt;
    }

    private static IOException merge(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private boolean start() {
        Log.info("Initializing AssetPlacement WatchService...");

        try {
            Files.createDirectories(fxProjectsDir);
            Files.createDirectories(fxDir);
            Files.createDirectories(fxpacksDir);
            Files.createDirectories(texturesDir);
            Files.createDirectories(resourcesDir);
            Files.createDirectories(modelsDir);
        } catch (IOException e) {
            Log.error("Failed to create target directories for AssetPlacement.", e);
            stop();
            return false;
        }

        Log.info("Registering file tree observers for: %s and %s", assetsRoot, fxpacksDir);
        try {
            watchService.start(List.of(assetsRoot, fxpacksDir), path -> false, this::onWatchEvent);
        } catch (RuntimeException e) {
            Log.error("Failed to start the AssetPlacement watcher.", e);
            stop();
            return false;
        }

        Log.info("Performing initial scan and correction of misplaced assets in: %s and %s", assetsRoot, fxpacksDir);
        schedule(Action.RESCAN, assetsRoot, 0, 0);
        schedule(Action.RESCAN, fxpacksDir, 0, 0);

        Log.info("AssetPlacement background sync is actively watching for changes.");
        return true;
    }

    private void stop() {
        stopped = true;
        watchService.stop();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(STOP_AWAIT_MS, TimeUnit.MILLISECONDS)) {
                Log.warn("AssetPlacement worker did not terminate within the expected time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pending.clear();
        Log.info("AssetPlacement watcher stopped.");
    }

    private Rule ruleFor(Path file) {
        String extension = extensionOf(file);
        if (JSON_EXTENSION.equals(extension) && file.startsWith(shadersDir)) {
            return null;
        }
        return rulesByExtension.get(extension);
    }

    private boolean isDirectFlatChildFolder(Path dir) {
        Path parent = dir.getParent();
        return parent != null && flatDirs.contains(parent);
    }

    private Path illegalFlatFolderOf(Path path, boolean knownFileType) {
        for (Path flatDir : flatDirs) {
            if (!path.startsWith(flatDir) || path.equals(flatDir)) {
                continue;
            }

            Path top = flatDir.resolve(flatDir.relativize(path).getName(0));
            if (!top.equals(path)) {
                return top;
            }

            if (knownFileType || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            return path;
        }
        return null;
    }

    private void onWatchEvent(Path watchedDir, Path changed, WatchService.ChangeKind kind) {
        if (stopped || changed == null) {
            return;
        }
        Log.bindWorkspace(workspaceRoot);

        switch (kind) {
            case OVERFLOW -> {
                Log.warn("File system events overflowed under '%s'. Scheduling a rescan.", changed);
                schedule(Action.RESCAN, changed, DEBOUNCE_MS, 0);
            }
            case CREATE, MODIFY -> onPathChanged(changed);
            case DELETE -> {
            }
        }
    }

    private void onPathChanged(Path changed) {
        Rule rule = ruleFor(changed);

        Path illegalFolder = illegalFlatFolderOf(changed, rule != null);
        if (illegalFolder != null) {
            schedule(Action.FX_FOLDER, illegalFolder, DEBOUNCE_MS, 0);
            return;
        }

        if (rule != null && !rule.isInPlace(changed)) {
            schedule(Action.FILE, changed, DEBOUNCE_MS, 0);
        }
    }

    private void schedule(Action action, Path path, long delayMs, int attempt) {
        if (stopped) {
            return;
        }

        TaskKey key = new TaskKey(action, path);
        try {
            pending.compute(key, (k, previous) -> {
                if (previous != null) {
                    previous.cancel(false);
                }
                return executor.schedule(() -> runTask(k, attempt), delayMs, TimeUnit.MILLISECONDS);
            });
        } catch (RejectedExecutionException e) {
            Log.warn("Failed to schedule AssetPlacement task for '%s'. Executor may be shutting down.", path);
        }
    }

    private void runTask(TaskKey key, int attempt) {
        pending.remove(key);
        if (stopped) {
            return;
        }
        Log.bindWorkspace(workspaceRoot);
        execute(key, attempt);
    }

    private void execute(TaskKey key, int attempt) {
        try {
            switch (key.action()) {
                case FILE -> correctFile(key.path());
                case FX_FOLDER -> cleanupIllegalFxFolder(key.path());
                case RESCAN -> scan(key.path());
            }
        } catch (IOException e) {
            retryOrGiveUp(key, attempt, e);
        } catch (RuntimeException e) {
            Log.warn("Unexpected error while correcting asset placement for: %s", key.path());
            Log.error("Unexpected error in AssetPlacement task.", e);
        }
    }

    private void retryOrGiveUp(TaskKey key, int attempt, IOException cause) {
        if (cause instanceof NoSuchFileException) {
            return;
        }

        int next = attempt + 1;
        if (next >= MAX_ATTEMPTS) {
            Log.warn("Giving up on correcting '%s' after %d attempts.", key.path(), MAX_ATTEMPTS);
            Log.error("Last error while correcting asset placement.", cause);
            return;
        }

        long delay = retryDelayMs(attempt);
        Log.warn("Could not correct '%s' (%s). Retrying in %d ms (%d/%d).",
                key.path(), cause.getMessage(), delay, next, MAX_ATTEMPTS - 1);

        if (!pending.containsKey(key)) {
            schedule(key.action(), key.path(), delay, next);
        }
    }

    private void scan(Path start) throws IOException {
        if (stopped || !Files.isDirectory(start)) {
            return;
        }

        List<Path> misplaced = new ArrayList<>();
        List<Path> illegalFolders = new ArrayList<>();

        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (stopped) {
                    return FileVisitResult.TERMINATE;
                }
                if (isDirectFlatChildFolder(dir)) {
                    illegalFolders.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (stopped) {
                    return FileVisitResult.TERMINATE;
                }
                if (attrs.isRegularFile()) {
                    Rule rule = ruleFor(file);
                    if (rule != null && !rule.isInPlace(file)) {
                        misplaced.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to access file during directory correction scan: %s. Reason: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path file : misplaced) {
            if (stopped) {
                return;
            }
            execute(new TaskKey(Action.FILE, file), 0);
        }
        for (Path folder : illegalFolders) {
            if (stopped) {
                return;
            }
            execute(new TaskKey(Action.FX_FOLDER, folder), 0);
        }
    }

    private void correctFile(Path file) throws IOException {
        Rule rule = ruleFor(file);
        if (rule == null || rule.isInPlace(file)) {
            return;
        }
        if (!Files.isRegularFile(file)) {
            return;
        }

        Log.info("Misplaced %s file detected: %s", rule.label(), file);
        Path target = moveWithoutOverwrite(file, rule.targetDir());
        Log.info("Corrected asset location: %s -> %s", file, target);
    }

    private Path moveWithoutOverwrite(Path source, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        String fileName = source.getFileName().toString();
        String baseName = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        for (int suffix = 0; suffix <= MAX_CONFLICT_SUFFIX; suffix++) {
            Path target = suffix == 0
                    ? targetDir.resolve(fileName)
                    : targetDir.resolve(baseName + "_" + suffix + extension);
            try {
                Files.move(source, target);
                if (suffix > 0) {
                    Log.info("File collision. Renamed duplicate file '%s' to '%s'", fileName, target.getFileName());
                }
                return target;
            } catch (FileAlreadyExistsException ignored) {
            }
        }
        throw new FileAlreadyExistsException(source.toString(), null,
                "no free name found in " + targetDir + " after " + MAX_CONFLICT_SUFFIX + " attempts");
    }

    private void cleanupIllegalFxFolder(Path folder) throws IOException {
        if (stopped || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Log.warn("Illegal subfolder detected inside a flat directory ('fx' or 'fxpacks'): %s. Moving its files out and removing it.", folder);

        List<Path> files = new ArrayList<>();
        List<Path> directoriesBottomUp = new ArrayList<>();

        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to access file while cleaning up illegal subfolder: %s. Reason: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                directoriesBottomUp.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        IOException failure = null;

        for (Path file : files) {
            try {
                correctFile(file);
            } catch (IOException e) {
                failure = merge(failure, e);
            }
        }

        boolean keptSomething = false;
        for (Path dir : directoriesBottomUp) {
            try {
                Files.delete(dir);
            } catch (DirectoryNotEmptyException e) {
                keptSomething = true;
            } catch (NoSuchFileException ignored) {
            } catch (IOException e) {
                failure = merge(failure, e);
            }
        }

        if (keptSomething) {
            Log.warn("Subfolder '%s' still contains files that are not recognised assets, so it was kept.", folder);
        } else if (failure == null) {
            Log.info("Removed illegal subfolder: %s", folder);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private enum Action {
        FILE,
        FX_FOLDER,
        RESCAN
    }

    private record TaskKey(Action action, Path path) {
    }

    private record Rule(String label, Path targetDir, boolean nestedAllowed) {
        boolean isInPlace(Path file) {
            if (nestedAllowed) {
                return file.startsWith(targetDir);
            }
            return targetDir.equals(file.getParent());
        }
    }
}
package net.renan.photonplugin.fixers;

import net.renan.photonplugin.Log;
import net.renan.photonplugin.WatchService;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AssetPlacementFixer {
    private static final Object LOCK = new Object();
    private static final WatchService watchService = new WatchService();
    private static final String ASSETS_RELATIVE_PATH = "run/ldlib2/assets";
    private static final String FX_PROJECTS_TARGET_RELATIVE_PATH = "fx_projects";
    private static final String FX_TARGET_RELATIVE_PATH = "photon/fx";
    private static final String TEXTURES_TARGET_RELATIVE_PATH = "ldlib2/textures";
    private static final String RESOURCES_TARGET_RELATIVE_PATH = "ldlib2/resources";
    private static final String MODELS_TARGET_RELATIVE_PATH = "ldlib2/models";
    private static volatile Path assetsRoot;
    private static volatile Path fxProjectsDir;
    private static volatile Path fxDir;
    private static volatile Path texturesDir;
    private static volatile Path resourcesDir;
    private static volatile Path modelsDir;

    private AssetPlacementFixer() {
    }

    public static void startSync(File workspaceRoot) {
        if (workspaceRoot == null) {
            Log.error("Cannot start AssetPlacementFixer: workspaceRoot is null.");
            return;
        }

        synchronized (LOCK) {
            stopSync();

            Log.info("Initializing AssetPlacementFixer WatchService...");

            assetsRoot = workspaceRoot.toPath().resolve(ASSETS_RELATIVE_PATH);
            fxProjectsDir = assetsRoot.resolve(FX_PROJECTS_TARGET_RELATIVE_PATH);
            fxDir = assetsRoot.resolve(FX_TARGET_RELATIVE_PATH);
            texturesDir = assetsRoot.resolve(TEXTURES_TARGET_RELATIVE_PATH);
            resourcesDir = assetsRoot.resolve(RESOURCES_TARGET_RELATIVE_PATH);
            modelsDir = assetsRoot.resolve(MODELS_TARGET_RELATIVE_PATH);

            try {
                Files.createDirectories(fxProjectsDir);
                Files.createDirectories(fxDir);
                Files.createDirectories(texturesDir);
                Files.createDirectories(resourcesDir);
                Files.createDirectories(modelsDir);
            } catch (IOException e) {
                Log.error("Failed to create target directories for AssetPlacementFixer.", e);
                return;
            }

            try {
                Log.info("Performing initial scan and correction of misplaced assets in: %s", assetsRoot);
                performFullCorrection();
            } catch (IOException e) {
                Log.warn("Error encountered during initial directory correction scan. Message: %s", e.getMessage());
            }

            Log.info("Registering file tree observers for: %s", assetsRoot);

            watchService.start(List.of(assetsRoot), AssetPlacementFixer::isIgnoredPath, (root, changed, kind) -> {
                switch (kind) {
                    case OVERFLOW -> {
                        Log.warn("File system events overflowed. Triggering full directory correction rescan.");
                        try {
                            performFullCorrection();
                        } catch (IOException e) {
                            Log.error("Failed to execute rescue scan after overflow.", e);
                        }
                    }
                    case CREATE, MODIFY -> handleChange(changed);
                    case DELETE -> {
                    }
                }
            });

            Log.info("AssetPlacementFixer background sync is actively watching for changes.");
        }
    }

    public static void stopSync() {
        synchronized (LOCK) {
            watchService.stop();
            Log.info("AssetPlacementFixer watcher stopped.");
        }
    }

    private static boolean isIgnoredPath(Path path) {
        return isWithin(path, fxProjectsDir);
    }

    private static void handleChange(Path changed) {
        try {
            if (changed == null || !Files.exists(changed) || isIgnoredPath(changed)) {
                return;
            }

            if (Files.isDirectory(changed)) {
                if (!changed.equals(fxDir) && isWithin(changed, fxDir)) {
                    cleanupIllegalFxSubfolder(changed);
                } else {
                    correctDirectoryTree(changed);
                }
            } else {
                correctFile(changed);
            }
        } catch (IOException e) {
            Log.error("Failed to process directory correction for: " + changed, e);
        }
    }

    private static void performFullCorrection() throws IOException {
        if (!Files.exists(assetsRoot)) {
            return;
        }

        correctDirectoryTree(assetsRoot);
        cleanupIllegalFxSubfolders();
    }

    private static void correctDirectoryTree(Path start) throws IOException {
        if (!Files.exists(start)) {
            return;
        }

        List<Path> candidates = new ArrayList<>();

        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(fxProjectsDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                candidates.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to access file during directory correction scan: %s. Reason: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path file : candidates) {
            correctFile(file);
        }
    }

    private static void correctFile(Path file) throws IOException {
        if (!Files.exists(file) || !Files.isRegularFile(file) || isIgnoredPath(file)) {
            return;
        }

        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (lower.endsWith(".fxproj")) {
            correctFxProjFile(file);
        } else if (lower.endsWith(".fx")) {
            correctFxFile(file);
        } else if (lower.endsWith(".png")) {
            correctFlatPlacement(file, texturesDir, "texture (.png)");
        } else if (lower.endsWith(".nbt")) {
            correctFlatPlacement(file, resourcesDir, "resource (.nbt)");
        } else if (lower.endsWith(".obj")) {
            correctFlatPlacement(file, modelsDir, "model (.obj)");
        } else if (lower.endsWith(".mtl")) {
            correctFlatPlacement(file, modelsDir, "model material (.mtl)");
        }
    }

    private static void correctFxFile(Path file) throws IOException {
        if (file.getParent() != null && file.getParent().equals(fxDir)) {
            return;
        }

        Log.info("Misplaced .fx file detected: %s", file);
        movePreservingNoConflict(file, fxDir);
    }

    private static void correctFxProjFile(Path file) throws IOException {
        if (file.getParent() != null && file.getParent().equals(fxProjectsDir)) {
            return;
        }

        Log.info("Misplaced .fxproj file detected: %s", file);
        movePreservingNoConflict(file, fxProjectsDir);
    }

    private static void correctFlatPlacement(Path file, Path targetRoot, String label) throws IOException {
        if (isWithin(file, targetRoot)) {
            return;
        }

        Log.info("Misplaced %s file detected: %s", label, file);
        movePreservingNoConflict(file, targetRoot);
    }

    private static void movePreservingNoConflict(Path file, Path targetDir) throws IOException {
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path target = targetDir.resolve(file.getFileName());
        if (Files.exists(target)) {
            target = resolveConflict(targetDir, file.getFileName().toString());
            Log.info("File collision. Renaming duplicate file '%s' to '%s'", file.getFileName(), target.getFileName());
        }

        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Log.info("Corrected asset location: %s -> %s", file, target.toAbsolutePath());
        } catch (IOException e) {
            Log.info("Atomic move failed, attempting standard move fallback for: %s", file);
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            Log.info("Corrected asset location (Fallback): %s -> %s", file, target.toAbsolutePath());
        }
    }

    private static Path resolveConflict(Path targetDir, String fileName) {
        String baseName = fileName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        Path candidate;
        do {
            candidate = targetDir.resolve(baseName + "_" + counter + extension);
            counter++;
        } while (Files.exists(candidate));

        return candidate;
    }

    private static boolean isWithin(Path path, Path root) {
        return root != null && (path.equals(root) || path.startsWith(root));
    }

    private static void cleanupIllegalFxSubfolders() throws IOException {
        if (!Files.exists(fxDir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fxDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    cleanupIllegalFxSubfolder(child);
                }
            }
        }
    }

    private static void cleanupIllegalFxSubfolder(Path subfolder) {
        try {
            if (!Files.exists(subfolder) || !Files.isDirectory(subfolder)) {
                return;
            }

            Log.warn("Illegal subfolder detected inside 'fx' directory: %s. Moving .fx files out and removing it.", subfolder);
            List<Path> fxFiles = new ArrayList<>();

            Files.walkFileTree(subfolder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".fx")) {
                        fxFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    Log.warn("Failed to access file while cleaning up 'fx' subfolder: %s. Reason: %s", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });

            for (Path file : fxFiles) {
                movePreservingNoConflict(file, fxDir);
            }

            deleteRecursive(subfolder);
            Log.info("Removed illegal subfolder from 'fx' directory: %s", subfolder);
        } catch (IOException e) {
            Log.error("Failed to clean up illegal subfolder inside 'fx' directory: " + subfolder, e);
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursive(child);
                }
            }
        }

        Files.deleteIfExists(path);
    }
}
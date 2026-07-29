package net.renan.photonplugin.copiers;

import net.renan.photonplugin.Log;
import net.renan.photonplugin.WatchService;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

public abstract class CopyFilesToAssetsFolderCommon {
    protected abstract String getSourceRelativePath();
    protected abstract boolean shouldExclude(Path path);

    protected static boolean isNamed(Path path, String name) {
        return path.getFileName() != null && name.equals(path.getFileName().toString());
    }

    private static final String TARGET_RELATIVE_PATH = "src/main/resources";

    private final WatchService watchService = new WatchService();
    private volatile boolean stopRequested = false;
    private volatile Path sourceRoot;
    private volatile Path targetRoot;
    private volatile Path manifestFile;
    private final Set<String> managedPaths = Collections.synchronizedSet(new LinkedHashSet<>());

    protected synchronized void start(File workspaceRoot) {
        if (workspaceRoot == null) {
            Log.error("Cannot start CopyFilesToAssetsFolderCommon: workspaceRoot is null.");
            return;
        }

        stop();

        Log.info("Initializing Assets WatchService observer...");

        sourceRoot = workspaceRoot.toPath().resolve(getSourceRelativePath());
        targetRoot = workspaceRoot.toPath().resolve(TARGET_RELATIVE_PATH);
        manifestFile = workspaceRoot.toPath()
                .resolve(".photon")
                .resolve("sync-manifest-" + sanitizeForFilename(getSourceRelativePath()) + ".json");

        loadManifest();
        validateManifest();

        Log.info("Performing initial verification and syncing resources directory: %s -> %s", sourceRoot, targetRoot);
        try {
            performInitialVerification(sourceRoot, targetRoot);
        } catch (Exception e) {
            Log.warn("Initial verification failed for '%s': %s", sourceRoot, e.getMessage());
        } finally {
            saveManifest();
        }

        Predicate<Path> watchExclusion = path -> shouldExclude(path)
                || (Files.isSymbolicLink(path) && Files.isDirectory(path));

        watchService.start(List.of(sourceRoot), watchExclusion, (watchedRoot, changed, kind) -> {
            if (!watchedRoot.equals(sourceRoot) && !watchedRoot.startsWith(sourceRoot)) return;

            Path target = targetRoot.resolve(sourceRoot.relativize(changed));

            try {
                switch (kind) {
                    case OVERFLOW -> {
                        Log.warn("File system events overflowed. Forcing full sync for: %s", sourceRoot);
                        syncDirectory(sourceRoot, targetRoot);
                    }
                    case DELETE -> {
                        if (Files.exists(target)) {
                            if (isManaged(target)) {
                                deletePath(target);
                                unmarkManagedRecursive(target);
                            } else {
                                Log.info("Source deleted but target '%s' isn't managed by sync; leaving it in place.", target);
                            }
                        }
                    }
                    case CREATE, MODIFY -> syncDirectory(changed, target);
                }
            } catch (Exception e) {
                Log.warn("Error processing dynamic changes for '%s': %s", changed, e.getMessage());
            } finally {
                saveManifest();
            }
        });

        Log.info("Assets sync WatchService is running and watching for modifications.");
    }

    protected synchronized void stop() {
        stopRequested = true;
        watchService.stop();
        stopRequested = false;
        saveManifest();
        Log.info("Assets synchronization watcher stopped.");
    }

    private void performInitialVerification(Path source, Path target) throws IOException {
        int[] correctionsApplied = {0};
        Log.info("Verifying files synchronization: %s -> %s", source, target);
        syncDirectory(source, target, correctionsApplied);

        if (correctionsApplied[0] > 0) {
            Log.info("Directory '%s' synced with '%s' — applied %d correction(s).", target, source, correctionsApplied[0]);
        } else {
            Log.info("Directory '%s' is up-to-date with '%s'.", target, source);
        }
    }

    private void syncDirectory(Path source, Path target) throws IOException {
        syncDirectory(source, target, null);
    }

    private void syncDirectory(Path source, Path target, int[] correctionsCounter) throws IOException {
        if (stopRequested) return;

        if (shouldExclude(source)) {
            Log.info("Skipping excluded resource path: %s", source);
            return;
        }

        if (!Files.exists(source)) {
            if (Files.exists(target)) {
                if (isManaged(target)) {
                    Log.info("Source deleted. Cleaning up target path: %s", target);
                    deletePath(target);
                    unmarkManagedRecursive(target);
                    if (correctionsCounter != null) correctionsCounter[0]++;
                } else {
                    Log.info("Source deleted but target '%s' isn't managed by sync; leaving it in place.", target);
                }
            }
            return;
        }

        if (Files.isSymbolicLink(source) && Files.isDirectory(source)) {
            Log.warn("Skipping symlinked directory to avoid a potential symlink cycle: %s", source);
            return;
        }

        if (!Files.isDirectory(source)) {
            boolean requiresCopy = false;

            if (!Files.exists(target)) {
                requiresCopy = true;
            } else if (Files.isDirectory(target)) {
                Log.info("Type collision. Directory '%s' replaced by file '%s'.", target, source);
                deletePath(target);
                unmarkManagedRecursive(target);
                requiresCopy = true;
            } else {
                requiresCopy = !contentsEqual(source, target);
            }

            if (requiresCopy) {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Log.info("Copying asset file: %s -> %s", source, target);
                Files.copy(source, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                if (correctionsCounter != null) correctionsCounter[0]++;
            }
            markManaged(target);
            return;
        }

        if (Files.exists(target) && !Files.isDirectory(target)) {
            Log.info("Type collision. File '%s' replaced by directory '%s'.", target, source);
            deletePath(target);
            unmarkManagedRecursive(target);
            if (correctionsCounter != null) correctionsCounter[0]++;
        }

        if (!Files.exists(target)) {
            Log.info("Recreating sub-folder: %s", target);
            Files.createDirectories(target);
        }
        markManaged(target);

        Set<String> sourceChildren = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                if (stopRequested) return;
                if (shouldExclude(child)) continue;
                String childName = child.getFileName().toString();
                sourceChildren.add(childName);
                syncDirectory(child, target.resolve(childName), correctionsCounter);
            }
        }

        if (stopRequested) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
            for (Path child : stream) {
                if (stopRequested) return;
                if (sourceChildren.contains(child.getFileName().toString()) || shouldExclude(child)) {
                    continue;
                }
                if (isManaged(child)) {
                    Log.info("Clean up: Removing orphaned asset: %s", child);
                    deletePath(child);
                    unmarkManagedRecursive(child);
                    if (correctionsCounter != null) correctionsCounter[0]++;
                } else {
                    Log.info("Skipping '%s': not managed by this sync, leaving it untouched.", child);
                }
            }
        }
    }

    private static boolean contentsEqual(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) {
            return false;
        }
        return Files.mismatch(a, b) == -1L;
    }

    private void deletePath(Path path) throws IOException {
        if (stopRequested) return;
        if (!Files.exists(path)) return;

        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    if (stopRequested) return;
                    deletePath(child);
                }
            }
        }

        if (Files.deleteIfExists(path)) {
            Log.info("Deleted: %s", path);
        }
    }

    private String relativeKey(Path target) {
        return targetRoot.relativize(target).toString().replace(File.separatorChar, '/');
    }

    private void markManaged(Path target) {
        managedPaths.add(relativeKey(target));
    }

    private boolean isManaged(Path target) {
        return managedPaths.contains(relativeKey(target));
    }

    private void unmarkManagedRecursive(Path target) {
        String rel = relativeKey(target);
        managedPaths.removeIf(p -> p.equals(rel) || p.startsWith(rel + "/"));
    }

    private void loadManifest() {
        managedPaths.clear();
        if (manifestFile == null || !Files.exists(manifestFile)) return;
        try {
            String content = Files.readString(manifestFile);
            for (String entry : parseJsonStringArray(content)) {
                if (!entry.isEmpty()) {
                    managedPaths.add(entry);
                }
            }
            Log.info("Loaded %d managed path(s) from sync manifest: %s", managedPaths.size(), manifestFile);
        } catch (IOException e) {
            Log.warn("Failed to load sync manifest '%s': %s", manifestFile, e.getMessage());
        }
    }

    private void validateManifest() {
        if (targetRoot == null || managedPaths.isEmpty()) {
            Log.info("Manifest verification: nothing to verify (0 managed path(s)).");
            return;
        }

        List<String> stale = new ArrayList<>();
        for (String rel : new ArrayList<>(managedPaths)) {
            Path resolved;
            try {
                resolved = targetRoot.resolve(rel).normalize();
            } catch (Exception e) {
                stale.add(rel);
                continue;
            }
            if (!resolved.startsWith(targetRoot) || !Files.exists(resolved)) {
                stale.add(rel);
            }
        }

        if (!stale.isEmpty()) {
            stale.forEach(managedPaths::remove);
            Log.warn("Manifest verification: removed %d stale/invalid entr%s no longer present on disk.",
                    stale.size(), stale.size() == 1 ? "y" : "ies");
        } else {
            Log.info("Manifest verification: all %d managed path(s) are consistent with disk state.", managedPaths.size());
        }
    }

    private synchronized void saveManifest() {
        if (manifestFile == null) return;
        try {
            if (manifestFile.getParent() != null) {
                Files.createDirectories(manifestFile.getParent());
            }
            List<String> sorted = new ArrayList<>(managedPaths);
            Collections.sort(sorted);
            Files.writeString(manifestFile, toJsonStringArray(sorted));
        } catch (IOException e) {
            Log.warn("Failed to save sync manifest '%s': %s", manifestFile, e.getMessage());
        }
    }

    private static String toJsonStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < values.size(); i++) {
            sb.append("  \"").append(escapeJson(values.get(i))).append("\"");
            if (i < values.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> parseJsonStringArray(String content) {
        List<String> result = new ArrayList<>();
        if (content == null) return result;
        int i = 0;
        int len = content.length();
        while (i < len && content.charAt(i) != '[') i++;
        i++;
        StringBuilder current = null;
        boolean escaping = false;
        while (i < len) {
            char c = content.charAt(i);
            if (current == null) {
                if (c == '"') {
                    current = new StringBuilder();
                } else if (c == ']') {
                    break;
                }
            } else {
                if (escaping) {
                    switch (c) {
                        case 'n' -> current.append('\n');
                        case 'r' -> current.append('\r');
                        case 't' -> current.append('\t');
                        case '"' -> current.append('"');
                        case '\\' -> current.append('\\');
                        default -> current.append(c);
                    }
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    result.add(current.toString());
                    current = null;
                } else {
                    current.append(c);
                }
            }
            i++;
        }
        return result;
    }

    private static String sanitizeForFilename(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}
package net.renan.photonplugin.blockly;

import net.renan.photonplugin.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FXListManager {
    private static final String FX_EXTENSION = ".fx";
    private static final int FX_EXT_LENGTH = FX_EXTENSION.length();

    private static final Map<String, CopyOnWriteArrayList<String>> ENTRIES_BY_WORKSPACE = new ConcurrentHashMap<>();

    private FXListManager() {
    }

    private static String workspaceKey(File workspaceRoot) {
        if (workspaceRoot == null) {
            return null;
        }
        try {
            return workspaceRoot.getCanonicalPath();
        } catch (IOException e) {
            return workspaceRoot.getAbsolutePath();
        }
    }

    private static CopyOnWriteArrayList<String> entriesFor(File workspaceRoot) {
        String key = workspaceKey(workspaceRoot);
        if (key == null) {
            return new CopyOnWriteArrayList<>();
        }
        return ENTRIES_BY_WORKSPACE.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
    }

    public static List<String> getEntries(File workspaceRoot) {
        return Collections.unmodifiableList(entriesFor(workspaceRoot));
    }

    public static void initialize(File workspaceRoot) {
        initialize(workspaceRoot, resolveFxDir(workspaceRoot));
    }

    public static void initialize(File workspaceRoot, File fxDir) {
        if (workspaceRoot == null) {
            Log.error("Cannot initialize FXListManager: workspaceRoot is null.");
            return;
        }

        Log.info("Initializing...");

        CopyOnWriteArrayList<String> fxEntries = entriesFor(workspaceRoot);

        if (fxDir == null || !fxDir.isDirectory()) {
            Log.warn("FX directory not found or is not a directory: %s", fxDir);
            return;
        }

        File[] children = fxDir.listFiles((dir, name) -> name.endsWith(FX_EXTENSION));
        if (children == null) {
            Log.warn("Failed to list files inside FX directory.");
            return;
        }

        List<String> found = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory()) continue;
            String baseName = baseNameOf(child.getName());
            if (!baseName.isBlank()) {
                found.add(baseName);
            }
        }

        fxEntries.clear();
        fxEntries.addAll(found);
        Log.info("initialized. Loaded %d entries: %s", found.size(), found);
    }

    public static void addEntry(File workspaceRoot, File file) {
        if (file == null) {
            Log.warn("Tried to add a null file reference to FX list.");
            return;
        }
        if (file.isDirectory()) return;
        String fileName = file.getName();
        if (!fileName.endsWith(FX_EXTENSION)) return;

        String baseName = baseNameOf(fileName);
        if (!baseName.isBlank()) {
            if (entriesFor(workspaceRoot).addIfAbsent(baseName)) {
                Log.info("Added FX entry: %s", baseName);
            } else {
                Log.info("FX entry already exists, skipping: %s", baseName);
            }
        }
    }

    public static void addEntries(File workspaceRoot, List<File> files) {
        if (files == null) {
            Log.warn("Tried to add a null list of files to FX list.");
            return;
        }
        for (File f : files) addEntry(workspaceRoot, f);
    }

    public static void removeEntry(File workspaceRoot, File file) {
        if (file == null) return;
        if (file.isDirectory()) return;
        String fileName = file.getName();
        if (!fileName.endsWith(FX_EXTENSION)) return;

        String baseName = baseNameOf(fileName);
        if (entriesFor(workspaceRoot).remove(baseName)) {
            Log.info("Removed FX entry: %s", baseName);
        }
    }

    public static void removeEntries(File workspaceRoot, List<File> files) {
        if (files == null) return;
        for (File f : files) removeEntry(workspaceRoot, f);
    }

    public static void renameEntry(File workspaceRoot, File oldFile, File newFile) {
        if (oldFile == null || newFile == null) return;
        Log.info("Renaming FX entry: %s -> %s", oldFile.getName(), newFile.getName());
        removeEntry(workspaceRoot, oldFile);
        addEntry(workspaceRoot, newFile);
    }

    private static String baseNameOf(String fileName) {
        return fileName.substring(0, fileName.length() - FX_EXT_LENGTH);
    }

    private static File resolveFxDir(File workspaceRoot) {
        if (workspaceRoot == null) return null;
        return workspaceRoot.toPath().resolve("src/main/resources/assets/photon/fx").toFile();
    }
}

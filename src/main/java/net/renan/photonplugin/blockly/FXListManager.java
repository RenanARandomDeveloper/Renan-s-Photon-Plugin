package net.renan.photonplugin.blockly;

import net.renan.photonplugin.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FXListManager {
    private static final String FX_EXTENSION = ".fx";
    private static final int FX_EXT_LENGTH = FX_EXTENSION.length();
    public static final CopyOnWriteArrayList<String> fxEntries = new CopyOnWriteArrayList<>();
    private FXListManager() {}

    public static void initialize(File workspaceRoot) {
        initialize(workspaceRoot, resolveFxDir(workspaceRoot));
    }

    public static void initialize(File workspaceRoot, File fxDir) {
        if (workspaceRoot == null) {
            Log.error("Cannot initialize FXListManager: workspaceRoot is null.");
            return;
        }

        Log.info("Initializing...");

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

    public static void addEntry(File file) {
        if (file == null) {
            Log.warn("Tried to add a null file reference to FX list.");
            return;
        }
        if (file.isDirectory()) return;
        String fileName = file.getName();
        if (!fileName.endsWith(FX_EXTENSION)) return;

        String baseName = baseNameOf(fileName);
        if (!baseName.isBlank()) {
            if (fxEntries.addIfAbsent(baseName)) {
                Log.info("Added FX entry: %s", baseName);
            } else {
                Log.info("FX entry already exists, skipping: %s", baseName);
            }
        }
    }

    public static void addEntries(List<File> files) {
        if (files == null) {
            Log.warn("Tried to add a null list of files to FX list.");
            return;
        }
        for (File f : files) addEntry(f);
    }

    public static void removeEntry(File file) {
        if (file == null) return;
        if (file.isDirectory()) return;
        String fileName = file.getName();
        if (!fileName.endsWith(FX_EXTENSION)) return;

        String baseName = baseNameOf(fileName);
        if (fxEntries.remove(baseName)) {
            Log.info("Removed FX entry: %s", baseName);
        }
    }

    public static void removeEntries(List<File> files) {
        if (files == null) return;
        for (File f : files) removeEntry(f);
    }

    public static void renameEntry(File oldFile, File newFile) {
        if (oldFile == null || newFile == null) return;
        Log.info("Renaming FX entry: %s -> %s", oldFile.getName(), newFile.getName());
        removeEntry(oldFile);
        addEntry(newFile);
    }

    private static String baseNameOf(String fileName) {
        return fileName.substring(0, fileName.length() - FX_EXT_LENGTH);
    }

    private static File resolveFxDir(File workspaceRoot) {
        if (workspaceRoot == null) return null;
        return workspaceRoot.toPath().resolve("src/main/resources/assets/photon/fx").toFile();
    }
}
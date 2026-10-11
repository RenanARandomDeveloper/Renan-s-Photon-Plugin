package net.renan.photonplugin.blockly;

import net.renan.photonplugin.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FXListManager {
    private static final String FX_EXTENSION = ".fx";
    private static final int FX_EXT_LENGTH = FX_EXTENSION.length();
    private static final String FXPACK_EXTENSION = ".fxpack";
    private static final Pattern FXPACK_FX_ENTRY = Pattern.compile("^assets/([^/]+)/fx/(.+)\\.fx$");
    private static final Comparator<String> ENTRY_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final Map<String, CopyOnWriteArrayList<String>> ENTRIES_BY_WORKSPACE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, List<String>>> FXPACK_SOURCES_BY_WORKSPACE = new ConcurrentHashMap<>();

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

    private static String fileKey(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static CopyOnWriteArrayList<String> entriesFor(File workspaceRoot) {
        String key = workspaceKey(workspaceRoot);
        if (key == null) {
            Log.warn("FXListManager called with a null workspace root; ignoring the operation.");
            return null;
        }
        return ENTRIES_BY_WORKSPACE.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
    }

    private static Map<String, List<String>> fxPackSourcesFor(File workspaceRoot) {
        String key = workspaceKey(workspaceRoot);
        if (key == null) {
            Log.warn("FXListManager called with a null workspace root; ignoring the operation.");
            return null;
        }
        return FXPACK_SOURCES_BY_WORKSPACE.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    public static List<String> getEntries(File workspaceRoot) {
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(entries);
    }

    public static void initialize(File workspaceRoot) {
        initialize(workspaceRoot, resolveFxDir(workspaceRoot), resolveFxPackDir(workspaceRoot));
    }

    public static void initialize(File workspaceRoot, File fxDir) {
        initialize(workspaceRoot, fxDir, resolveFxPackDir(workspaceRoot));
    }

    public static void initialize(File workspaceRoot, File fxDir, File fxPackDir) {
        if (workspaceRoot == null) {
            Log.error("Cannot initialize FXListManager: workspaceRoot is null.");
            return;
        }
        Log.info("Initializing...");
        syncFxDir(workspaceRoot, fxDir);
        syncFxPackDir(workspaceRoot, fxPackDir);

        CopyOnWriteArrayList<String> fxEntries = entriesFor(workspaceRoot);
        Log.info("initialized. Loaded %d entries: %s", fxEntries.size(), fxEntries);
    }

    public static void syncFxDir(File workspaceRoot, File fxDir) {
        CopyOnWriteArrayList<String> fxEntries = entriesFor(workspaceRoot);
        if (fxEntries == null) return;

        List<File> fxFiles = listFilesRecursively(fxDir, FX_EXTENSION);
        if (fxFiles == null) {
            Log.warn("FX directory not found, not a directory, or failed to list: %s", fxDir);
            return;
        }

        Set<String> found = new TreeSet<>(ENTRY_ORDER);
        for (File child : fxFiles) {
            String baseName = fxBaseName(child);
            if (baseName != null) found.add(baseName);
        }

        Set<String> packSourced = packSourcedNames(workspaceRoot);
        synchronized (fxEntries) {
            fxEntries.removeIf(name -> !packSourced.contains(name) && !found.contains(name));
            for (String name : found) insertSorted(fxEntries, name);
        }
    }

    public static void syncFxPackDir(File workspaceRoot, File fxPackDir) {
        CopyOnWriteArrayList<String> fxEntries = entriesFor(workspaceRoot);
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        if (fxEntries == null || packSources == null) return;

        List<File> packFiles = listFilesRecursively(fxPackDir, FXPACK_EXTENSION);
        if (packFiles == null) {
            Log.warn("FX Pack directory not found, not a directory, or failed to list: %s", fxPackDir);
            return;
        }

        Map<String, List<String>> newPackSources = new ConcurrentHashMap<>();
        Set<String> found = new HashSet<>();
        for (File pack : packFiles) {
            List<String> packedEntries = readFxPackEntries(pack);
            String key = fileKey(pack);
            if (key != null) newPackSources.put(key, packedEntries);
            found.addAll(packedEntries);
        }

        synchronized (fxEntries) {
            Set<String> oldPackSourced = new HashSet<>();
            for (List<String> v : packSources.values()) oldPackSourced.addAll(v);
            for (String stale : oldPackSourced) {
                if (!found.contains(stale)) fxEntries.remove(stale);
            }
            for (String name : found) insertSorted(fxEntries, name);
        }

        packSources.clear();
        packSources.putAll(newPackSources);
    }

    private static Set<String> packSourcedNames(File workspaceRoot) {
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        Set<String> names = new HashSet<>();
        if (packSources != null) {
            for (List<String> v : packSources.values()) names.addAll(v);
        }
        return names;
    }

    public static void addEntry(File workspaceRoot, File file) {
        if (file == null) {
            Log.warn("Tried to add a null file reference to FX list.");
            return;
        }
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        addTo(entries, file);
    }

    public static void addEntries(File workspaceRoot, List<File> files) {
        if (files == null) {
            Log.warn("Tried to add a null list of files to FX list.");
            return;
        }
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        for (File f : files) addTo(entries, f);
    }

    public static void removeEntry(File workspaceRoot, File file) {
        if (file == null) return;
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        removeFrom(entries, file);
    }

    public static void removeEntries(File workspaceRoot, List<File> files) {
        if (files == null) return;
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        for (File f : files) removeFrom(entries, f);
    }

    public static void renameEntry(File workspaceRoot, File oldFile, File newFile) {
        if (oldFile == null || newFile == null) return;
        Log.info("Renaming FX entry: %s -> %s", oldFile.getName(), newFile.getName());

        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;

        String oldBase = fxBaseName(oldFile);
        String newBase = fxBaseName(newFile);

        synchronized (entries) {
            if (newBase != null) {
                insertSorted(entries, newBase);
            }
            if (oldBase != null && !oldBase.equals(newBase)) {
                entries.remove(oldBase);
            }
        }
    }

    public static void addFxPackEntry(File workspaceRoot, File file) {
        if (file == null) {
            Log.warn("Tried to add a null file reference to FX Pack list.");
            return;
        }
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        addFxPackTo(entries, packSources, file);
    }

    public static void addFxPackEntries(File workspaceRoot, List<File> files) {
        if (files == null) {
            Log.warn("Tried to add a null list of FX Pack files.");
            return;
        }
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        for (File f : files) addFxPackTo(entries, packSources, f);
    }

    public static void removeFxPackEntry(File workspaceRoot, File file) {
        if (file == null) return;
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        removeFxPackFrom(entries, packSources, file);
    }

    public static void removeFxPackEntries(File workspaceRoot, List<File> files) {
        if (files == null) return;
        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        for (File f : files) removeFxPackFrom(entries, packSources, f);
    }

    public static void renameFxPackEntry(File workspaceRoot, File oldFile, File newFile) {
        if (oldFile == null || newFile == null) return;
        Log.info("Renaming FX Pack: %s -> %s", oldFile.getName(), newFile.getName());

        CopyOnWriteArrayList<String> entries = entriesFor(workspaceRoot);
        if (entries == null) return;
        Map<String, List<String>> packSources = fxPackSourcesFor(workspaceRoot);
        if (packSources == null) return;

        String oldKey = fileKey(oldFile);
        List<String> previous = oldKey != null ? packSources.remove(oldKey) : null;
        List<String> current = readFxPackEntries(newFile);

        synchronized (entries) {
            if (previous != null) {
                for (String name : previous) {
                    if (!current.contains(name)) {
                        entries.remove(name);
                    }
                }
            }
            for (String name : current) {
                insertSorted(entries, name);
            }
        }

        String newKey = fileKey(newFile);
        if (newKey != null) {
            packSources.put(newKey, current);
        }
    }

    private static void addTo(CopyOnWriteArrayList<String> entries, File file) {
        String baseName = fxBaseName(file);
        if (baseName == null) return;

        if (insertSorted(entries, baseName)) {
            Log.info("Added FX entry: %s", baseName);
        } else {
            Log.info("FX entry already exists, skipping: %s", baseName);
        }
    }

    private static void removeFrom(CopyOnWriteArrayList<String> entries, File file) {
        String baseName = fxBaseName(file);
        if (baseName == null) return;

        boolean removed;
        synchronized (entries) {
            removed = entries.remove(baseName);
        }
        if (removed) {
            Log.info("Removed FX entry: %s", baseName);
        }
    }

    private static void addFxPackTo(CopyOnWriteArrayList<String> entries, Map<String, List<String>> packSources, File file) {
        if (file == null) return;
        List<String> found = readFxPackEntries(file);
        if (found.isEmpty()) {
            Log.warn("No FX entries found inside FX Pack: %s", file.getName());
        }

        synchronized (entries) {
            for (String name : found) {
                if (insertSorted(entries, name)) {
                    Log.info("Added FX entry from pack '%s': %s", file.getName(), name);
                } else {
                    Log.info("FX entry already exists, skipping: %s", name);
                }
            }
        }

        String key = fileKey(file);
        if (key != null && packSources != null) {
            packSources.put(key, found);
        }
    }

    private static void removeFxPackFrom(CopyOnWriteArrayList<String> entries, Map<String, List<String>> packSources, File file) {
        if (file == null) return;
        String key = fileKey(file);
        List<String> found = (key != null && packSources != null) ? packSources.remove(key) : null;
        if (found == null) {
            found = readFxPackEntries(file);
        }

        synchronized (entries) {
            for (String name : found) {
                if (entries.remove(name)) {
                    Log.info("Removed FX entry from pack '%s': %s", file.getName(), name);
                }
            }
        }
    }

    private static List<String> readFxPackEntries(File fxPackFile) {
        List<String> found = new ArrayList<>();
        if (fxPackFile == null || !fxPackFile.isFile()) {
            return found;
        }
        try (ZipFile zip = new ZipFile(fxPackFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (entry.isDirectory()) continue;

                String name = entry.getName().replace('\\', '/');
                Matcher matcher = FXPACK_FX_ENTRY.matcher(name);
                if (matcher.matches()) {
                    String namespace = matcher.group(1);
                    String path = matcher.group(2);
                    found.add(namespace + ":" + path);
                }
            }
        } catch (IOException e) {
            Log.warn("Failed to read FX Pack '%s': %s", fxPackFile.getName(), e.getMessage());
        }
        return found;
    }

    private static List<File> listFilesRecursively(File root, String extension) {
        if (root == null || !root.isDirectory()) return null;
        List<File> result = new ArrayList<>();
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) return null;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.push(child);
                } else if (child.getName().endsWith(extension)) {
                    result.add(child);
                }
            }
        }
        return result;
    }

    private static boolean insertSorted(CopyOnWriteArrayList<String> entries, String baseName) {
        synchronized (entries) {
            int index = Collections.binarySearch(entries, baseName, ENTRY_ORDER);
            if (index >= 0) {
                return false;
            }
            entries.add(-(index + 1), baseName);
            return true;
        }
    }

    private static String fxBaseName(File file) {
        if (file == null || file.isDirectory()) return null;
        String fileName = file.getName();
        if (!fileName.endsWith(FX_EXTENSION)) return null;

        String baseName = fileName.substring(0, fileName.length() - FX_EXT_LENGTH);
        return baseName.isBlank() ? null : baseName;
    }

    private static File resolveFxDir(File workspaceRoot) {
        if (workspaceRoot == null) return null;
        return workspaceRoot.toPath().resolve("src/main/resources/assets/photon/fx").toFile();
    }

    private static File resolveFxPackDir(File workspaceRoot) {
        if (workspaceRoot == null) return null;
        return workspaceRoot.toPath().resolve("src/main/resources/assets/photon/fxpacks").toFile();
    }
}
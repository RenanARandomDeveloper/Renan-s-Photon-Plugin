package net.renan.photonplugin.menus.resource.common.fx.virtual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class VirtualFolderStore {

    public static final String SEP = "\u0001";

    private static final String V_PREFIX = "\0virt:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File storeFile;
    private final Supplier<List<File>> allFilesSupplier;
    private final LinkedHashMap<String, List<String>> data = new LinkedHashMap<>();

    public VirtualFolderStore(File workspaceFolder, Supplier<List<File>> allFilesSupplier) {
        this.allFilesSupplier = allFilesSupplier;
        File photonDir = new File(workspaceFolder, ".photon");
        photonDir.mkdirs();
        this.storeFile = new File(photonDir, "fx_virtual_folders.json");
        load();
    }

    private static String parentPath(String fullPath) {
        int idx = fullPath.lastIndexOf(SEP);
        return idx >= 0 ? fullPath.substring(0, idx) : "";
    }

    private static String lastSegment(String fullPath) {
        int idx = fullPath.lastIndexOf(SEP);
        return idx >= 0 ? fullPath.substring(idx + 1) : fullPath;
    }

    private static boolean isDescendantOrSelf(String maybeAncestor, String path) {
        return path.equals(maybeAncestor) || path.startsWith(maybeAncestor + SEP);
    }

    public static String sanitizeSegmentName(String name) {
        return name == null ? "" : name.replace(SEP, "");
    }

    private static boolean isSafeKey(String key) {
        if (key == null) return false;
        if (key.isEmpty()) return true;
        for (String segment : key.split(SEP, -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
            if (segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0 || segment.indexOf('\0') >= 0) return false;
        }
        return true;
    }

    public boolean isVirtualDir(File f) {
        return f != null && f.getPath().startsWith(V_PREFIX);
    }

    public File virtualDirRef(String fullPath) {
        return new File(V_PREFIX + fullPath);
    }

    public String virtualDirName(File virtualDir) {
        return virtualDir.getPath().substring(V_PREFIX.length());
    }

    public String parentOf(String fullPath) {
        return parentPath(fullPath);
    }

    public String lastSegmentOf(String fullPath) {
        return lastSegment(fullPath);
    }

    public String childPath(String parentPath, String childName) {
        return parentPath.isEmpty() ? childName : parentPath + SEP + childName;
    }

    public synchronized List<File> listVirtualDirs(File currentDir) {
        String parent = (isVirtualDir(currentDir)) ? virtualDirName(currentDir) : "";
        return data.keySet().stream()
                .filter(k -> !k.isEmpty())
                .filter(k -> parentPath(k).equals(parent))
                .sorted()
                .map(this::virtualDirRef)
                .collect(Collectors.toList());
    }

    public synchronized boolean folderExists(String fullPath) {
        return data.containsKey(fullPath);
    }

    public synchronized List<File> getFilesInFolder(File virtualDir) {
        Map<String, File> byName = filesByName();
        List<File> result = new ArrayList<>();
        for (String name : data.getOrDefault(virtualDirName(virtualDir), List.of())) {
            File f = byName.get(name);
            if (f != null) result.add(f);
        }
        return result;
    }

    public synchronized List<File> getAllFilesInSubtree(File virtualDir) {
        String path = virtualDirName(virtualDir);
        Map<String, File> byName = filesByName();
        List<File> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            if (isDescendantOrSelf(path, entry.getKey())) {
                for (String name : entry.getValue()) {
                    File f = byName.get(name);
                    if (f != null) result.add(f);
                }
            }
        }
        return result;
    }

    public synchronized boolean hasDescendantFolders(File virtualDir) {
        String path = virtualDirName(virtualDir);
        return data.keySet().stream().anyMatch(k -> !k.equals(path) && isDescendantOrSelf(path, k));
    }

    public synchronized LinkedHashMap<String, List<File>> snapshotSubtree(File virtualDir) {
        String rootPath = virtualDirName(virtualDir);
        Map<String, File> byName = filesByName();
        LinkedHashMap<String, List<File>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!isDescendantOrSelf(rootPath, key)) continue;
            String relative = key.equals(rootPath) ? "" : key.substring(rootPath.length() + SEP.length());
            List<File> files = new ArrayList<>();
            for (String name : entry.getValue()) {
                File f = byName.get(name);
                if (f != null) files.add(f);
            }
            snapshot.put(relative, files);
        }
        return snapshot;
    }

    public synchronized List<File> listFilesAt(File currentDir, String extension, String filterText) {
        Map<String, File> byName = filesByName();
        List<File> result = new ArrayList<>();

        if (isVirtualDir(currentDir)) {
            for (String name : data.getOrDefault(virtualDirName(currentDir), List.of())) {
                File f = byName.get(name);
                if (f != null && matches(f, extension, filterText)) result.add(f);
            }
        } else {
            Set<String> allAssigned = getAllAssigned();
            List<String> rootNames = data.getOrDefault("", List.of());
            Set<String> rootNameSet = new HashSet<>(rootNames);
            for (String name : rootNames) {
                File f = byName.get(name);
                if (f != null && matches(f, extension, filterText)) result.add(f);
            }
            for (File f : allFilesSupplier.get()) {
                if (!allAssigned.contains(f.getName()) && !rootNameSet.contains(f.getName())
                        && matches(f, extension, filterText)) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public synchronized boolean createFolder(String fullPath) {
        String trimmed = fullPath == null ? "" : fullPath.trim();
        if (trimmed.isEmpty() || data.containsKey(trimmed)) return false;
        data.put(trimmed, new ArrayList<>());
        save();
        return true;
    }

    public synchronized boolean renameFolder(String oldFullPath, String newName) {
        String cleanName = sanitizeSegmentName(newName).trim();
        if (cleanName.isEmpty()) return false;
        String newFullPath = childPath(parentPath(oldFullPath), cleanName);
        if (newFullPath.equals(oldFullPath) || data.containsKey(newFullPath)) return false;
        reparentSubtree(oldFullPath, newFullPath);
        save();
        return true;
    }

    public synchronized boolean moveFolderInto(String sourceFullPath, String destParentPath) {
        return moveFolderIntoReturningName(sourceFullPath, destParentPath) != null;
    }

    public synchronized String moveFolderIntoReturningName(String sourceFullPath, String destParentPath) {
        if (isDescendantOrSelf(sourceFullPath, destParentPath)) return null;
        String name = lastSegment(sourceFullPath);
        if (data.containsKey(childPath(destParentPath, name))) {
            name = uniqueChildName(destParentPath, name);
        }
        String newFullPath = childPath(destParentPath, name);
        if (newFullPath.equals(sourceFullPath)) return null;
        reparentSubtree(sourceFullPath, newFullPath);
        save();
        return name;
    }

    public synchronized void deleteFolder(File virtualDir) {
        String path = virtualDirName(virtualDir);
        String parent = parentPath(path);

        List<String> ownFiles = data.remove(path);
        if (ownFiles != null && !ownFiles.isEmpty()) {
            data.computeIfAbsent(parent, k -> new ArrayList<>()).addAll(ownFiles);
        }

        data.keySet().stream()
                .filter(k -> parentPath(k).equals(path))
                .collect(Collectors.toList())
                .forEach(childKey -> reparentSubtree(childKey, childPath(parent, lastSegment(childKey))));

        save();
    }

    public synchronized void deleteFolderAndFiles(File virtualDir) {
        String path = virtualDirName(virtualDir);
        data.keySet().stream()
                .filter(k -> isDescendantOrSelf(path, k))
                .collect(Collectors.toList())
                .forEach(data::remove);
        save();
    }

    public synchronized void assignToVirtualDir(List<File> files, File dest) {
        String key = virtualDirName(dest);
        List<String> names = files.stream().map(File::getName).collect(Collectors.toList());
        data.values().forEach(l -> l.removeAll(names));
        data.computeIfAbsent(key, k -> new ArrayList<>()).addAll(names);
        save();
    }

    public synchronized void renameFile(File oldFile, File newFile) {
        String old = oldFile.getName(), nw = newFile.getName();
        for (List<String> list : data.values()) {
            int i = list.indexOf(old);
            if (i >= 0) {
                list.set(i, nw);
                save();
                return;
            }
        }
    }

    public synchronized void removeFiles(List<File> deleted) {
        if (deleted.isEmpty()) return;
        Set<String> names = deleted.stream().map(File::getName).collect(Collectors.toSet());
        data.values().forEach(l -> l.removeAll(names));
        save();
    }

    private Map<String, File> filesByName() {
        Map<String, File> byName = new LinkedHashMap<>();
        for (File f : allFilesSupplier.get()) byName.put(f.getName(), f);
        return byName;
    }

    private Set<String> getAllAssigned() {
        Set<String> all = new HashSet<>();
        data.entrySet().stream()
                .filter(e -> !e.getKey().isEmpty())
                .forEach(e -> all.addAll(e.getValue()));
        return all;
    }

    private void reparentSubtree(String oldPrefix, String newPrefix) {
        if (oldPrefix.equals(newPrefix)) return;
        List<String> keys = data.keySet().stream()
                .filter(k -> isDescendantOrSelf(oldPrefix, k))
                .collect(Collectors.toList());
        for (String oldKey : keys) {
            String newKey = newPrefix + oldKey.substring(oldPrefix.length());
            List<String> files = data.remove(oldKey);
            data.merge(newKey, files, (a, b) -> {
                a.addAll(b);
                return a;
            });
        }
    }

    private String uniqueChildName(String parentPath, String baseName) {
        String candidate = baseName;
        int counter = 1;
        while (data.containsKey(childPath(parentPath, candidate))) {
            candidate = baseName + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private boolean matches(File f, String ext, String filter) {
        return (ext == null || FileNameRules.hasExtension(f, ext))
                && (filter == null || filter.isBlank()
                || f.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT)));
    }

    private void backupCorruptStore() {
        try {
            Path backup = new File(storeFile.getParentFile(), storeFile.getName() + ".corrupt-" + System.currentTimeMillis()).toPath();
            Files.copy(storeFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            MenuLog.warn("The virtual folder store could not be read; a copy was kept at " + backup);
        } catch (Exception ex) {
            MenuLog.warn("Could not keep a copy of the unreadable virtual folder store: " + ex.getMessage());
        }
    }

    private void load() {
        if (!storeFile.exists()) return;
        try {
            String json = Files.readString(storeFile.toPath(), StandardCharsets.UTF_8);
            Type type = new TypeToken<LinkedHashMap<String, List<String>>>() {
            }.getType();
            LinkedHashMap<String, List<String>> loaded = GSON.fromJson(json, type);
            if (loaded == null) return;
            for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
                if (!isSafeKey(entry.getKey())) {
                    MenuLog.warn("Ignoring unsafe virtual folder path in " + storeFile.getName());
                    continue;
                }
                List<String> names = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (String name : entry.getValue()) {
                        if (name != null && !name.isBlank()) names.add(name);
                    }
                }
                data.put(entry.getKey(), names);
            }
        } catch (Exception e) {
            MenuLog.error("Failed to load virtual folder store from " + storeFile.getAbsolutePath(), e);
            backupCorruptStore();
        }
    }

    private void save() {
        Path target = storeFile.toPath();
        Path temp = new File(storeFile.getParentFile(), storeFile.getName() + ".tmp").toPath();
        try {
            Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            MenuLog.error("Failed to save virtual folder store to " + storeFile.getAbsolutePath(), e);
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }
}

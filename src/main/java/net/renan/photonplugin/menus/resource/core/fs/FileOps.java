package net.renan.photonplugin.menus.resource.core.fs;

import net.mcreator.io.FileIO;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileOps {

    private static final String RENAME_TEMP_PREFIX = ".photon_rename_tmp_";
    private static final Pattern RENAME_TEMP_PATTERN = Pattern.compile("^\\.photon_rename_tmp_\\d+__(.+)$");

    private FileOps() {
    }

    public static boolean isSamePathExact(File a, File b) {
        if (a == null || b == null) return a == b;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    public static boolean isSamePathIgnoreCase(File a, File b) {
        if (a == null || b == null) return a == b;
        File parentA = a.getParentFile();
        File parentB = b.getParentFile();
        String dirA = parentA != null ? parentA.getAbsolutePath() : "";
        String dirB = parentB != null ? parentB.getAbsolutePath() : "";
        if (!dirA.equalsIgnoreCase(dirB) || !a.getName().equalsIgnoreCase(b.getName())) return false;
        if (a.exists() && b.exists()) return isSameFile(a, b);
        return true;
    }

    public static boolean isSameFile(File a, File b) {
        if (a == null || b == null) return a == b;
        try {
            return a.exists() && b.exists() && Files.isSameFile(a.toPath(), b.toPath());
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    public static boolean isInside(File candidate, File ancestor) {
        if (candidate == null || ancestor == null) return false;
        try {
            Path target = ancestor.toPath().toAbsolutePath().normalize();
            Path step = candidate.toPath().toAbsolutePath().normalize();
            while (step != null) {
                if (sameLocation(step, target)) return true;
                step = step.getParent();
            }
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean sameLocation(Path a, Path b) {
        try {
            if (a.equals(b)) return true;
            return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean isLink(File file) {
        try {
            Path path = file.toPath().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(path)) return true;
            Path name = path.getFileName();
            Path parent = path.getParent();
            if (name == null || parent == null) return true;
            Path real = path.toRealPath();
            Path realName = real.getFileName();
            Path realParent = real.getParent();
            if (realName == null || realParent == null) return true;
            boolean sameName = Normalizer.normalize(realName.toString(), Normalizer.Form.NFC)
                    .equalsIgnoreCase(Normalizer.normalize(name.toString(), Normalizer.Form.NFC));
            return !(sameName && Files.isSameFile(realParent, parent));
        } catch (IOException | RuntimeException ex) {
            return true;
        }
    }

    public static boolean isSystemJunk(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.equals(".ds_store") || name.equals("thumbs.db") || name.equals("ehthumbs.db")
                || name.equals("desktop.ini") || name.equals(".directory") || name.equals(".localized");
    }

    public static File[] listWithoutSystemJunk(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        List<File> kept = new ArrayList<>(children.length);
        for (File child : children) {
            if (!(child.isFile() && isSystemJunk(child))) kept.add(child);
        }
        return kept.toArray(new File[0]);
    }

    public static void deleteSystemJunk(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && isSystemJunk(child) && !child.delete()) {
                MenuLog.warn("Could not remove system file '" + child.getAbsolutePath() + "'");
            }
        }
    }

    private static File uniqueSibling(File target, String prefix) {
        File parent = target.getParentFile();
        File candidate;
        int attempt = 0;
        do {
            candidate = new File(parent, prefix + System.nanoTime() + "_" + (attempt++));
        } while (candidate.exists());
        return candidate;
    }

    private static File renameTempFor(File src) {
        File parent = src.getParentFile();
        File candidate;
        do {
            candidate = new File(parent, RENAME_TEMP_PREFIX + System.nanoTime() + "__" + src.getName());
        } while (candidate.exists());
        return candidate;
    }

    public static boolean isOwnTemporary(File file) {
        return file.getName().startsWith(".photon_");
    }

    public static int recoverRenameTemps(File dir) {
        return recoverRenameTemps(dir, new HashSet<>());
    }

    private static int recoverRenameTemps(File dir, Set<Path> visited) {
        if (dir == null || !dir.isDirectory()) return 0;
        try {
            if (!visited.add(dir.toPath().toRealPath())) return 0;
        } catch (IOException | RuntimeException ex) {
            return 0;
        }
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int recovered = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                recovered += recoverRenameTemps(child, visited);
                continue;
            }
            Matcher matcher = RENAME_TEMP_PATTERN.matcher(child.getName());
            if (!matcher.matches()) continue;
            File original = new File(dir, matcher.group(1));
            if (original.exists()) {
                MenuLog.warn("Found leftover '" + child.getAbsolutePath() + "' but '" + original.getName() + "' already exists; leaving it untouched");
            } else if (child.renameTo(original)) {
                MenuLog.info("Recovered '" + original.getAbsolutePath() + "' from an interrupted case-only rename");
                recovered++;
            } else {
                MenuLog.warn("Could not recover '" + child.getAbsolutePath() + "'");
            }
        }
        return recovered;
    }

    public static boolean waitUntilUnlocked(File file, int attempts, long delayMillis) {
        if (file == null) return false;
        for (int i = 0; i < attempts; i++) {
            if (!file.exists()) return false;
            if (!file.canWrite()) return true;
            try (FileChannel ignored = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
                return true;
            } catch (IOException | RuntimeException ex) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return file.exists();
    }

    public static boolean renameFile(File src, File dest) {
        if (isSamePathExact(src, dest)) return true;
        if (isSamePathIgnoreCase(src, dest)) {
            File tmp = renameTempFor(src);
            if (!src.renameTo(tmp)) return false;
            if (tmp.renameTo(dest)) return true;
            if (!tmp.renameTo(src)) {
                MenuLog.error("Case-only rename of '" + src.getAbsolutePath()
                        + "' failed and the file could not be restored from its temporary name '"
                        + tmp.getAbsolutePath() + "' - the file remains under the temporary name on disk", null);
            }
            return false;
        }
        return moveFileOrDirectory(src, dest);
    }

    public static boolean replaceMove(File src, File dest) {
        if (!dest.exists() || isSamePathExact(src, dest) || isSamePathIgnoreCase(src, dest)) {
            return renameFile(src, dest);
        }
        if (src.isDirectory() != dest.isDirectory()) {
            MenuLog.warn("Refusing to replace '" + dest.getAbsolutePath() + "' with '" + src.getAbsolutePath()
                    + "': one is a file and the other a directory");
            return false;
        }
        if (src.isDirectory()) {
            File backup = uniqueSibling(dest, ".photon_replace_bak_");
            if (!dest.renameTo(backup)) return false;
            if (renameFile(src, dest)) {
                if (!deleteRecursively(backup)) {
                    MenuLog.warn("Could not remove temporary backup '" + backup.getAbsolutePath() + "'");
                }
                return true;
            }
            if (!backup.renameTo(dest)) {
                MenuLog.error("Could not restore '" + dest.getAbsolutePath() + "' from '"
                        + backup.getAbsolutePath() + "' after a failed replace", null);
            }
            return false;
        }
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException ex) {
            MenuLog.warn("Replace move failed for '" + src.getAbsolutePath()
                    + "' -> '" + dest.getAbsolutePath() + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean moveFileOrDirectory(File source, File dest) {
        if (source.isDirectory() && isInside(dest, source)) {
            MenuLog.warn("Refusing to move '" + source.getAbsolutePath() + "' into itself ('" + dest.getAbsolutePath() + "')");
            return false;
        }
        if (source.renameTo(dest)) return true;
        try {
            if (source.isDirectory()) {
                if (!dest.exists() && !dest.mkdirs()) return false;
                copyFolderContents(source, dest);
                return deleteRecursively(source);
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
                FileIO.copyFile(source, dest);
                return source.delete();
            }
        } catch (IOException | RuntimeException ex) {
            MenuLog.warn("Move fallback (copy+delete) failed for '" + source.getAbsolutePath()
                    + "' -> '" + dest.getAbsolutePath() + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean deleteRecursively(File file) {
        if (file.isDirectory() && !isLink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }

    public static void copyReplacing(File src, File dest) throws IOException {
        if (isSameFile(src, dest)) return;
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create destination directory: " + parent.getAbsolutePath());
        }
        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (InvalidPathException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    public static void replaceDirectory(File dest, IoAction fill) throws IOException {
        File backup = null;
        if (dest.exists()) {
            backup = uniqueSibling(dest, ".photon_replace_bak_");
            if (!dest.renameTo(backup)) {
                throw new IOException("Cannot replace destination: " + dest.getAbsolutePath());
            }
        }
        try {
            if (!dest.mkdirs() && !dest.isDirectory()) {
                throw new IOException("Cannot create destination directory: " + dest.getAbsolutePath());
            }
            fill.run();
        } catch (IOException | RuntimeException ex) {
            deleteRecursively(dest);
            if (backup != null && !backup.renameTo(dest)) {
                MenuLog.error("Could not restore '" + dest.getAbsolutePath() + "' from '"
                        + backup.getAbsolutePath() + "' after a failed replace", null);
            }
            throw ex;
        }
        if (backup != null && !deleteRecursively(backup)) {
            MenuLog.warn("Could not remove temporary backup '" + backup.getAbsolutePath() + "'");
        }
    }

    public static int copyDirectoryRecursively(File srcDir, File destParent) throws IOException {
        if (isInside(destParent, srcDir)) {
            throw new IOException("Destination is inside the source directory: " + srcDir.getAbsolutePath());
        }
        File destDir = new File(destParent, srcDir.getName());
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot create destination directory: " + destDir.getAbsolutePath());
        }
        int copied = 0;
        File[] entries = srcDir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    copyDirectoryRecursively(entry, destDir);
                } else {
                    FileIO.copyFile(entry, new File(destDir, entry.getName()));
                }
                copied++;
            }
        }
        return copied;
    }

    public static void copyFolderContents(File src, File dest) throws IOException {
        if (isInside(dest, src)) {
            throw new IOException("Destination is inside the source directory: " + src.getAbsolutePath());
        }
        File[] entries = src.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                copyDirectoryRecursively(entry, dest);
            } else {
                FileIO.copyFile(entry, new File(dest, entry.getName()));
            }
        }
    }

    public static void copyFolderContentsShallow(File src, File dest) throws IOException {
        File[] files = src.listFiles(File::isFile);
        if (files == null) return;
        for (File f : files) {
            FileIO.copyFile(f, new File(dest, f.getName()));
        }
    }

    @FunctionalInterface
    public interface IoAction {
        void run() throws IOException;
    }
}

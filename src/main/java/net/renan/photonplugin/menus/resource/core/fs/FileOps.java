package net.renan.photonplugin.menus.resource.core.fs;

import net.mcreator.io.FileIO;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.io.File;
import java.io.IOException;

public final class FileOps {

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
        return dirA.equalsIgnoreCase(dirB) && a.getName().equalsIgnoreCase(b.getName());
    }

    public static boolean renameFile(File src, File dest) {
        if (isSamePathExact(src, dest)) return true;
        if (isSamePathIgnoreCase(src, dest)) {
            File parent = dest.getParentFile();
            File tmp;
            int attempt = 0;
            do {
                tmp = new File(parent, ".photon_rename_tmp_" + System.nanoTime() + "_" + (attempt++));
            } while (tmp.exists());
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

    public static boolean moveFileOrDirectory(File source, File dest) {
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
        } catch (IOException ex) {
            MenuLog.warn("Move fallback (copy+delete) failed for '" + source.getAbsolutePath()
                    + "' -> '" + dest.getAbsolutePath() + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean deleteRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return dir.delete();
    }

    public static int copyDirectoryRecursively(File srcDir, File destParent) throws IOException {
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
}

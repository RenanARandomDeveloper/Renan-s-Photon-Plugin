package net.renan.photonplugin.menus.workspace.core;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public final class FileListUtils {
    private FileListUtils() {
    }

    public static List<File> existingOnly(List<File> files) {
        return files.stream().filter(File::exists).collect(Collectors.toList());
    }
}

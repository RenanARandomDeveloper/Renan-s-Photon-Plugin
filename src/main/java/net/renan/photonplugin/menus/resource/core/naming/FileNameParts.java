package net.renan.photonplugin.menus.resource.core.naming;

import java.io.File;

public record FileNameParts(String baseName, String extension) {

    public static FileNameParts of(File file) {
        return of(file.getName());
    }

    public static FileNameParts of(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0
                ? new FileNameParts(fileName.substring(0, dot), fileName.substring(dot))
                : new FileNameParts(fileName, "");
    }
}

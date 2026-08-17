package net.renan.photonplugin.menus.resource.common.fx.virtual;

import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class VirtualBrowserSupport {

    private VirtualBrowserSupport() {
    }

    public static List<File> listFlatFilesByExtension(File dir, String extension) {
        List<File> result = new ArrayList<>();
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles(f -> f.isFile() && FileNameRules.hasExtension(f, extension));
            if (files != null) result.addAll(Arrays.asList(files));
        }
        result.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static List<File> selectedVirtualFolders(VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        return browser.getSelectedFolders().stream()
                .filter(store::isVirtualDir)
                .collect(Collectors.toList());
    }
}

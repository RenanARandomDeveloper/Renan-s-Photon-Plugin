package net.renan.photonplugin.menus.resource.neo.fxpacks;

import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;

import javax.swing.*;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

final class FXPackAwareBrowserPanel extends FXBrowserPanel {

    private static final String FXPACK_EXTENSION = ".fxpack";

    private final Supplier<File> workspaceRootGetter;
    private final Supplier<File> fxPackDirGetter;
    private volatile String lastSnapshot;

    FXPackAwareBrowserPanel(List<Supplier<File>> rootSuppliers, String extension, Function<File, Icon> iconProvider,
                            Supplier<File> workspaceRootGetter, Supplier<File> fxPackDirGetter) {
        super(rootSuppliers, extension, iconProvider);
        this.workspaceRootGetter = workspaceRootGetter;
        this.fxPackDirGetter = fxPackDirGetter;
        syncIfChanged();
    }

    private static String snapshotOf(File dir) {
        if (dir == null || !dir.isDirectory()) return "";
        StringBuilder sb = new StringBuilder();
        Deque<File> stack = new ArrayDeque<>();
        stack.push(dir);
        while (!stack.isEmpty()) {
            File current = stack.pop();
            File[] children = current.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.push(child);
                } else if (child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(FXPACK_EXTENSION)) {
                    sb.append(child.getAbsolutePath()).append(':')
                            .append(child.length()).append(':')
                            .append(child.lastModified()).append('|');
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void refresh() {
        super.refresh();
        syncIfChanged();
    }

    private void syncIfChanged() {
        if (fxPackDirGetter == null || workspaceRootGetter == null) return;

        File fxPackDir = fxPackDirGetter.get();
        String snapshot = snapshotOf(fxPackDir);
        if (snapshot.equals(lastSnapshot)) return;
        lastSnapshot = snapshot;

        File workspaceRoot = workspaceRootGetter.get();
        if (workspaceRoot == null) return;
        FXListManager.syncFxPackDir(workspaceRoot, fxPackDir);
    }
}
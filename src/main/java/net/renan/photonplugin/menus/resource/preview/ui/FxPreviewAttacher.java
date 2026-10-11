package net.renan.photonplugin.menus.resource.preview.ui;

import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;
import net.renan.photonplugin.menus.resource.preview.common.render.DefaultAssetInstaller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class FxPreviewAttacher {
    private static final String RUN_ASSETS_DIR = "run/ldlib2/assets";

    private static final String RUN_ASSETS_DIR_FORGE = "run/ldlib/assets";
    private static final int SELECTION_POLL_MILLIS = 150;
    private static final double BROWSER_SHARE = 0.50;

    private FxPreviewAttacher() {
    }

    public static JSplitPane attach(Component browser, Supplier<List<File>> selectedFiles,
                                    Supplier<List<File>> assetRoots) {
        return attach(browser, selectedFiles, assetRoots, PreviewMode.NEOFORGE);
    }

    public static JSplitPane attach(Component browser, Supplier<List<File>> selectedFiles,
                                    Supplier<List<File>> assetRoots, PreviewMode mode) {
        FXPreviewPanel preview = new FXPreviewPanel(assetRoots, mode);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser, preview);
        split.setOpaque(false);
        split.setResizeWeight(BROWSER_SHARE);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        installProportionalDivider(split);
        installSelectionSync(split, new SelectionSync(preview, selectedFiles));
        return split;
    }

    public static void installProportionalDivider(JSplitPane split) {
        split.setResizeWeight(BROWSER_SHARE);
        boolean[] userMoved = {false};
        if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui && ui.getDivider() != null) {
            ui.getDivider().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    userMoved[0] = true;
                }
            });
        }
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!userMoved[0] && split.getWidth() > 0) {
                    split.setDividerLocation((int) Math.round((split.getWidth() - split.getDividerSize()) * BROWSER_SHARE));
                }
            }
        });
    }

    public static List<File> workspaceAssetRoots(File workspaceFolder) {
        return workspaceAssetRoots(workspaceFolder, PreviewMode.NEOFORGE);
    }

    public static List<File> workspaceAssetRoots(File workspaceFolder, PreviewMode mode) {
        if (workspaceFolder == null) {
            return List.of();
        }
        List<File> roots = new ArrayList<>();
        File installedDefaults = new File(workspaceFolder, DefaultAssetInstaller.INSTALLED_FOLDER_NAME);
        if (installedDefaults.isDirectory()) {
            roots.add(installedDefaults);
        }
        roots.add(new File(workspaceFolder, mode == PreviewMode.FORGE ? RUN_ASSETS_DIR_FORGE : RUN_ASSETS_DIR));
        return roots;
    }

    private static void installSelectionSync(JComponent host, SelectionSync sync) {
        Timer timer = new Timer(SELECTION_POLL_MILLIS, e -> sync.sync());
        host.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (host.isShowing()) {
                sync.sync();
                timer.start();
            } else {
                timer.stop();
                sync.reset();
            }
        });
    }

    private static final class SelectionSync {
        private final FXPreviewPanel preview;
        private final Supplier<List<File>> selectedFiles;
        private File lastTarget;
        private long lastStamp;

        SelectionSync(FXPreviewPanel preview, Supplier<List<File>> selectedFiles) {
            this.preview = preview;
            this.selectedFiles = selectedFiles;
        }

        private static File singleExistingFile(List<File> files) {
            if (files == null || files.size() != 1) {
                return null;
            }
            File file = files.get(0);
            return file != null && file.isFile() ? file : null;
        }

        private static long stampOf(File file) {
            return file == null ? 0L : file.lastModified() * 31L + file.length();
        }

        void sync() {
            File target = singleExistingFile(selectedFiles.get());
            long stamp = stampOf(target);
            if (target != null && target.equals(lastTarget) && stamp != lastStamp) {
                preview.setSelectedFile(null);
            }
            preview.setSelectedFile(target);
            lastTarget = target;
            lastStamp = stamp;
        }

        void reset() {
            lastTarget = null;
            lastStamp = 0L;
            preview.setSelectedFile(null);
        }
    }
}

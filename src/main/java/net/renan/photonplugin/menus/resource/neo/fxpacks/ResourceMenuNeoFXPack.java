package net.renan.photonplugin.menus.resource.neo.fxpacks;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserPanel;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.virtual.actions.*;
import net.renan.photonplugin.menus.resource.neo.fxpacks.usages.FxPackUsagesDialog;
import net.renan.photonplugin.menus.resource.preview.ui.FxPreviewAttacher;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.FilterBar.buildFilterBarForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ImportAction.buildImportButton;
import static net.renan.photonplugin.menus.resource.core.support.DirectoryWatcher.attachDirectoryWatcher;
import static net.renan.photonplugin.menus.resource.core.support.IconScaler.scaleIconTo;
import static net.renan.photonplugin.menus.resource.core.support.MenuToolbars.createToolBar;

public final class ResourceMenuNeoFXPack {

    private static final String FXPACK_EXTENSION = ".fxpack";

    private ResourceMenuNeoFXPack() {
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {
        return createFXPanel(mcreator, targetDirGetter, false);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter, boolean withPreview) {
        File workspaceFolder = mcreator.getWorkspace().getWorkspaceFolder();
        FxPackSync sync = new FxPackSync(workspaceFolder, targetDirGetter);
        sync.syncIfChanged();

        VirtualFolderStore store = new VirtualFolderStore(workspaceFolder, FxPackVirtualProfile.INSTANCE,
                () -> VirtualBrowserSupport.listFlatFilesByExtension(targetDirGetter.get(), FXPACK_EXTENSION));

        VirtualBrowserPanel browser = new VirtualBrowserPanel(List.of(targetDirGetter), FXPACK_EXTENSION,
                file -> {
                    try {
                        return scaleIconTo(UIRES.get("64px_photon.fxpack"), 64);
                    } catch (Exception ignored) {
                        return null;
                    }
                },
                FxPackVirtualProfile.INSTANCE
        );
        browser.setVirtualStore(store);

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildImportButton(mcreator, browser, targetDirGetter, FXPACK_EXTENSION,
                (targetDir, importedFiles) -> {
                    File currentDir = browser.getCurrentDir();
                    if (store.isVirtualDir(currentDir)) {
                        store.assignToVirtualDir(new ArrayList<>(importedFiles), currentDir);
                    }
                    sync.syncIfChanged();
                    SwingUtilities.invokeLater(browser::refresh);
                }));

        toolBar.add(VirtualCloneAction.build(mcreator, browser, store, targetDirGetter, FxPackVirtualProfile.INSTANCE));
        toolBar.add(VirtualDeleteAction.build(mcreator, browser, store, FxPackVirtualProfile.INSTANCE));
        toolBar.add(VirtualExportAction.build(mcreator, browser, store, FxPackVirtualProfile.INSTANCE));
        toolBar.add(VirtualRenameAction.build(mcreator, browser, store, FxPackVirtualProfile.INSTANCE));
        toolBar.add(VirtualMoveAction.build(mcreator, browser, store, targetDirGetter));
        toolBar.add(VirtualCreateFolderAction.build(mcreator, browser, store));
        toolBar.add(FxPackUsagesDialog.buildSearchUsagesButton(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));
        panel.add(toolBar, BorderLayout.NORTH);
        Component center = withPreview
                ? FxPreviewAttacher.attach(browser, browser::getSelectedFiles,
                () -> FxPreviewAttacher.workspaceAssetRoots(workspaceFolder))
                : browser;
        panel.add(center, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter, (root, changed, kind) -> sync.syncIfChanged());

        return panel;
    }

    private static final class FxPackSync {

        private final File workspaceFolder;
        private final Supplier<File> fxPackDirGetter;
        private String lastSnapshot;

        FxPackSync(File workspaceFolder, Supplier<File> fxPackDirGetter) {
            this.workspaceFolder = workspaceFolder;
            this.fxPackDirGetter = fxPackDirGetter;
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
                    } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(FXPACK_EXTENSION)) {
                        sb.append(child.getAbsolutePath()).append(':')
                                .append(child.length()).append(':')
                                .append(child.lastModified()).append('|');
                    }
                }
            }
            return sb.toString();
        }

        synchronized void syncIfChanged() {
            File fxPackDir = fxPackDirGetter.get();
            String snapshot = snapshotOf(fxPackDir);
            if (snapshot.equals(lastSnapshot)) return;
            lastSnapshot = snapshot;
            if (workspaceFolder == null) return;
            FXListManager.syncFxPackDir(workspaceFolder, fxPackDir);
        }
    }
}
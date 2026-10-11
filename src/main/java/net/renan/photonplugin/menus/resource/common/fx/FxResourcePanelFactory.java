package net.renan.photonplugin.menus.resource.common.fx;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.log.Log;
import net.renan.photonplugin.menus.resource.common.fx.actions.*;
import net.renan.photonplugin.menus.resource.common.fx.usages.FxUsagesDialog;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFXBrowserPanel;
import net.renan.photonplugin.menus.resource.common.fx.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.support.IconScaler;
import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;
import net.renan.photonplugin.menus.resource.preview.common.render.DefaultAssetInstaller;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewDiagnostics;
import net.renan.photonplugin.menus.resource.preview.ui.FXPreviewPanel;
import net.renan.photonplugin.menus.resource.preview.ui.FxPreviewAttacher;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.FilterBar.buildFilterBarForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ImportAction.buildImportButton;
import static net.renan.photonplugin.menus.resource.core.support.DirectoryWatcher.attachDirectoryWatcher;
import static net.renan.photonplugin.menus.resource.core.support.MenuToolbars.createToolBar;

public final class FxResourcePanelFactory {

    private static final String FX_EXTENSION = ".fx";

    private FxResourcePanelFactory() {
    }

    public static JPanel build(MCreator mcreator, Supplier<File> targetDirGetter) {
        return build(mcreator, targetDirGetter, false);
    }

    public static JPanel build(MCreator mcreator, Supplier<File> targetDirGetter, boolean enablePreview) {
        return build(mcreator, targetDirGetter, enablePreview, PreviewMode.NEOFORGE);
    }

    public static JPanel build(MCreator mcreator, Supplier<File> targetDirGetter, boolean enablePreview,
                               PreviewMode mode) {
        VirtualFolderStore store = new VirtualFolderStore(
                mcreator.getWorkspace().getWorkspaceFolder(),
                () -> VirtualBrowserSupport.listFlatFilesByExtension(targetDirGetter.get(), FX_EXTENSION));
        FXListManager.initialize(mcreator.getWorkspace().getWorkspaceFolder());

        VirtualFXBrowserPanel browser = new VirtualFXBrowserPanel(
                List.of(targetDirGetter),
                FX_EXTENSION,
                file -> {
                    try {
                        return IconScaler.scaleIconTo(UIRES.get("64px_photon.fx"), 64);
                    } catch (Exception ignored) {
                        return null;
                    }
                }
        );
        browser.setVirtualStore(store);

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildImportButton(mcreator, browser, targetDirGetter, FX_EXTENSION,
                (targetDir, importedFiles) -> {
                    File current = browser.getCurrentDir();
                    if (store.isVirtualDir(current)) store.assignToVirtualDir(importedFiles, current);
                    FXListManager.addEntries(mcreator.getWorkspace().getWorkspaceFolder(), importedFiles);
                }));
        toolBar.add(VirtualCloneAction.build(mcreator, browser, store, targetDirGetter));
        toolBar.add(VirtualDeleteAction.build(mcreator, browser, store));
        toolBar.add(VirtualExportAction.build(mcreator, browser, store));
        toolBar.add(VirtualRenameAction.build(mcreator, browser, store));
        toolBar.add(VirtualMoveAction.build(mcreator, browser, store, targetDirGetter));
        toolBar.add(VirtualCreateFolderAction.build(mcreator, browser, store));
        toolBar.add(FxUsagesDialog.buildSearchUsagesButton(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));

        panel.add(toolBar, BorderLayout.NORTH);

        if (enablePreview) {
            File defaultAssetsRoot = DefaultAssetInstaller.ensureInstalled(
                    mcreator.getWorkspace().getWorkspaceFolder(), PreviewDiagnostics.CONSOLE);
            File gameRunDir = new File(mcreator.getWorkspace().getWorkspaceFolder(), "run");
            Supplier<List<File>> assetsRootsGetter = () -> {
                File runAssetsRoot = targetDirGetter.get().getParentFile().getParentFile();
                return defaultAssetsRoot != null
                        ? List.of(runAssetsRoot, defaultAssetsRoot, gameRunDir)
                        : List.of(runAssetsRoot, gameRunDir);
            };
            FXPreviewPanel preview = new FXPreviewPanel(assetsRootsGetter, mode);
            browser.setSelectionListener(
                    selectedFiles -> preview.setSelectedFile(selectedFiles.size() == 1 ? selectedFiles.get(0) : null));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser, preview);
            split.setOpaque(false);
            split.setContinuousLayout(true);
            split.setOneTouchExpandable(true);
            split.setDividerSize(6);
            split.setBorder(BorderFactory.createEmptyBorder());
            FxPreviewAttacher.installProportionalDivider(split);
            panel.add(split, BorderLayout.CENTER);
        } else {
            panel.add(browser, BorderLayout.CENTER);
        }

        attachDirectoryWatcher(panel, browser, targetDirGetter, (root, changed, kind) -> {
            Log.bindWorkspace(mcreator.getWorkspace().getWorkspaceFolder());
            File file = changed.toFile();
            switch (kind) {
                case CREATE -> {
                    if (!file.isDirectory() && FileNameRules.hasExtension(file, FX_EXTENSION)) {
                        FXListManager.addEntry(mcreator.getWorkspace().getWorkspaceFolder(), file);
                    }
                }
                case DELETE -> {
                    if (FileNameRules.hasExtension(file, FX_EXTENSION)) {
                        FXListManager.removeEntry(mcreator.getWorkspace().getWorkspaceFolder(), file);
                        store.removeFiles(List.of(file));
                    }
                }
                case OVERFLOW ->
                        FXListManager.initialize(mcreator.getWorkspace().getWorkspaceFolder(), targetDirGetter.get());
                case MODIFY -> {
                }
            }
        });

        return panel;
    }
}
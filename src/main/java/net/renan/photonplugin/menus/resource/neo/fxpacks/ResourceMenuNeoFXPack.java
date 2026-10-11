package net.renan.photonplugin.menus.resource.neo.fxpacks;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.neo.fxpacks.usages.FxPackUsagesDialog;
import net.renan.photonplugin.menus.resource.preview.ui.FxPreviewAttacher;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.CloneAction.buildCloneButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.CreateFolderAction.buildCreateFolderButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.DeleteAction.buildDeleteButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ExportAction.buildExportButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.FilterBar.buildFilterBarForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ImportAction.buildImportButton;
import static net.renan.photonplugin.menus.resource.core.actions.MoveAction.buildMoveButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.RenameAction.buildRenameButtonForBrowser;
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

        FXBrowserPanel browser = new FXPackAwareBrowserPanel(List.of(targetDirGetter), FXPACK_EXTENSION,
                file -> {
                    try {
                        return scaleIconTo(UIRES.get("64px_photon.fxpack"), 64);
                    } catch (Exception ignored) {
                        return null;
                    }
                },
                () -> mcreator.getWorkspace().getWorkspaceFolder(),
                targetDirGetter
        );

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildImportButton(mcreator, browser, targetDirGetter, FXPACK_EXTENSION));

        toolBar.add(buildCloneButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildDeleteButtonForBrowser(mcreator, browser));
        toolBar.add(buildExportButtonForBrowser(mcreator, browser));
        toolBar.add(buildRenameButtonForBrowser(mcreator, browser));
        toolBar.add(buildMoveButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildCreateFolderButtonForBrowser(mcreator, browser));
        toolBar.add(FxPackUsagesDialog.buildSearchUsagesButton(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));
        panel.add(toolBar, BorderLayout.NORTH);
        Component center = withPreview
                ? FxPreviewAttacher.attach(browser, browser::getSelectedFiles,
                () -> FxPreviewAttacher.workspaceAssetRoots(mcreator.getWorkspace().getWorkspaceFolder()))
                : browser;
        panel.add(center, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter);

        return panel;
    }
}
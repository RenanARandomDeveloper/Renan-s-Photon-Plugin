package net.renan.photonplugin.menus.resource.neo;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.UIRES;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public final class ResourceMenuNeoFXPack {

    private static final String FXPACK_EXTENSION = ".fxpack";

    private ResourceMenuNeoFXPack() {}

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {

        FXBrowserPanel browser = new FXBrowserPanel(List.of(targetDirGetter), FXPACK_EXTENSION,
                file -> {try { return scaleIconTo(UIRES.get("64px_photon.fxpack"), 64); }
                catch (Exception ignored) { return null; }
                }
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
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter);

        return panel;
    }
}
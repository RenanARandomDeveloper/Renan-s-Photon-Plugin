package net.renan.photonplugin.menus.resource.common;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public final class ResourceMenuCommonResource {

    private static final String RESOURCE_EXTENSION = ".nbt";

    private ResourceMenuCommonResource() {}


    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDir) {
        return createFXPanel(mcreator, targetDir, false);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDir, boolean restrictOperations) {
        FXBrowserPanel browser = new FXBrowserPanel(List.of(targetDir), RESOURCE_EXTENSION, file -> {
            try { return scaleIconTo(UIRES.get("64px_photon.resource"), 64); }
            catch (Exception ignored) { return null; }
        });

        browser.setDragAndDropEnabled(!restrictOperations);

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();

        JButton importButton = buildImportButton(mcreator, browser, targetDir, RESOURCE_EXTENSION);
        if (restrictOperations) blockImportOutsideFolder(mcreator, browser, targetDir, importButton);
        toolBar.add(importButton);

        JButton cloneButton = buildCloneButtonForBrowser(mcreator, browser, targetDir);
        if (restrictOperations) blockFolderOperation(mcreator, browser, cloneButton);
        toolBar.add(cloneButton);

        JButton deleteButton = buildDeleteButtonForBrowser(mcreator, browser);
        if (restrictOperations) {
            blockFolderOperation(mcreator, browser, deleteButton);
            blockFolderOperationForDeleteKey(mcreator, browser);
        }
        toolBar.add(deleteButton);

        toolBar.add(buildExportButtonForBrowser(mcreator, browser));

        JButton renameButton = buildRenameButtonForBrowser(mcreator, browser);
        if (restrictOperations) blockFolderOperation(mcreator, browser, renameButton);
        toolBar.add(renameButton);

        if (!restrictOperations) {
            toolBar.add(buildMoveButtonForBrowser(mcreator, browser, targetDir));
            toolBar.add(buildCreateFolderButtonForBrowser(mcreator, browser));
        }

        toolBar.add(buildFilterBarForBrowser(mcreator, browser));
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDir);

        return panel;
    }

    private static void blockFolderOperation(MCreator mcreator, FXBrowserPanel browser, JButton button) {
        ActionListener[] originalListeners = button.getActionListeners();
        for (ActionListener listener : originalListeners) {
            button.removeActionListener(listener);
        }
        button.addActionListener(e -> {
            if (!browser.getSelectedFolders().isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.resource.folder.operation.disabled"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            for (ActionListener listener : originalListeners) {
                listener.actionPerformed(e);
            }
        });
    }

    private static void blockFolderOperationForDeleteKey(MCreator mcreator, FXBrowserPanel browser) {
        Action originalAction = browser.grid.getActionMap().get("photon.deleteSelected");
        if (originalAction == null) return;
        browser.grid.getActionMap().put("photon.deleteSelected", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!browser.getSelectedFolders().isEmpty()) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.resource.folder.operation.disabled"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                originalAction.actionPerformed(e);
            }
        });
    }

    private static void blockImportOutsideFolder(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDir, JButton button) {
        ActionListener[] originalListeners = button.getActionListeners();
        for (ActionListener listener : originalListeners) {
            button.removeActionListener(listener);
        }
        button.addActionListener(e -> {
            File currentDir = browser.getCurrentDir();
            File rootDir = targetDir.get();
            boolean insideFolder = currentDir != null && !currentDir.equals(rootDir);
            if (!insideFolder) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.resource.import.requires.folder"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            for (ActionListener listener : originalListeners) {
                listener.actionPerformed(e);
            }
        });
    }
}
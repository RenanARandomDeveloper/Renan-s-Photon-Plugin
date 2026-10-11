package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DeleteAction {

    private DeleteAction() {
    }

    public static JButton buildDeleteButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.delete.files"), "16px_photon.delete");
        btn.addActionListener(e -> performDeleteSelected(mcreator, browser));
        bindDeleteKey(mcreator, browser, () -> performDeleteSelected(mcreator, browser));
        return btn;
    }

    public static void bindDeleteKey(MCreator mcreator, FXBrowserPanel browser, Runnable action) {
        InputMap inputMap = browser.getGrid().getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "photon.deleteSelected");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "photon.deleteSelected");
        browser.getGrid().getActionMap().put("photon.deleteSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static void performDeleteSelected(MCreator mcreator, FXBrowserPanel browser) {
        List<File> fileTargets = browser.getSelectedFiles();
        List<File> folderTargets = browser.getSelectedFolders().stream()
                .filter(File::isDirectory)
                .collect(java.util.stream.Collectors.toList());
        if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean cancelAll = false;
        boolean deleteConfirmedFiles = false;

        if (!fileTargets.isEmpty()) {
            final String msg = MenuMessages.buildDeleteConfirmationMessage(browser, fileTargets, folderTargets);
            int choice = JOptionPane.showConfirmDialog(
                    mcreator, msg,
                    L10N.t("plugin.photon.resourcemenu.operation.warning"),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                cancelAll = true;
            } else if (choice == JOptionPane.YES_OPTION) {
                deleteConfirmedFiles = true;
            }
        }

        List<FolderDeleteDecision> decisions = new ArrayList<>();
        for (int fi = 0; fi < folderTargets.size() && !cancelAll; fi++) {
            File folder = folderTargets.get(fi);
            if (!folder.isDirectory()) continue;
            File[] contents = FileOps.listWithoutSystemJunk(folder);
            int contentCount = (contents != null) ? contents.length : 0;

            if (contentCount == 0) {
                int choice = JOptionPane.showConfirmDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.delete.folder.empty.confirm", browser.getFolderName(folder)),
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    cancelAll = true;
                } else if (choice == JOptionPane.YES_OPTION) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.DELETE_EMPTY, contents));
                }
            } else {
                boolean hasSkip = folderTargets.size() > 1;
                String optMoveContents = L10N.t("plugin.photon.resourcemenu.delete.folder.option.move.contents");
                String optDeleteAll = L10N.t("plugin.photon.resourcemenu.delete.folder.option.delete.all");
                String optSkip = L10N.t("plugin.photon.resourcemenu.delete.folder.option.skip");
                String optCancelAll = L10N.t("plugin.photon.resourcemenu.delete.folder.option.cancel");

                Object[] options = hasSkip
                        ? new Object[]{optMoveContents, optDeleteAll, optSkip, optCancelAll}
                        : new Object[]{optMoveContents, optDeleteAll, optCancelAll};

                int cancelOptIdx = options.length - 1;
                String progressPrefix = hasSkip ? "(" + (fi + 1) + " / " + folderTargets.size() + ")  " : "";

                String folderMsg = MenuMessages.buildFolderContentsPreviewMessage(progressPrefix, browser.getFolderName(folder),
                        contents != null ? Arrays.asList(contents) : List.of());

                int action = JOptionPane.showOptionDialog(
                        mcreator, folderMsg,
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[options.length - 1]);

                if (action == JOptionPane.CLOSED_OPTION || action == cancelOptIdx) {
                    cancelAll = true;
                } else if (action == 0) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.MOVE_CONTENTS, contents));
                } else if (action == 1) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.DELETE_ALL, contents));
                }
            }
        }

        if (!deleteConfirmedFiles && decisions.isEmpty()) {
            if (!cancelAll) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            }
            return;
        }

        final boolean doDeleteFiles = deleteConfirmedFiles;
        final boolean wasCancelled = cancelAll;
        int[] counts = new int[2];
        List<String> failedNames = new java.util.concurrent.CopyOnWriteArrayList<>();

        browser.beginInternalMutation();
        EdtSupport.runFileTaskInBackground(mcreator, () -> {
            if (doDeleteFiles) {
                for (File f : fileTargets) {
                    if (f.delete()) counts[0]++;
                    else failedNames.add(f.getName());
                }
            }
            for (FolderDeleteDecision decision : decisions) {
                File folder = decision.folder();
                switch (decision.action()) {
                    case DELETE_EMPTY -> {
                        FileOps.deleteSystemJunk(folder);
                        if (folder.delete()) counts[1]++;
                        else failedNames.add(browser.getFolderName(folder));
                    }
                    case MOVE_CONTENTS -> {
                        File parent = folder.getParentFile();
                        int failed = 0;
                        File[] contents = decision.contents();
                        if (contents != null) {
                            for (File child : contents) {
                                File dest = new File(parent, child.getName());
                                if (dest.exists() || !FileOps.moveFileOrDirectory(child, dest)) failed++;
                            }
                        }
                        if (failed > 0) {
                            int finalFailed = failed;
                            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.delete.move.contents.partial.fail", finalFailed, browser.getFolderName(folder)),
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                        }
                        File[] remaining = FileOps.listWithoutSystemJunk(folder);
                        if (remaining == null || remaining.length == 0) {
                            FileOps.deleteSystemJunk(folder);
                            if (folder.delete()) counts[1]++;
                        }
                    }
                    case DELETE_ALL -> {
                        if (FileOps.deleteRecursively(folder)) {
                            counts[1]++;
                        } else {
                            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.folder.delete.error") + "\n" + browser.getFolderName(folder),
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                        }
                    }
                }
            }
        }, () -> {
            try {
                if (counts[0] + counts[1] > 0) {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.deleted")
                            + MenuMessages.buildFailureSummary(failedNames));
                } else if (!failedNames.isEmpty()) {
                    JOptionPane.showMessageDialog(mcreator, MenuMessages.buildFailureSummary(failedNames).trim(),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                } else if (!wasCancelled) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
    }

    private enum FolderDeleteAction {DELETE_EMPTY, MOVE_CONTENTS, DELETE_ALL}

    private record FolderDeleteDecision(File folder, FolderDeleteAction action, File[] contents) {
    }
}

package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.BackupService;
import net.renan.photonplugin.backup.core.WorkspaceFolders;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class BackupMenuDialog {
    private static final int MIN_BUTTON_WIDTH = 220;

    private BackupMenuDialog() {
    }

    public static void show(MCreator mcreator) {
        File workspaceFolder = WorkspaceFolders.resolve(mcreator);
        if (workspaceFolder == null) {
            JOptionPane.showMessageDialog(mcreator,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        BackupService backup = BackupService.forWorkspace(workspaceFolder);

        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.backup.title"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JButton backupNowButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.now"),
                PhotonTheme.ButtonVariant.PRIMARY);
        JButton exportButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.export"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton restoreButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.restore"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton deleteButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.delete"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton openFolderButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.open_folder"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton closeButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.close"),
                PhotonTheme.ButtonVariant.NEUTRAL);

        equalizeWidths(backupNowButton, exportButton, restoreButton, deleteButton, openFolderButton);

        JButton[] allButtons = {backupNowButton, exportButton, restoreButton, deleteButton, openFolderButton,
                closeButton};

        backupNowButton.addActionListener(e -> performManualBackup(dialog, backup, allButtons));
        exportButton.addActionListener(e -> BackupExportDialog.show(dialog, backup));
        restoreButton.addActionListener(e -> BackupRestoreDialog.show(dialog, backup));
        deleteButton.addActionListener(e -> BackupDeleteDialog.show(dialog, backup));
        openFolderButton.addActionListener(e -> openBackupsFolder(dialog, backup, allButtons));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel createSection = PhotonTheme.card();
        createSection.setLayout(new BoxLayout(createSection, BoxLayout.Y_AXIS));
        createSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        createSection.add(PhotonTheme.heading(L10N.t("plugin.photon.workspacemenu.backup.section.create")));
        createSection.add(DialogUtils.withCaption(backupNowButton,
                L10N.t("plugin.photon.workspacemenu.backup.now.caption")));

        JPanel manageSection = PhotonTheme.card();
        manageSection.setLayout(new BoxLayout(manageSection, BoxLayout.Y_AXIS));
        manageSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        manageSection.add(PhotonTheme.heading(L10N.t("plugin.photon.workspacemenu.backup.section.manage")));
        manageSection.add(DialogUtils.withCaption(exportButton,
                L10N.t("plugin.photon.workspacemenu.backup.export.caption")));
        manageSection.add(Box.createVerticalStrut(6));
        manageSection.add(DialogUtils.withCaption(restoreButton,
                L10N.t("plugin.photon.workspacemenu.backup.restore.caption")));
        manageSection.add(Box.createVerticalStrut(6));
        manageSection.add(DialogUtils.withCaption(deleteButton,
                L10N.t("plugin.photon.workspacemenu.backup.delete.caption")));
        manageSection.add(Box.createVerticalStrut(6));
        manageSection.add(DialogUtils.withCaption(openFolderButton,
                L10N.t("plugin.photon.workspacemenu.backup.open_folder.caption")));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        body.add(createSection);
        body.add(Box.createVerticalStrut(14));
        body.add(manageSection);

        JPanel bottomPanel = DialogUtils.bottomButtonRow(closeButton);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 14, 16));

        JPanel content = new JPanel(new BorderLayout());
        content.add(body, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        DialogUtils.setDefaultButton(dialog, closeButton);
        dialog.pack();
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private static void equalizeWidths(JButton... buttons) {
        int maxWidth = MIN_BUTTON_WIDTH;
        for (JButton button : buttons) {
            maxWidth = Math.max(maxWidth, button.getPreferredSize().width);
        }
        for (JButton button : buttons) {
            Dimension size = new Dimension(maxWidth, button.getPreferredSize().height);
            button.setPreferredSize(size);
            button.setMaximumSize(size);
        }
    }

    private static void openBackupsFolder(Window owner, BackupService backup, JButton[] buttonsToDisable) {
        File backupsRootFolder = backup.getBackupsRootFolder();
        if (backupsRootFolder == null || !backupsRootFolder.exists()) {
            JOptionPane.showMessageDialog(owner,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        SwingWorkers.runInBackground(owner, buttonsToDisable, () -> {
            Desktop.getDesktop().open(backupsRootFolder);
            return null;
        }, ignored -> {
        }, ex -> {
            Log.error("Failed to open backups folder.", ex);
            JOptionPane.showMessageDialog(owner,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.failure", ex.getMessage()),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.ERROR_MESSAGE);
        });
    }

    private static void performManualBackup(Window owner, BackupService backup, JButton[] buttonsToDisable) {
        SwingWorkers.runInBackground(owner, buttonsToDisable, backup::createManualBackup,
                created -> {
                    String location = created != null ? created.getAbsolutePath() : "";
                    Log.info("Manual backup created at %s", location);
                    JOptionPane.showMessageDialog(owner,
                            L10N.t("plugin.photon.workspacemenu.backup.now.success", location),
                            L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.INFORMATION_MESSAGE);
                },
                ex -> {
                    Log.error("Failed to create manual backup.", ex);
                    JOptionPane.showMessageDialog(owner,
                            L10N.t("plugin.photon.workspacemenu.backup.now.failure", ex.getMessage()),
                            L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.ERROR_MESSAGE);
                });
    }
}
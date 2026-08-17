package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.BackupService;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public final class BackupRestoreDialog {
    private BackupRestoreDialog() {
    }

    public static void show(Window owner, BackupService backup) {
        List<File> manualBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.MANUAL));
        List<File> automaticBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.AUTOMATIC));

        BackupListSelectionPanel picker = BackupListSelectionPanel.singleSelect(manualBackups, automaticBackups);
        if (!picker.hasAnyBackups()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.restore.none_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.restore"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.restore"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JButton restoreButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.restore.confirm"));
        JButton cancelButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        restoreButton.setEnabled(false);
        picker.onSelectionChange(() -> restoreButton.setEnabled(picker.getSingleSelectedFile() != null));

        cancelButton.addActionListener(e -> dialog.dispose());
        restoreButton.addActionListener(e -> confirmAndRestore(dialog, backup, picker.getSingleSelectedFile(),
                restoreButton, cancelButton));

        picker.onDoubleClick(selected -> confirmAndRestore(dialog, backup, selected, restoreButton, cancelButton));

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(picker.getComponent(), BorderLayout.CENTER);
        content.add(DialogUtils.bottomButtonRow(cancelButton, restoreButton), BorderLayout.SOUTH);

        dialog.setContentPane(content);

        DialogUtils.setDefaultButton(dialog, cancelButton);
        dialog.pack();
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void confirmAndRestore(JDialog dialog, BackupService backup, File selected,
                                          JButton restoreButton, JButton cancelButton) {
        if (selected == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(dialog,
                L10N.t("plugin.photon.workspacemenu.backup.restore.confirm_message", selected.getName()),
                L10N.t("plugin.photon.workspacemenu.backup.restore"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorkers.runInBackground(dialog, new JButton[] { restoreButton, cancelButton },
                () -> {
                    backup.restoreBackup(selected);
                    return selected;
                },
                restored -> {
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.restore.success"),
                            L10N.t("plugin.photon.workspacemenu.backup.restore"), JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                },
                ex -> {
                    Log.error("Failed to restore backup.", ex);
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.restore.failure", ex.getMessage()),
                            L10N.t("plugin.photon.workspacemenu.backup.restore"), JOptionPane.ERROR_MESSAGE);
                });
    }
}

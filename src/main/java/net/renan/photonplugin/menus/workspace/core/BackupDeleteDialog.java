package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.BackupService;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

public final class BackupDeleteDialog {
    private BackupDeleteDialog() {
    }

    public static void show(Window owner, BackupService backup) {
        List<File> manualBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.MANUAL));
        List<File> automaticBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.AUTOMATIC));

        BackupListSelectionPanel picker = BackupListSelectionPanel.multiSelect(manualBackups, automaticBackups);
        if (!picker.hasAnyBackups()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.delete.none_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.delete.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.delete.title"),
                Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JLabel selectionLabel = DialogUtils.mutedCaption();

        JButton deleteButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.delete.confirm"),
                PhotonTheme.ButtonVariant.DANGER);
        JButton cancelButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.close"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        deleteButton.setEnabled(false);

        Runnable updateSelectionState = () -> {
            int selectedCount = picker.getSelectedFiles().size();
            deleteButton.setEnabled(selectedCount > 0);
            selectionLabel.setText(L10N.t("plugin.photon.workspacemenu.list.selection_count", selectedCount,
                    picker.getTotalCount()));
        };
        picker.onSelectionChange(updateSelectionState);
        updateSelectionState.run();

        cancelButton.addActionListener(e -> dialog.dispose());
        deleteButton.addActionListener(
                e -> confirmAndDelete(dialog, backup, picker.getSelectedFiles(), deleteButton, cancelButton));
        picker.onDoubleClick(
                file -> confirmAndDelete(dialog, backup, List.of(file), deleteButton, cancelButton));

        Runnable triggerDeleteIfEnabled = () -> {
            if (deleteButton.isEnabled()) {
                deleteButton.doClick();
            }
        };
        DialogUtils.bindShortcut(picker.getComponent(), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                triggerDeleteIfEnabled);
        DialogUtils.bindShortcut(picker.getComponent(), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                triggerDeleteIfEnabled);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(selectionLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(picker.getComponent(), BorderLayout.CENTER);
        content.add(topPanel, BorderLayout.NORTH);
        content.add(DialogUtils.bottomButtonRow(cancelButton, deleteButton), BorderLayout.SOUTH);

        dialog.setContentPane(content);
        DialogUtils.setDefaultButton(dialog, cancelButton);
        dialog.pack();
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void confirmAndDelete(JDialog dialog, BackupService backup, List<File> selected,
                                         JButton deleteButton, JButton cancelButton) {
        if (selected.isEmpty()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(dialog,
                L10N.t("plugin.photon.workspacemenu.backup.delete.confirm_message", selected.size()),
                L10N.t("plugin.photon.workspacemenu.backup.delete.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorkers.runInBackground(dialog, new JButton[]{deleteButton, cancelButton},
                () -> backup.deleteBackups(selected),
                deletedCount -> {
                    Log.info("Deleted %d backup(s) via the Photon menu.", deletedCount);
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.delete.success", deletedCount),
                            L10N.t("plugin.photon.workspacemenu.backup.delete.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                },
                ex -> {
                    Log.error("Failed to delete backup.", ex);
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.delete.failure", ex.getMessage()),
                            L10N.t("plugin.photon.workspacemenu.backup.delete.title"), JOptionPane.ERROR_MESSAGE);
                });
    }
}
package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.BackupService;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public final class BackupExportDialog {
    private static final String MULTI_EXPORT_FILE_NAME = "photon_backups_export.zip";

    private BackupExportDialog() {
    }

    public static void show(Window owner, BackupService backup) {
        List<File> manualBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.MANUAL));
        List<File> automaticBackups = FileListUtils.existingOnly(
                backup.listAvailableBackups(BackupService.BackupType.AUTOMATIC));

        BackupListSelectionPanel picker = BackupListSelectionPanel.multiSelect(manualBackups, automaticBackups);
        if (!picker.hasAnyBackups()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.export.none_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.export.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.export.title"),
                Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JLabel selectionLabel = DialogUtils.mutedCaption();

        JButton exportButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.backup.export.confirm"),
                PhotonTheme.ButtonVariant.PRIMARY);
        JButton cancelButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.close"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        exportButton.setEnabled(false);

        Runnable updateSelectionState = () -> {
            int selectedCount = picker.getSelectedFiles().size();
            exportButton.setEnabled(selectedCount > 0);
            selectionLabel.setText(L10N.t("plugin.photon.workspacemenu.list.selection_count", selectedCount,
                    picker.getTotalCount()));
        };
        picker.onSelectionChange(updateSelectionState);
        updateSelectionState.run();

        cancelButton.addActionListener(e -> dialog.dispose());
        exportButton.addActionListener(
                e -> exportSelected(dialog, backup, picker.getSelectedFiles(), exportButton, cancelButton));
        picker.onDoubleClick(file -> exportSelected(dialog, backup, List.of(file), exportButton, cancelButton));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(selectionLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(picker.getComponent(), BorderLayout.CENTER);
        content.add(topPanel, BorderLayout.NORTH);
        content.add(DialogUtils.bottomButtonRow(cancelButton, exportButton), BorderLayout.SOUTH);

        dialog.setContentPane(content);
        DialogUtils.setDefaultButton(dialog, exportButton);
        dialog.pack();
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void exportSelected(JDialog dialog, BackupService backup, List<File> selected,
                                       JButton exportButton, JButton cancelButton) {
        if (selected.isEmpty()) {
            return;
        }

        boolean singleFile = selected.size() == 1;
        String suggestedName = singleFile
                ? selected.get(0).getName().replaceAll("(?i)\\.zip$", "") + ".zip"
                : MULTI_EXPORT_FILE_NAME;

        File destination = ExportFileChooser.chooseDestination(dialog,
                L10N.t("plugin.photon.workspacemenu.backup.export.choose_destination"), suggestedName, "zip");
        if (destination == null) {
            return;
        }

        SwingWorkers.runInBackground(dialog, new JButton[]{exportButton, cancelButton},
                () -> {
                    backup.exportBackups(selected, destination);
                    return destination;
                },
                exported -> {
                    Log.info("Exported %d backup(s) to %s", selected.size(), exported.getAbsolutePath());
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.export.success", exported.getAbsolutePath()),
                            L10N.t("plugin.photon.workspacemenu.backup.export.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                },
                ex -> {
                    Log.error("Failed to export backup.", ex);
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.backup.export.failure", ex.getMessage()),
                            L10N.t("plugin.photon.workspacemenu.backup.export.title"), JOptionPane.ERROR_MESSAGE);
                });
    }
}
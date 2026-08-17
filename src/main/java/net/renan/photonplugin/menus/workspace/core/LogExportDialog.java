package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public final class LogExportDialog {
    private LogExportDialog() {
    }

    public static void show(Window owner, File workspaceFolder) {
        List<File> availableLogs = FileListUtils.existingOnly(Log.listAvailableLogFilesFor(workspaceFolder));

        if (availableLogs.isEmpty()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.log.export.none_found"),
                    L10N.t("plugin.photon.workspacemenu.log.export.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File activeLogFile = Log.getLogFileFor(workspaceFolder);
        LogListSelectionPanel picker = LogListSelectionPanel.multiSelect(availableLogs, activeLogFile);

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.log.export.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JLabel selectionLabel = DialogUtils.mutedCaption();

        JButton exportButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.confirm"));
        JButton cancelButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
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
                e -> exportLogs(dialog, workspaceFolder, picker.getSelectedFiles(), exportButton, cancelButton));
        picker.onDoubleClick(
                file -> exportLogs(dialog, workspaceFolder, List.of(file), exportButton, cancelButton));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(selectionLabel, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
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

    private static void exportLogs(JDialog dialog, File workspaceFolder, List<File> selected, JButton exportButton,
                                   JButton cancelButton) {
        if (selected.isEmpty()) {
            return;
        }

        boolean singleFile = selected.size() == 1;
        String suggestedExtension = singleFile ? "txt" : "zip";
        String suggestedName = singleFile
                ? selected.get(0).getName().replaceAll("(?i)\\.(log|zip)$", "") + ".txt"
                : "photon_logs_export.zip";

        File destination = ExportFileChooser.chooseDestination(dialog,
                L10N.t("plugin.photon.workspacemenu.log.export.choose_destination"), suggestedName,
                suggestedExtension);
        if (destination == null) {
            return;
        }

        SwingWorkers.runInBackground(dialog, new JButton[] { exportButton, cancelButton },
                () -> {
                    Log.exportLogs(selected, destination);
                    return destination;
                },
                exported -> {
                    Log.withWorkspace(workspaceFolder, () ->
                            Log.info("Exported %d log file(s) to %s", selected.size(),
                                    exported.getAbsolutePath()));
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.log.export.success", exported.getAbsolutePath()),
                            L10N.t("plugin.photon.workspacemenu.log.export.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                },
                ex -> {
                    Log.withWorkspace(workspaceFolder, () -> Log.error("Failed to export logs.", ex));
                    JOptionPane.showMessageDialog(dialog,
                            L10N.t("plugin.photon.workspacemenu.log.export.failure", ex.getMessage()),
                            L10N.t("plugin.photon.workspacemenu.log.export.title"), JOptionPane.ERROR_MESSAGE);
                });
    }
}

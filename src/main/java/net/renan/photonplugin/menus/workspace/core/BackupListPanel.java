package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.BackupService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class BackupListPanel {
    private BackupListPanel() {
    }

    public static JList<File> buildList(List<File> backups, DateTimeFormatter formatter, int selectionMode) {
        DefaultListModel<File> listModel = new DefaultListModel<>();
        for (File backup : backups) {
            listModel.addElement(backup);
        }

        JList<File> list = new JList<>(listModel);
        list.setSelectionMode(selectionMode);
        list.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));

        list.setDragEnabled(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jList, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(jList, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof File file) {
                    String createdAt = BackupService.getBackupCreationDate(file).format(formatter);
                    label.setText(file.getName() + " (" + createdAt + ")");
                }
                return label;
            }
        });
        return list;
    }

    public static JPanel wrapWithTitledSection(String titleKey, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(L10N.t(titleKey)));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
}

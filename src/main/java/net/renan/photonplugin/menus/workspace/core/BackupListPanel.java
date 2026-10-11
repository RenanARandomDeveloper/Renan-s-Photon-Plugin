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
        list.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        list.setBackground(PhotonTheme.listBackground());
        list.setSelectionBackground(PhotonTheme.accent());
        list.setSelectionForeground(Color.WHITE);

        list.setDragEnabled(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jList, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(jList, value, index, isSelected,
                        cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                label.setOpaque(true);
                if (!isSelected) {
                    label.setBackground(index % 2 == 0 ? PhotonTheme.listBackground() : PhotonTheme.zebraStripe());
                    label.setForeground(PhotonTheme.textForeground());
                }
                if (value instanceof File file) {
                    String createdAt;
                    try {
                        createdAt = BackupService.getBackupCreationDate(file).format(formatter);
                    } catch (Exception ex) {
                        createdAt = "--";
                    }
                    label.setText(file.getName() + " (" + createdAt + ")");
                }
                return label;
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (isOpaque()) {
                    PhotonTheme.paintRoundedCellBackground(g, this, getBackground());
                    setOpaque(false);
                    super.paintComponent(g);
                    setOpaque(true);
                } else {
                    super.paintComponent(g);
                }
            }
        });
        return list;
    }

    public static JPanel wrapWithTitledSection(String titleKey, JComponent component) {
        JPanel panel = PhotonTheme.roundedPanel(PhotonTheme.CARD_ARC);
        panel.setLayout(new BorderLayout(0, 6));
        panel.setBackground(PhotonTheme.cardBackground());
        panel.setBorder(BorderFactory.createCompoundBorder(
                PhotonTheme.roundedLineBorder(PhotonTheme.cardBorder(), 1, PhotonTheme.CARD_ARC),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        panel.add(PhotonTheme.heading(L10N.t(titleKey)), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
}
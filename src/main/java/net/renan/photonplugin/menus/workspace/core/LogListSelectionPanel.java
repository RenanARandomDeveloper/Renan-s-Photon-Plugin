package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public final class LogListSelectionPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final int MIN_VISIBLE_ROWS = 4;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int MARGIN_ROWS = 1;

    private final List<File> logs;
    private final DefaultListModel<File> listModel;
    private final JList<File> logList;
    private final JComponent component;

    private LogListSelectionPanel(List<File> logs, File activeLogFile, int selectionMode) {
        this.logs = logs;

        listModel = new DefaultListModel<>();
        for (File log : logs) {
            listModel.addElement(log);
        }

        logList = new JList<>(listModel);
        logList.setSelectionMode(selectionMode);
        logList.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        logList.setBackground(PhotonTheme.listBackground());
        logList.setSelectionBackground(PhotonTheme.accent());
        logList.setSelectionForeground(Color.WHITE);
        logList.setDragEnabled(false);
        DialogUtils.configureListMouseSelection(logList, logList::clearSelection);
        DialogUtils.enableWheelSelectionNavigation(logList);
        int contentRows = Math.min(logs.size(), MAX_VISIBLE_ROWS);
        logList.setVisibleRowCount(Math.max(MIN_VISIBLE_ROWS, contentRows + MARGIN_ROWS));
        logList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                label.setOpaque(true);
                if (!isSelected) {
                    label.setBackground(index % 2 == 0 ? PhotonTheme.listBackground() : PhotonTheme.zebraStripe());
                    label.setForeground(PhotonTheme.textForeground());
                }
                if (value instanceof File file) {
                    boolean isActive = file.equals(activeLogFile);
                    String createdAt;
                    try {
                        createdAt = Log.getLogCreationDate(file).format(DATE_FORMAT);
                    } catch (Exception ex) {
                        createdAt = "--";
                    }
                    String rowText = escapeHtml(file.getName()) + " (" + escapeHtml(createdAt) + ")";
                    if (isActive) {
                        String badgeColor = String.format("#%06X", PhotonTheme.badgeForeground().getRGB() & 0xFFFFFF);
                        rowText += "&nbsp;&nbsp;<font color='" + badgeColor + "'><b>"
                                + escapeHtml(L10N.t("plugin.photon.workspacemenu.log.export.active")) + "</b></font>";
                    }
                    label.setText("<html>" + rowText + "</html>");
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

        JScrollPane scrollPane = new JScrollPane(logList);
        scrollPane.setBorder(PhotonTheme.roundedLineBorder(PhotonTheme.cardBorder(), 1,
                PhotonTheme.LIST_CONTAINER_ARC));

        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        if (selectionMode != ListSelectionModel.SINGLE_SELECTION) {
            wrapper.add(buildSelectAllNoneRow(), BorderLayout.NORTH);
            DialogUtils.bindShortcut(wrapper,
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, DialogUtils.platformShortcutModifier()),
                    this::selectAll);
        }
        wrapper.add(scrollPane, BorderLayout.CENTER);
        component = wrapper;
    }

    public static LogListSelectionPanel multiSelect(List<File> logs, File activeLogFile) {
        return new LogListSelectionPanel(logs, activeLogFile, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    public static LogListSelectionPanel singleSelect(List<File> logs, File activeLogFile) {
        return new LogListSelectionPanel(logs, activeLogFile, ListSelectionModel.SINGLE_SELECTION);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public JComponent getComponent() {
        return component;
    }

    public boolean isEmpty() {
        return logs.isEmpty();
    }

    public List<File> getSelectedFiles() {
        return logList.getSelectedValuesList();
    }

    public int getTotalCount() {
        return logs.size();
    }

    public void onSelectionChange(Runnable onChange) {
        logList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onChange.run();
            }
        });
    }

    public void onDoubleClick(Consumer<File> handler) {
        BackupListSelectionPanel.bindDoubleClick(logList, handler);
    }

    public void selectAll() {
        if (!logs.isEmpty()) {
            logList.setSelectionInterval(0, logs.size() - 1);
        }
    }

    public void selectNone() {
        logList.clearSelection();
    }

    private JPanel buildSelectAllNoneRow() {
        JButton selectAllButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.list.select_all"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton selectNoneButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.list.select_none"),
                PhotonTheme.ButtonVariant.NEUTRAL);

        selectAllButton.addActionListener(e -> selectAll());
        selectNoneButton.addActionListener(e -> selectNone());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(selectAllButton);
        row.add(selectNoneButton);
        return row;
    }
}
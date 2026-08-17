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
        logList.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
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
                if (value instanceof File file) {
                    boolean isActive = activeLogFile != null && file.equals(activeLogFile);
                    String activeSuffix = isActive ? " " + L10N.t("plugin.photon.workspacemenu.log.export.active")
                            : "";
                    String createdAt = Log.getLogCreationDate(file).format(DATE_FORMAT);
                    label.setText(file.getName() + " (" + createdAt + ")" + activeSuffix);
                }
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(logList);

        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
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
        JButton selectAllButton = new JButton(L10N.t("plugin.photon.workspacemenu.list.select_all"));
        JButton selectNoneButton = new JButton(L10N.t("plugin.photon.workspacemenu.list.select_none"));

        selectAllButton.addActionListener(e -> selectAll());
        selectNoneButton.addActionListener(e -> selectNone());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(selectAllButton);
        row.add(selectNoneButton);
        return row;
    }
}

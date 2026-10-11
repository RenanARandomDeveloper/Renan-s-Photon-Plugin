package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class BackupListSelectionPanel {
    private static final DateTimeFormatter MANUAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter AUTOMATIC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MIN_VISIBLE_ROWS = 4;
    private static final int MAX_VISIBLE_ROWS = 10;
    private static final int MARGIN_ROWS = 1;
    private final List<File> manualBackups;
    private final List<File> automaticBackups;
    private final JList<File> manualList;
    private final JList<File> automaticList;
    private final JComponent component;
    private boolean crossClearGuard;

    private BackupListSelectionPanel(List<File> manualBackups, List<File> automaticBackups, int selectionMode,
                                     boolean crossClearEnabled) {
        this.manualBackups = manualBackups;
        this.automaticBackups = automaticBackups;

        manualList = BackupListPanel.buildList(manualBackups, MANUAL_DATE_FORMAT, selectionMode);
        automaticList = BackupListPanel.buildList(automaticBackups, AUTOMATIC_DATE_FORMAT, selectionMode);
        DialogUtils.configureListMouseSelection(manualList, this::selectNone);
        DialogUtils.configureListMouseSelection(automaticList, this::selectNone);
        DialogUtils.enableWheelSelectionNavigation(manualList);
        DialogUtils.enableWheelSelectionNavigation(automaticList);

        int visibleRows = clampRows(Math.max(manualBackups.size(), automaticBackups.size()));
        manualList.setVisibleRowCount(visibleRows);
        automaticList.setVisibleRowCount(visibleRows);

        if (crossClearEnabled) {
            manualList.addListSelectionListener(e -> {
                if (crossClearGuard) {
                    return;
                }
                if (manualList.getSelectedIndex() >= 0 && automaticList.getSelectedIndex() >= 0) {
                    crossClearGuard = true;
                    automaticList.clearSelection();
                    crossClearGuard = false;
                }
            });
            automaticList.addListSelectionListener(e -> {
                if (crossClearGuard) {
                    return;
                }
                if (automaticList.getSelectedIndex() >= 0 && manualList.getSelectedIndex() >= 0) {
                    crossClearGuard = true;
                    manualList.clearSelection();
                    crossClearGuard = false;
                }
            });
        }

        JPanel manualPanel = BackupListPanel.wrapWithTitledSection(
                "plugin.photon.workspacemenu.backup.export.type_manual",
                withEmptyState(manualList, manualBackups, visibleRows));
        JPanel automaticPanel = BackupListPanel.wrapWithTitledSection(
                "plugin.photon.workspacemenu.backup.export.type_automatic",
                withEmptyState(automaticList, automaticBackups, visibleRows));

        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        listsPanel.setOpaque(false);
        listsPanel.add(manualPanel);
        listsPanel.add(automaticPanel);

        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        if (selectionMode != ListSelectionModel.SINGLE_SELECTION) {
            wrapper.add(buildSelectAllNoneRow(), BorderLayout.NORTH);
            DialogUtils.bindShortcut(wrapper,
                    KeyStroke.getKeyStroke(KeyEvent.VK_A, DialogUtils.platformShortcutModifier()), this::selectAll);
        }
        wrapper.add(listsPanel, BorderLayout.CENTER);
        component = wrapper;
    }

    public static BackupListSelectionPanel singleSelect(List<File> manualBackups, List<File> automaticBackups) {
        return new BackupListSelectionPanel(manualBackups, automaticBackups, ListSelectionModel.SINGLE_SELECTION,
                true);
    }

    public static BackupListSelectionPanel multiSelect(List<File> manualBackups, List<File> automaticBackups) {
        return new BackupListSelectionPanel(manualBackups, automaticBackups,
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, false);
    }

    static void bindDoubleClick(JList<File> list, java.util.function.Consumer<File> handler) {
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle bounds = list.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(e.getPoint())) {
                    return;
                }
                handler.accept(list.getModel().getElementAt(index));
            }
        });
    }

    private static int clampRows(int itemCount) {
        if (itemCount <= 0) {
            return MIN_VISIBLE_ROWS;
        }

        int contentRows = Math.min(itemCount, MAX_VISIBLE_ROWS);
        return Math.max(MIN_VISIBLE_ROWS, contentRows + MARGIN_ROWS);
    }

    private static JComponent withEmptyState(JList<File> list, List<File> backups, int visibleRows) {
        if (!backups.isEmpty()) {
            JScrollPane scrollPane = new JScrollPane(list);
            scrollPane.setBorder(PhotonTheme.roundedLineBorder(PhotonTheme.cardBorder(), 1,
                    PhotonTheme.LIST_CONTAINER_ARC));
            return scrollPane;
        }
        JLabel emptyLabel = new JLabel(L10N.t("plugin.photon.workspacemenu.backup.list.empty"),
                SwingConstants.CENTER);
        emptyLabel.setForeground(PhotonTheme.mutedForeground());
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));

        JPanel placeholder = new JPanel(new GridBagLayout());
        placeholder.setOpaque(false);
        placeholder.add(emptyLabel);

        FontMetrics metrics = list.getFontMetrics(list.getFont());
        int rowHeight = metrics.getHeight() + 4;
        placeholder.setPreferredSize(new Dimension(220, Math.max(80, visibleRows * rowHeight)));
        return placeholder;
    }

    public JComponent getComponent() {
        return component;
    }

    public boolean hasAnyBackups() {
        return !manualBackups.isEmpty() || !automaticBackups.isEmpty();
    }

    public File getSingleSelectedFile() {
        return manualList.getSelectedValue() != null ? manualList.getSelectedValue()
                : automaticList.getSelectedValue();
    }

    public List<File> getSelectedFiles() {
        List<File> selected = new ArrayList<>(manualList.getSelectedValuesList());
        selected.addAll(automaticList.getSelectedValuesList());
        return selected;
    }

    public int getTotalCount() {
        return manualBackups.size() + automaticBackups.size();
    }

    public void selectAll() {
        if (!manualBackups.isEmpty()) {
            manualList.setSelectionInterval(0, manualBackups.size() - 1);
        }
        if (!automaticBackups.isEmpty()) {
            automaticList.setSelectionInterval(0, automaticBackups.size() - 1);
        }
    }

    public void selectNone() {
        manualList.clearSelection();
        automaticList.clearSelection();
    }

    public void onDoubleClick(java.util.function.Consumer<File> handler) {
        for (JList<File> list : List.of(manualList, automaticList)) {
            bindDoubleClick(list, handler);
        }
    }

    public void onSelectionChange(Runnable onChange) {
        manualList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onChange.run();
            }
        });
        automaticList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onChange.run();
            }
        });
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
package net.renan.photonplugin.menus.workspace;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.Log;
import net.renan.photonplugin.PluginMetadata;
import net.renan.photonplugin.backup.BackupCommon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public final class WorkspaceMenuCommon {
    private static final String MENU_NAME = "PhotonPluginMenu";
    private static final String PLUGIN_AUTHOR = "Renan";

    private static Icon scaleIconTo(Icon icon, int size) {
        if (!(icon instanceof ImageIcon imageIcon)) {
            return icon;
        }
        Image scaled = imageIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private WorkspaceMenuCommon() {
    }

    private static File resolveWorkspaceFolder(MCreator mcreator) {
        try {
            return mcreator.getFolderManager().getWorkspaceFolder();
        } catch (Exception e) {
            Log.error("Failed to resolve workspace folder for the Photon menu.", e);
            return null;
        }
    }

    public static void setupMenu(MCreator mcreator, String componentLabel, String componentVersion,
                                 String libLabel, String libVersion) {
        JMenuBar menuBar = mcreator.getMainMenuBar();
        if (menuBar == null) {
            Log.warn("Could not find MCreator's menu bar, Photon Menu was not added.");
            return;
        }

        removeMenu(mcreator);

        JMenu photonMenu = buildMenu(mcreator, componentLabel, componentVersion, libLabel, libVersion);

        int insertIndex = menuBar.getMenuCount();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && "Help".equalsIgnoreCase(String.valueOf(menu.getText()))) {
                insertIndex = i + 1;
                break;
            }
        }

        menuBar.add(photonMenu, insertIndex);

        if (menuBar instanceof net.mcreator.ui.MainMenuBar mainMenuBar) {
            mainMenuBar.refreshMenuBar();
        } else {
            menuBar.revalidate();
            menuBar.repaint();
        }

        Log.info("Photon Menu added to the workspace toolbar.");
    }

    public static void removeMenu(MCreator mcreator) {
        JMenuBar menuBar = mcreator.getMainMenuBar();
        if (menuBar == null) {
            return;
        }

        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && MENU_NAME.equals(menu.getName())) {
                menuBar.remove(menu);
                if (menuBar instanceof net.mcreator.ui.MainMenuBar mainMenuBar) {
                    mainMenuBar.refreshMenuBar();
                } else {
                    menuBar.revalidate();
                    menuBar.repaint();
                }
                Log.info("Photon Menu removed from the workspace toolbar.");
                break;
            }
        }
    }

    private static JMenu buildMenu(MCreator mcreator, String componentLabel, String componentVersion,
                                   String libLabel, String libVersion) {
        JMenu photonMenu = new JMenu(L10N.t("plugin.photon.workspacemenu.title"));
        photonMenu.setName(MENU_NAME);

        JMenuItem logItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.log"));
        logItem.addActionListener(e -> openLogWindow(mcreator));

        JMenuItem backupItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.backup.title"));
        backupItem.addActionListener(e -> openBackupWindow(mcreator));

        JMenuItem aboutItem = new JMenuItem(L10N.t("plugin.photon.workspacemenu.about"));
        aboutItem.addActionListener(
                e -> openAboutWindow(mcreator, componentLabel, componentVersion, libLabel, libVersion));

        try {
            photonMenu.setIcon(scaleIconTo(UIRES.get("32px_photon.photonplugin"), 32));
            backupItem.setIcon(scaleIconTo(UIRES.get("32px_photon.backup"), 32));
            aboutItem.setIcon(scaleIconTo(UIRES.get("32px_photon.about"), 32));
            logItem.setIcon(scaleIconTo(UIRES.get("32px_photon.log"), 32));

        } catch (Exception ignored) {
        }

        photonMenu.add(logItem);
        photonMenu.add(backupItem);
        photonMenu.add(aboutItem);

        return photonMenu;
    }

    public static void openBackupWindow(MCreator mcreator) {
        File workspaceFolder = resolveWorkspaceFolder(mcreator);
        if (workspaceFolder == null) {
            JOptionPane.showMessageDialog(mcreator,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        BackupCommon backup = BackupCommon.forWorkspace(workspaceFolder);

        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.backup.title"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JButton backupNowButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.now"));
        JButton exportButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.export"));
        JButton restoreButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.restore"));
        JButton openFolderButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.open_folder"));

        for (JButton button : new JButton[] {backupNowButton, exportButton, restoreButton, openFolderButton}) {
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setMaximumSize(new Dimension(260, button.getPreferredSize().height));
        }

        backupNowButton.addActionListener(e -> performManualBackup(dialog, backup));
        exportButton.addActionListener(e -> openExportBackupsDialog(dialog, backup));
        restoreButton.addActionListener(e -> openRestoreBackupDialog(dialog, backup));
        openFolderButton.addActionListener(e -> openBackupsFolder(dialog, backup));

        buttonsPanel.add(backupNowButton);
        buttonsPanel.add(Box.createVerticalStrut(8));
        buttonsPanel.add(exportButton);
        buttonsPanel.add(Box.createVerticalStrut(8));
        buttonsPanel.add(restoreButton);
        buttonsPanel.add(Box.createVerticalStrut(8));
        buttonsPanel.add(openFolderButton);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        closeButton.addActionListener(e -> dialog.dispose());
        bottomPanel.add(closeButton);

        JPanel content = new JPanel(new BorderLayout());
        content.add(buttonsPanel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private static void openBackupsFolder(Window owner, BackupCommon backup) {
        File backupsRootFolder = backup.getBackupsRootFolder();
        if (backupsRootFolder == null || !backupsRootFolder.exists()) {
            JOptionPane.showMessageDialog(owner,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(backupsRootFolder);
        } catch (Exception ex) {
            Log.error("Failed to open backups folder.", ex);
            JOptionPane.showMessageDialog(owner,
                    L10N.t("plugin.photon.workspacemenu.backup.open_folder.failure", ex.getMessage()),
                    L10N.t("plugin.photon.workspacemenu.backup.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void performManualBackup(Window owner, BackupCommon backup) {
        runInBackground(owner, backup::createManualBackup,
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

    private static <T> void runInBackground(Window owner, IOTask<T> task, java.util.function.Consumer<T> onSuccess,
                                             java.util.function.Consumer<IOException> onFailure) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            private IOException failure;

            @Override
            protected T doInBackground() {
                try {
                    return task.run();
                } catch (IOException e) {
                    failure = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                if (failure != null) {
                    onFailure.accept(failure);
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception e) {
                    onFailure.accept(new IOException(e));
                }
            }
        }.execute();
    }

    @FunctionalInterface
    private interface IOTask<T> {
        T run() throws IOException;
    }

    private static JList<File> buildBackupList(List<File> backups, DateTimeFormatter formatter, int selectionMode) {
        DefaultListModel<File> listModel = new DefaultListModel<>();
        for (File backup : backups) {
            listModel.addElement(backup);
        }

        JList<File> list = new JList<>(listModel);
        list.setSelectionMode(selectionMode);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jList, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(jList, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof File file) {
                    String createdAt = BackupCommon.getBackupCreationDate(file).format(formatter);
                    label.setText(file.getName() + " (" + createdAt + ")");
                }
                return label;
            }
        });
        return list;
    }

    private static JPanel wrapWithTitledSection(String titleKey, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(L10N.t(titleKey)));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    public static void openExportBackupsDialog(Window owner, BackupCommon backup) {
        List<File> manualBackups = backup.listAvailableBackups(BackupCommon.BackupType.MANUAL);
        List<File> automaticBackups = backup.listAvailableBackups(BackupCommon.BackupType.AUTOMATIC);

        if (manualBackups.isEmpty() && automaticBackups.isEmpty()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.export.none_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.export.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.export.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        DateTimeFormatter dateOnlyFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        JList<File> manualList = buildBackupList(manualBackups, dateTimeFormat,
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JList<File> automaticList = buildBackupList(automaticBackups, dateOnlyFormat,
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel manualPanel = wrapWithTitledSection("plugin.photon.workspacemenu.backup.export.type_manual",
                new JScrollPane(manualList));
        JPanel automaticPanel = wrapWithTitledSection("plugin.photon.workspacemenu.backup.export.type_automatic",
                new JScrollPane(automaticList));

        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        listsPanel.add(manualPanel);
        listsPanel.add(automaticPanel);
        listsPanel.setPreferredSize(new Dimension(520, 260));

        JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.select_all"));
        JButton selectNoneButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.select_none"));
        selectionButtonsPanel.add(selectAllButton);
        selectionButtonsPanel.add(selectNoneButton);

        selectAllButton.addActionListener(e -> {
            if (!manualBackups.isEmpty()) {
                manualList.setSelectionInterval(0, manualBackups.size() - 1);
            }
            if (!automaticBackups.isEmpty()) {
                automaticList.setSelectionInterval(0, automaticBackups.size() - 1);
            }
        });
        selectNoneButton.addActionListener(e -> {
            manualList.clearSelection();
            automaticList.clearSelection();
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.confirm"));
        JButton cancelButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        bottomPanel.add(exportButton);
        bottomPanel.add(cancelButton);

        cancelButton.addActionListener(e -> dialog.dispose());

        exportButton.addActionListener(e -> {
            List<File> selected = new ArrayList<>();
            selected.addAll(manualList.getSelectedValuesList());
            selected.addAll(automaticList.getSelectedValuesList());
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        L10N.t("plugin.photon.workspacemenu.log.export.none_selected"),
                        L10N.t("plugin.photon.workspacemenu.backup.export.title"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            String suggestedName = selected.size() == 1
                    ? selected.get(0).getName().replaceAll("(?i)\\.zip$", "") + ".zip"
                    : "photon_backups_export.zip";

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(L10N.t("plugin.photon.workspacemenu.log.export.choose_destination"));
            fileChooser.setSelectedFile(new File(suggestedName));
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP", "zip"));

            int result = fileChooser.showSaveDialog(dialog);
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File destination = fileChooser.getSelectedFile();
            if (!destination.getName().toLowerCase().endsWith(".zip")) {
                destination = new File(destination.getParentFile(), destination.getName() + ".zip");
            }

            File finalDestination = destination;
            runInBackground(dialog,
                    () -> {
                        backup.exportBackups(selected, finalDestination);
                        return finalDestination;
                    },
                    exported -> {
                        Log.info("Exported %d backup(s) to %s", selected.size(), exported.getAbsolutePath());
                        JOptionPane.showMessageDialog(dialog,
                                L10N.t("plugin.photon.workspacemenu.backup.export.success",
                                        exported.getAbsolutePath()),
                                L10N.t("plugin.photon.workspacemenu.backup.export.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    },
                    ex -> {
                        Log.error("Failed to export backups.", ex);
                        JOptionPane.showMessageDialog(dialog,
                                L10N.t("plugin.photon.workspacemenu.backup.export.failure", ex.getMessage()),
                                L10N.t("plugin.photon.workspacemenu.backup.export.title"), JOptionPane.ERROR_MESSAGE);
                    });
        });

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(listsPanel, BorderLayout.CENTER);
        content.add(selectionButtonsPanel, BorderLayout.NORTH);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    public static void openRestoreBackupDialog(Window owner, BackupCommon backup) {
        List<File> manualBackups = backup.listAvailableBackups(BackupCommon.BackupType.MANUAL);
        List<File> automaticBackups = backup.listAvailableBackups(BackupCommon.BackupType.AUTOMATIC);

        if (manualBackups.isEmpty() && automaticBackups.isEmpty()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.restore.none_found"),
                    L10N.t("plugin.photon.workspacemenu.backup.restore"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.backup.restore"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        DateTimeFormatter dateOnlyFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        JList<File> manualList = buildBackupList(manualBackups, dateTimeFormat, ListSelectionModel.SINGLE_SELECTION);
        JList<File> automaticList = buildBackupList(automaticBackups, dateOnlyFormat,
                ListSelectionModel.SINGLE_SELECTION);

        manualList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && manualList.getSelectedIndex() >= 0) {
                automaticList.clearSelection();
            }
        });
        automaticList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && automaticList.getSelectedIndex() >= 0) {
                manualList.clearSelection();
            }
        });

        JPanel manualPanel = wrapWithTitledSection("plugin.photon.workspacemenu.backup.export.type_manual",
                new JScrollPane(manualList));
        JPanel automaticPanel = wrapWithTitledSection("plugin.photon.workspacemenu.backup.export.type_automatic",
                new JScrollPane(automaticList));

        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        listsPanel.add(manualPanel);
        listsPanel.add(automaticPanel);
        listsPanel.setPreferredSize(new Dimension(520, 260));

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restoreButton = new JButton(L10N.t("plugin.photon.workspacemenu.backup.restore.confirm"));
        JButton cancelButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        bottomPanel.add(restoreButton);
        bottomPanel.add(cancelButton);

        cancelButton.addActionListener(e -> dialog.dispose());

        restoreButton.addActionListener(e -> {
            File selected = manualList.getSelectedValue() != null
                    ? manualList.getSelectedValue() : automaticList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog,
                        L10N.t("plugin.photon.workspacemenu.log.export.none_selected"),
                        L10N.t("plugin.photon.workspacemenu.backup.restore"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(dialog,
                    L10N.t("plugin.photon.workspacemenu.backup.restore.confirm_message", selected.getName()),
                    L10N.t("plugin.photon.workspacemenu.backup.restore"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            runInBackground(dialog,
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
        });

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(listsPanel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    public static void openLogWindow(MCreator mcreator) {
        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.log.title"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setText(Log.readPersistedLog());
        textArea.setCaretPosition(textArea.getDocument().getLength());

        JScrollPane scrollPane = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.refresh"));
        JButton openFolderButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.open_folder"));
        JButton exportButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export"));
        JButton closeButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        bottomPanel.add(refreshButton);
        bottomPanel.add(openFolderButton);
        bottomPanel.add(exportButton);
        bottomPanel.add(closeButton);

        long[] lastLineCount = { Log.getTotalLinesRecorded() };

        exportButton.addActionListener(e -> openExportLogsDialog(dialog));

        refreshButton.addActionListener(e -> {
            lastLineCount[0] = Log.getTotalLinesRecorded();
            textArea.setText(Log.readPersistedLog());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });

        openFolderButton.addActionListener(e -> {
            File logFile = Log.getLogFile();
            if (logFile != null && logFile.getParentFile() != null && logFile.getParentFile().exists()) {
                try {
                    Desktop.getDesktop().open(logFile.getParentFile());
                } catch (Exception ex) {
                    Log.error("Failed to open .photon log folder.", ex);
                }
            }
        });

        closeButton.addActionListener(e -> dialog.dispose());

        Timer refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                List<String> newLines = Log.getBufferedLogSince(lastLineCount[0]);

                if (newLines == null) {
                    lastLineCount[0] = Log.getTotalLinesRecorded();
                    SwingUtilities.invokeLater(() -> {
                        textArea.setText(Log.readPersistedLog());
                        textArea.setCaretPosition(textArea.getDocument().getLength());
                    });
                    return;
                }

                if (newLines.isEmpty()) {
                    return;
                }

                lastLineCount[0] += newLines.size();
                SwingUtilities.invokeLater(() -> {
                    int caret = textArea.getCaretPosition();
                    boolean wasAtEnd = caret >= textArea.getDocument().getLength() - 1;
                    for (String line : newLines) {
                        textArea.append(line);
                        textArea.append(System.lineSeparator());
                    }
                    textArea.setCaretPosition(wasAtEnd ? textArea.getDocument().getLength() : Math.min(caret,
                            textArea.getDocument().getLength()));
                });
            }
        }, 2000, 2000);

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                refreshTimer.cancel();
            }
        });

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setVisible(true);
    }

    public static void openExportLogsDialog(Window owner) {
        List<File> availableLogs = Log.listAvailableLogFiles();

        if (availableLogs.isEmpty()) {
            JOptionPane.showMessageDialog(owner, L10N.t("plugin.photon.workspacemenu.log.export.none_found"),
                    L10N.t("plugin.photon.workspacemenu.log.export.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(owner, L10N.t("plugin.photon.workspacemenu.log.export.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        File activeLogFile = Log.getLogFile();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

        DefaultListModel<File> listModel = new DefaultListModel<>();
        for (File log : availableLogs) {
            listModel.addElement(log);
        }

        JList<File> logList = new JList<>(listModel);
        logList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        logList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                if (value instanceof File file) {
                    boolean isActive = activeLogFile != null && file.getAbsolutePath()
                            .equals(activeLogFile.getAbsolutePath());
                    String activeSuffix = isActive ? " " + L10N.t("plugin.photon.workspacemenu.log.export.active")
                            : "";
                    String createdAt = Log.getLogCreationDate(file).format(dateFormat);
                    label.setText(file.getName() + " (" + createdAt + ")" + activeSuffix);
                }
                return label;
            }
        });

        JScrollPane listScrollPane = new JScrollPane(logList);
        listScrollPane.setPreferredSize(new Dimension(420, 260));

        JPanel selectionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.select_all"));
        JButton selectNoneButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.select_none"));
        selectionButtonsPanel.add(selectAllButton);
        selectionButtonsPanel.add(selectNoneButton);

        selectAllButton.addActionListener(e -> logList.setSelectionInterval(0, listModel.getSize() - 1));
        selectNoneButton.addActionListener(e -> logList.clearSelection());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton(L10N.t("plugin.photon.workspacemenu.log.export.confirm"));
        JButton cancelButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        bottomPanel.add(exportButton);
        bottomPanel.add(cancelButton);

        cancelButton.addActionListener(e -> dialog.dispose());

        exportButton.addActionListener(e -> {
            List<File> selected = logList.getSelectedValuesList();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        L10N.t("plugin.photon.workspacemenu.log.export.none_selected"),
                        L10N.t("plugin.photon.workspacemenu.log.export.title"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean singleFile = selected.size() == 1;
            String suggestedExtension = singleFile ? "txt" : "zip";
            String suggestedName = singleFile
                    ? selected.get(0).getName().replaceAll("(?i)\\.(log|zip)$", "") + ".txt"
                    : "photon_logs_export.zip";

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(L10N.t("plugin.photon.workspacemenu.log.export.choose_destination"));
            fileChooser.setSelectedFile(new File(suggestedName));
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    suggestedExtension.toUpperCase(), suggestedExtension));

            int result = fileChooser.showSaveDialog(dialog);
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File destination = fileChooser.getSelectedFile();
            if (!destination.getName().toLowerCase().endsWith("." + suggestedExtension)) {
                destination = new File(destination.getParentFile(),
                        destination.getName() + "." + suggestedExtension);
            }

            File finalDestination = destination;
            runInBackground(dialog,
                    () -> {
                        Log.exportLogs(selected, finalDestination);
                        return finalDestination;
                    },
                    exported -> {
                        Log.info("Exported %d log file(s) to %s", selected.size(), exported.getAbsolutePath());
                        JOptionPane.showMessageDialog(dialog,
                                L10N.t("plugin.photon.workspacemenu.log.export.success", exported.getAbsolutePath()),
                                L10N.t("plugin.photon.workspacemenu.log.export.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    },
                    ex -> {
                        Log.error("Failed to export logs.", ex);
                        JOptionPane.showMessageDialog(dialog,
                                L10N.t("plugin.photon.workspacemenu.log.export.failure", ex.getMessage()),
                                L10N.t("plugin.photon.workspacemenu.log.export.title"), JOptionPane.ERROR_MESSAGE);
                    });
        });

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(listScrollPane, BorderLayout.CENTER);
        content.add(selectionButtonsPanel, BorderLayout.NORTH);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    public static void openAboutWindow(MCreator mcreator, String componentLabel, String componentVersion,
                                       String libLabel, String libVersion) {
        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.about.title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(15, 15));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel iconLabel;
        try {
            iconLabel = new JLabel(scaleIconTo(UIRES.get("64px_photon.photonplugin"), 64));
        } catch (Exception ignored) {
            iconLabel = new JLabel();
        }
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        content.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Renan's Photon Plugin");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel pluginVersionLabel = new JLabel(
                L10N.t("plugin.photon.workspacemenu.about.plugin_version", PluginMetadata.getPluginVersion()));
        JLabel authorLabel = new JLabel(L10N.t("plugin.photon.workspacemenu.about.author", PLUGIN_AUTHOR));
        JLabel componentVersionLabel = new JLabel(componentLabel + ": " + componentVersion);
        JLabel libVersionLabel = new JLabel(libLabel + ": " + libVersion);

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(pluginVersionLabel);
        textPanel.add(authorLabel);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(componentVersionLabel);
        textPanel.add(libVersionLabel);

        content.add(textPanel, BorderLayout.CENTER);

        JButton closeButton = new JButton(L10N.t("plugin.photon.workspacemenu.close"));
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.add(closeButton);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(320, 180));
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }
}

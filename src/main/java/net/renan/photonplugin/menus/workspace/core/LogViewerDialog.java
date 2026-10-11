package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.backup.core.WorkspaceFolders;
import net.renan.photonplugin.log.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

public final class LogViewerDialog {
    private static final int MIN_WIDTH = 560;
    private static final int MAX_WIDTH = 900;
    private static final int MIN_HEIGHT = 360;
    private static final int MAX_HEIGHT = 650;
    private static final int MAX_WIDTH_SAMPLE_LINES = 2000;

    private LogViewerDialog() {
    }

    public static void show(MCreator mcreator) {
        File workspaceFolder = WorkspaceFolders.resolve(mcreator);
        if (workspaceFolder == null) {
            JOptionPane.showMessageDialog(mcreator,
                    L10N.t("plugin.photon.workspacemenu.log.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.log.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.log.title"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setMargin(new Insets(8, 10, 8, 10));
        String initialLog = Log.readPersistedLogFor(workspaceFolder);
        textArea.setText(initialLog);
        textArea.setCaretPosition(textArea.getDocument().getLength());

        long[] lastLineCount = {countLines(initialLog)};

        JScrollPane scrollPane = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(PhotonTheme.roundedLineBorder(PhotonTheme.cardBorder(), 1,
                PhotonTheme.LIST_CONTAINER_ARC));

        JButton openFolderButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.log.open_folder"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton exportButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.log.export"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton deleteButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.log.delete"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        JButton closeButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.close"),
                PhotonTheme.ButtonVariant.NEUTRAL);
        openFolderButton.setToolTipText(L10N.t("plugin.photon.workspacemenu.log.open_folder"));
        exportButton.setToolTipText(L10N.t("plugin.photon.workspacemenu.log.export"));
        deleteButton.setToolTipText(L10N.t("plugin.photon.workspacemenu.log.delete"));

        JButton[] allButtons = {openFolderButton, exportButton, deleteButton, closeButton};

        JPanel bottomPanel = DialogUtils.bottomButtonRow(openFolderButton, exportButton, deleteButton, closeButton);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        exportButton.addActionListener(e -> LogExportDialog.show(dialog, workspaceFolder));
        deleteButton.addActionListener(e -> LogDeleteDialog.show(dialog, workspaceFolder));
        openFolderButton.addActionListener(e -> openLogsFolder(dialog, workspaceFolder, allButtons));
        closeButton.addActionListener(e -> dialog.dispose());

        Runnable onNewLine = () -> SwingUtilities.invokeLater(() -> {
            List<String> newLines = Log.getBufferedLogSinceFor(workspaceFolder, lastLineCount[0]);

            if (newLines == null) {
                lastLineCount[0] = Log.getTotalLinesRecordedFor(workspaceFolder);
                textArea.setText(Log.readPersistedLogFor(workspaceFolder));
                textArea.setCaretPosition(textArea.getDocument().getLength());
                return;
            }

            if (newLines.isEmpty()) {
                return;
            }

            lastLineCount[0] += newLines.size();
            int caret = textArea.getCaretPosition();
            boolean wasAtEnd = caret >= textArea.getDocument().getLength() - 1;
            StringBuilder appended = new StringBuilder();
            for (String line : newLines) {
                appended.append(line).append(System.lineSeparator());
            }
            textArea.append(appended.toString());
            textArea.setCaretPosition(
                    wasAtEnd ? textArea.getDocument().getLength() : Math.min(caret, textArea.getDocument()
                            .getLength()));
        });

        AutoCloseable logListener = Log.addLogListener(workspaceFolder, onNewLine);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    logListener.close();
                } catch (Exception ignored) {
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        DialogUtils.setDefaultButton(dialog, closeButton);

        Dimension initialSize = computeInitialSize(textArea, initialLog);
        dialog.setSize(initialSize);
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setVisible(true);
    }

    private static long countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        long lines = 1L;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static Dimension computeInitialSize(JTextArea textArea, String logText) {
        int lineCount = Math.max(1, textArea.getLineCount());
        FontMetrics metrics = textArea.getFontMetrics(textArea.getFont());
        int lineHeight = metrics.getHeight();
        int height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, lineCount * lineHeight + 140));

        String[] lines = logText.split("\\R", -1);
        int step = Math.max(1, lines.length / MAX_WIDTH_SAMPLE_LINES);
        int longestLineWidth = 0;
        for (int i = 0; i < lines.length; i += step) {
            longestLineWidth = Math.max(longestLineWidth, metrics.stringWidth(lines[i]));
        }
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, longestLineWidth + 80));

        return new Dimension(width, height);
    }

    private static void openLogsFolder(JDialog dialog, File workspaceFolder, JButton[] buttonsToDisable) {
        File logsFolder = Log.getLogsFolderFor(workspaceFolder);
        if (logsFolder == null) {
            JOptionPane.showMessageDialog(dialog,
                    L10N.t("plugin.photon.workspacemenu.log.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.log.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!logsFolder.exists() && !logsFolder.mkdirs()) {
            JOptionPane.showMessageDialog(dialog,
                    L10N.t("plugin.photon.workspacemenu.log.open_folder.not_found"),
                    L10N.t("plugin.photon.workspacemenu.log.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        SwingWorkers.runInBackground(dialog, buttonsToDisable, () -> {
            Desktop.getDesktop().open(logsFolder);
            return null;
        }, ignored -> {
        }, ex -> {
            Log.withWorkspace(workspaceFolder, () -> Log.error("Failed to open .photon log folder.", ex));
            JOptionPane.showMessageDialog(dialog,
                    L10N.t("plugin.photon.workspacemenu.log.open_folder.failure", ex.getMessage()),
                    L10N.t("plugin.photon.workspacemenu.log.title"), JOptionPane.ERROR_MESSAGE);
        });
    }
}
package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.PluginMetadata;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public final class AboutDialog {
    private static final String PLUGIN_AUTHOR = "Renan";

    private AboutDialog() {
    }

    public static void show(MCreator mcreator, String componentLabel, String componentVersion, String libLabel,
                            String libVersion) {
        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.workspacemenu.about.title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        DialogUtils.closeOnEscape(dialog);

        JPanel content = new JPanel(new BorderLayout(16, 16));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));

        Icon dialogIcon = loadIcon("64px_photon.photonplugin", 64);
        JLabel iconLabel = new JLabel(dialogIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        content.add(iconLabel, BorderLayout.WEST);
        if (dialogIcon instanceof ImageIcon imageIcon) {
            dialog.setIconImage(imageIcon.getImage());
        }

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Renan's Photon Plugin");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setForeground(PhotonTheme.accentPressed());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        String pluginVersionText = L10N.t("plugin.photon.workspacemenu.about.plugin_version",
                PluginMetadata.getPluginVersion());
        String authorText = L10N.t("plugin.photon.workspacemenu.about.author", PLUGIN_AUTHOR);
        String componentVersionText = componentLabel + ": " + componentVersion;
        String libVersionText = libLabel + ": " + libVersion;

        JLabel pluginVersionLabel = infoLine(pluginVersionText);
        JLabel authorLabel = infoLine(authorText);
        JLabel componentVersionLabel = infoLine(componentVersionText);
        JLabel libVersionLabel = infoLine(libVersionText);

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(pluginVersionLabel);
        textPanel.add(authorLabel);
        textPanel.add(Box.createVerticalStrut(10));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setForeground(PhotonTheme.cardBorder());
        textPanel.add(separator);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(componentVersionLabel);
        textPanel.add(libVersionLabel);

        content.add(textPanel, BorderLayout.CENTER);

        JButton copyButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.about.copy_info"),
                PhotonTheme.ButtonVariant.PRIMARY);
        JButton closeButton = PhotonTheme.button(L10N.t("plugin.photon.workspacemenu.close"),
                PhotonTheme.ButtonVariant.NEUTRAL);

        String clipboardText = String.join(System.lineSeparator(), "Renan's Photon Plugin", pluginVersionText,
                authorText, componentVersionText, libVersionText);
        copyButton.addActionListener(e -> copyToClipboard(copyButton, clipboardText));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel bottomPanel = DialogUtils.bottomButtonRow(copyButton, closeButton);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        content.add(bottomPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        DialogUtils.setDefaultButton(dialog, closeButton);
        dialog.pack();
        DialogUtils.clampToScreen(dialog, 0.9, 0.85);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private static JLabel infoLine(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setForeground(PhotonTheme.textForeground());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }

    private static Icon loadIcon(String resourceKey, int size) {
        try {
            return IconUtils.scaleTo(resourceKey, UIRES.get(resourceKey), size);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void copyToClipboard(JButton button, String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);

        String originalText = button.getText();
        button.setText(L10N.t("plugin.photon.workspacemenu.about.copied"));
        button.setEnabled(false);
        Timer revertTimer = new Timer(1200, e -> {
            button.setText(originalText);
            button.setEnabled(true);
        });
        revertTimer.setRepeats(false);
        revertTimer.start();
    }
}
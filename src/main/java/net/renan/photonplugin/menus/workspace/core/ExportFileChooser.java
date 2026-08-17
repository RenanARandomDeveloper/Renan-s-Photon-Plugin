package net.renan.photonplugin.menus.workspace.core;

import net.mcreator.ui.init.L10N;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Locale;

public final class ExportFileChooser {

    private static File lastUsedDirectory;

    private ExportFileChooser() {
    }

    public static File chooseDestination(Window owner, String dialogTitle, String suggestedName, String extension) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(dialogTitle);
        if (lastUsedDirectory != null && lastUsedDirectory.isDirectory()) {
            fileChooser.setCurrentDirectory(lastUsedDirectory);
        }
        fileChooser.setSelectedFile(new File(suggestedName));
        fileChooser.setFileFilter(new FileNameExtensionFilter(extension.toUpperCase(Locale.ROOT), extension));

        int result = fileChooser.showSaveDialog(owner);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File destination = fileChooser.getSelectedFile();
        String suffix = "." + extension;
        if (!destination.getName().toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            destination = new File(destination.getParentFile(), destination.getName() + suffix);
        }
        lastUsedDirectory = destination.getParentFile();

        if (destination.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(owner,
                    L10N.t("plugin.photon.workspacemenu.export.overwrite_confirm", destination.getName()),
                    dialogTitle, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return null;
            }
        }

        return destination;
    }
}

package net.renan.photonplugin.menus.resource.core.support;

import com.formdev.flatlaf.util.SystemFileChooser;
import net.mcreator.preferences.PreferencesManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public final class DirectoryDialogs {

    private static File lastDir = new File(System.getProperty("user.home"));

    private DirectoryDialogs() {
    }

    public static File chooseDirectory(Window parent) {
        boolean nativeChooser;
        try {
            nativeChooser = PreferencesManager.PREFERENCES.ui.nativeFileChooser.get();
        } catch (Throwable t) {
            nativeChooser = true;
        }

        File selected = nativeChooser ? chooseNative(parent) : chooseSwing(parent);
        if (selected != null) {
            lastDir = selected.getParentFile() != null ? selected.getParentFile() : selected;
        }
        return selected;
    }

    private static File chooseNative(Window parent) {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (lastDir != null && lastDir.isDirectory()) {
            chooser.setCurrentDirectory(lastDir);
        }
        if (chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private static File chooseSwing(Window parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (lastDir != null && lastDir.isDirectory()) {
            chooser.setCurrentDirectory(lastDir);
        }
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }
}
package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class FolderPicker {

    private FolderPicker() {
    }

    private static DefaultMutableTreeNode buildFolderOnlyTree(File dir, String label, Set<String> excludePaths) {
        return buildFolderOnlyTree(dir, label, excludePaths, new HashSet<>());
    }

    private static DefaultMutableTreeNode buildFolderOnlyTree(File dir, String label, Set<String> excludePaths, Set<Path> visited) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FolderPickerNode(dir, label));
        try {
            if (!visited.add(dir.toPath().toRealPath())) return node;
        } catch (IOException | RuntimeException ex) {
            return node;
        }
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs != null) {
            Arrays.sort(subs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File sub : subs) {
                if (!excludePaths.contains(sub.getAbsolutePath())) {
                    node.add(buildFolderOnlyTree(sub, sub.getName(), excludePaths, visited));
                }
            }
        }
        return node;
    }

    public static File showFolderPickerDialog(Component parent, File baseDir, String rootLabel, Set<String> excludePaths) {
        DefaultMutableTreeNode rootNode = buildFolderOnlyTree(baseDir, rootLabel, excludePaths);
        return showFolderPickerDialog(parent, rootNode, baseDir, true);
    }

    public static File showFolderPickerDialog(Component parent, DefaultMutableTreeNode rootNode, File fallback) {
        return showFolderPickerDialog(parent, rootNode, fallback, true);
    }

    public static File showFolderPickerDialog(Component parent, DefaultMutableTreeNode rootNode, File fallback, boolean rootVisible) {
        JTree tree = new JTree(rootNode);
        tree.setRootVisible(rootVisible);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setSelectionRow(0);
        int prevRowCount;
        do {
            prevRowCount = tree.getRowCount();
            for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        } while (tree.getRowCount() != prevRowCount);

        tree.setBackground(MenuTheme.backgroundColor());
        tree.setForeground(MenuTheme.foregroundColor());
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setTextNonSelectionColor(MenuTheme.foregroundColor());
                setTextSelectionColor(Color.WHITE);
                setBackgroundNonSelectionColor(MenuTheme.backgroundColor());
                setBackgroundSelectionColor(MenuTheme.accentColor());
                setBorderSelectionColor(MenuTheme.shade(MenuTheme.accentColor(), 30));
            }

            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode dmtn && dmtn.getUserObject() instanceof FolderPickerNode fpn) {
                    setText(fpn.label());
                    Icon fi = UIManager.getIcon("FileView.directoryIcon");
                    if (fi != null) setIcon(fi);
                }
                setForeground(sel ? Color.WHITE : MenuTheme.foregroundColor());
                setBackground(sel ? MenuTheme.accentColor() : MenuTheme.backgroundColor());
                setOpaque(sel);
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(new Dimension(320, 280));
        scroll.setBorder(BorderFactory.createLineBorder(MenuTheme.shade(MenuTheme.backgroundColor(), 25)));
        scroll.getViewport().setBackground(MenuTheme.backgroundColor());
        int result = JOptionPane.showConfirmDialog(
                parent, scroll,
                L10N.t("plugin.photon.resourcemenu.move.select.target.folder"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null) return fallback;
        DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        if (selNode.getUserObject() instanceof FolderPickerNode fpn) return fpn.dir();
        return fallback;
    }

    public record FolderPickerNode(File dir, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}

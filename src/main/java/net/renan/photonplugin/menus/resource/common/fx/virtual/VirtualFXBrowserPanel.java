package net.renan.photonplugin.menus.resource.common.fx.virtual;

import net.mcreator.ui.component.util.ComponentUtils;
import net.renan.photonplugin.menus.resource.core.browser.*;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class VirtualFXBrowserPanel extends FXBrowserPanel {

    private VirtualFolderStore store;
    private final Supplier<File> rootDirGetter;

    public VirtualFXBrowserPanel(List<Supplier<File>> rootSuppliers, String extension,
                                  Function<File, Icon> iconProvider) {
        super(rootSuppliers, extension, iconProvider);
        this.rootDirGetter = (rootSuppliers != null && !rootSuppliers.isEmpty()) ? rootSuppliers.get(0) : null;
    }

    public void setVirtualStore(VirtualFolderStore store) {
        this.store = store;
        refresh();
    }

    @Override
    public String getFolderName(File dir) {
        if (store != null && store.isVirtualDir(dir)) {
            return store.lastSegmentOf(store.virtualDirName(dir));
        }
        return super.getFolderName(dir);
    }

    @Override
    public String getFolderTooltip(File dir) {
        if (store != null && store.isVirtualDir(dir)) {
            return store.lastSegmentOf(store.virtualDirName(dir));
        }
        return super.getFolderTooltip(dir);
    }

    @Override
    public void refresh() {
        if (store != null) {
            refreshVirtual();
        } else {
            super.refresh();
        }
    }

    private void refreshVirtual() {
        Set<File> prevFiles = new HashSet<>(selectedFiles);
        Set<File> prevFolders = new HashSet<>(selectedFolders);
        selectedFiles.clear();
        selectedFolders.clear();
        lastSelected = null;
        grid.removeAll();
        allCards.clear();

        if (filterType != FilterType.FILE) {
            for (File vDir : store.listVirtualDirs(currentDir)) {
                if (!filterText.isEmpty() && !getFolderName(vDir).toLowerCase().contains(filterText)) continue;
                FolderCard card = new FolderCard(vDir, this);
                if (prevFolders.contains(vDir)) {
                    card.setSelected(true);
                    selectedFolders.add(vDir);
                    lastSelected = card;
                }
                grid.add(card);
                allCards.add(card);
            }
        }
        if (filterType != FilterType.FOLDER) {
            for (File f : store.listFilesAt(currentDir, extension, filterText)) {
                FileCard card = new FileCard(f, this, iconProvider);
                if (prevFiles.contains(f)) {
                    card.setSelected(true);
                    selectedFiles.add(f);
                    lastSelected = card;
                }
                grid.add(card);
                allCards.add(card);
            }
        }
        grid.revalidate();
        grid.repaint();
        updateNavBar(new ArrayList<>());
    }

    @Override
    protected void rebuildBreadcrumbPanel(List<File> roots) {
        if (store != null && currentDir != null && store.isVirtualDir(currentDir)) {
            breadcrumbPanel.removeAll();

            String rootName = (rootDirGetter != null && rootDirGetter.get() != null) ? rootDirGetter.get().getName() : "fx";
            addVirtualBreadcrumbSegment(rootName, rootDirGetter != null ? rootDirGetter.get() : null, false);

            String fullPath = store.virtualDirName(currentDir);
            String[] segments = fullPath.split(VirtualFolderStore.SEP);
            StringBuilder accumulated = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (accumulated.length() > 0) accumulated.append(VirtualFolderStore.SEP);
                accumulated.append(segments[i]);
                boolean isLast = (i == segments.length - 1);
                addVirtualBreadcrumbSegment(segments[i], store.virtualDirRef(accumulated.toString()), isLast);
            }

            breadcrumbPanel.revalidate();
            breadcrumbPanel.repaint();
            return;
        }
        super.rebuildBreadcrumbPanel(roots);
    }

    private void addVirtualBreadcrumbSegment(String label, File target, boolean isCurrent) {
        if (breadcrumbPanel.getComponentCount() > 0) {
            JLabel sep = new JLabel(" / ");
            sep.setForeground(new Color(90, 90, 90));
            ComponentUtils.deriveFont(sep, 11f);
            breadcrumbPanel.add(sep);
        }

        JLabel lbl = new JLabel(label);
        ComponentUtils.deriveFont(lbl, 11f);

        if (isCurrent) {
            lbl.setForeground(new Color(200, 200, 200));
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            breadcrumbPanel.add(lbl);
            return;
        }

        lbl.setForeground(new Color(130, 190, 100));
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) navigateDirect(target);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                lbl.setForeground(new Color(160, 220, 130));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lbl.setForeground(new Color(130, 190, 100));
            }
        });

        FileDropSupport.install(lbl, this, "breadcrumb '" + label + "'", () -> target, null, null, null);

        breadcrumbPanel.add(lbl);
    }

    @Override
    protected boolean isFolderItem(File f) {
        return (store != null && store.isVirtualDir(f)) || super.isFolderItem(f);
    }

    @Override
    public int performMove(List<File> filesToMove, File destDir, BulkConflictResolver resolver) {
        if (store == null) return super.performMove(filesToMove, destDir, resolver);

        List<File> realFiles = new ArrayList<>();
        List<File> virtualFolders = new ArrayList<>();
        for (File f : filesToMove) {
            if (store.isVirtualDir(f)) virtualFolders.add(f);
            else realFiles.add(f);
        }

        int moved = 0;
        if (!realFiles.isEmpty()) {
            if (store.isVirtualDir(destDir)) {
                store.assignToVirtualDir(realFiles, destDir);
                moved += realFiles.size();
            } else if (rootDirGetter != null && destDir.equals(rootDirGetter.get())) {
                store.removeFiles(realFiles);
                moved += realFiles.size();
            } else {
                moved += super.performMove(realFiles, destDir, resolver);
            }
        }

        for (File folder : virtualFolders) {
            if (folder.equals(destDir)) continue;
            String destParent = store.isVirtualDir(destDir) ? store.virtualDirName(destDir) : "";
            if (store.moveFolderInto(store.virtualDirName(folder), destParent)) moved++;
        }

        return moved;
    }
}

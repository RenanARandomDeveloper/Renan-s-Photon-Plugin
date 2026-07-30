package net.renan.photonplugin.menus.resource.common;

import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.workspace.elements.ModElement;
import net.renan.photonplugin.Log;
import net.renan.photonplugin.blockly.FXListManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public final class ResourceMenuCommonFX {
    private static final String FX_EXTENSION = ".fx";
    private static final Set<String> SKIP_DIRS = Set.of("photon_plugin");

    private ResourceMenuCommonFX() {}

    private static class VirtualFolderStore {
        static final String V_PREFIX = "\0virt:";
        static final String SEP = "\u0001";
        private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

        private final File storeFile;
        private final Supplier<List<File>> allFilesSupplier;
        private final java.util.LinkedHashMap<String, List<String>> data = new java.util.LinkedHashMap<>();

        VirtualFolderStore(File workspaceFolder, Supplier<List<File>> allFilesSupplier) {
            this.allFilesSupplier = allFilesSupplier;
            File photonDir = new File(workspaceFolder, ".photon");
            photonDir.mkdirs();
            this.storeFile = new File(photonDir, "fx_virtual_folders.json");
            load();
        }

        public List<File> listVirtualDirs(File currentDir) {
            String parent = (currentDir != null && isVirtualDir(currentDir)) ? virtualDirName(currentDir) : "";
            return data.keySet().stream()
                    .filter(k -> !k.isEmpty())
                    .filter(k -> parentPath(k).equals(parent))
                    .sorted()
                    .map(this::virtualDirRef)
                    .collect(java.util.stream.Collectors.toList());
        }

        public List<File> getFilesInFolder(File virtualDir) {
            Map<String, File> byName = new java.util.LinkedHashMap<>();
            for (File f : allFilesSupplier.get()) byName.put(f.getName(), f);
            List<File> result = new ArrayList<>();
            for (String name : data.getOrDefault(virtualDirName(virtualDir), List.of())) {
                File f = byName.get(name);
                if (f != null) result.add(f);
            }
            return result;
        }

        public boolean folderExists(String name) {
            return data.containsKey(name);
        }

        public File virtualDirRef(String name) {
            return new File(V_PREFIX + name);
        }

        private static String parentPath(String fullPath) {
            int idx = fullPath.lastIndexOf(SEP);
            return idx >= 0 ? fullPath.substring(0, idx) : "";
        }

        private static String lastSegment(String fullPath) {
            int idx = fullPath.lastIndexOf(SEP);
            return idx >= 0 ? fullPath.substring(idx + 1) : fullPath;
        }

        private static String childPath(String parentPath, String childName) {
            return parentPath.isEmpty() ? childName : parentPath + SEP + childName;
        }

        private static boolean isDescendantOrSelf(String maybeAncestor, String path) {
            return path.equals(maybeAncestor) || path.startsWith(maybeAncestor + SEP);
        }

        public String parentOf(String fullPath) {
            return parentPath(fullPath);
        }

        public String lastSegmentOf(String fullPath) {
            return lastSegment(fullPath);
        }

        private void reparentSubtree(String oldPrefix, String newPrefix) {
            if (oldPrefix.equals(newPrefix)) return;
            List<String> keys = data.keySet().stream()
                    .filter(k -> isDescendantOrSelf(oldPrefix, k))
                    .collect(java.util.stream.Collectors.toList());
            for (String oldKey : keys) {
                String newKey = newPrefix + oldKey.substring(oldPrefix.length());
                List<String> files = data.remove(oldKey);
                data.merge(newKey, files, (a, b) -> { a.addAll(b); return a; });
            }
        }

        private String uniqueChildName(String parentPath, String baseName) {
            String candidate = baseName;
            int counter = 2;
            while (data.containsKey(childPath(parentPath, candidate))) {
                candidate = baseName + " (" + counter + ")";
                counter++;
            }
            return candidate;
        }

        void renameFolder(String oldFullPath, String newName) {
            String newFullPath = childPath(parentPath(oldFullPath), newName.trim());
            if (newFullPath.equals(oldFullPath) || data.containsKey(newFullPath)) return;
            reparentSubtree(oldFullPath, newFullPath);
            save();
        }

        void moveFolderInto(String sourceFullPath, String destParentPath) {
            if (isDescendantOrSelf(sourceFullPath, destParentPath)) return;
            String name = lastSegment(sourceFullPath);
            if (data.containsKey(childPath(destParentPath, name))) {
                name = uniqueChildName(destParentPath, name);
            }
            String newFullPath = childPath(destParentPath, name);
            if (newFullPath.equals(sourceFullPath)) return;
            reparentSubtree(sourceFullPath, newFullPath);
            save();
        }

        void deleteFolderAndFiles(File virtualDir) {
            String path = virtualDirName(virtualDir);
            data.keySet().stream()
                    .filter(k -> isDescendantOrSelf(path, k))
                    .collect(java.util.stream.Collectors.toList())
                    .forEach(data::remove);
            save();
        }

        public List<File> listFilesAt(File currentDir, String extension, String filterText) {
            Map<String, File> byName = new java.util.LinkedHashMap<>();
            for (File f : allFilesSupplier.get()) byName.put(f.getName(), f);
            List<File> result = new ArrayList<>();

            if (currentDir != null && isVirtualDir(currentDir)) {
                for (String name : data.getOrDefault(virtualDirName(currentDir), List.of())) {
                    File f = byName.get(name);
                    if (f != null && matches(f, extension, filterText)) result.add(f);
                }
            } else {
                Set<String> allAssigned = getAllAssigned();
                for (String name : data.getOrDefault("", List.of())) {
                    File f = byName.get(name);
                    if (f != null && matches(f, extension, filterText)) result.add(f);
                }
                for (File f : allFilesSupplier.get()) {
                    if (!allAssigned.contains(f.getName())
                            && !data.getOrDefault("", List.of()).contains(f.getName())
                            && matches(f, extension, filterText)) result.add(f);
                }
            }
            return result;
        }

        public boolean isVirtualDir(File f) {
            return f != null && f.getPath().startsWith(V_PREFIX);
        }

        public void assignToVirtualDir(List<File> files, File dest) {
            String key = virtualDirName(dest);
            List<String> names = files.stream().map(File::getName).collect(java.util.stream.Collectors.toList());
            data.values().forEach(l -> l.removeAll(names));
            data.computeIfAbsent(key, k -> new ArrayList<>()).addAll(names);
            save();
        }

        public String virtualDirName(File virtualDir) {
            return virtualDir.getPath().substring(V_PREFIX.length());
        }

        void createFolder(String name) {
            name = name.trim();
            if (name.isEmpty() || data.containsKey(name)) return;
            data.put(name, new ArrayList<>());
            save();
        }

        void deleteFolder(File virtualDir) {
            String path = virtualDirName(virtualDir);
            String parent = parentPath(path);

            List<String> ownFiles = data.remove(path);
            if (ownFiles != null && !ownFiles.isEmpty())
                data.computeIfAbsent(parent, k -> new ArrayList<>()).addAll(ownFiles);

            data.keySet().stream()
                    .filter(k -> parentPath(k).equals(path))
                    .collect(java.util.stream.Collectors.toList())
                    .forEach(childKey -> reparentSubtree(childKey, childPath(parent, lastSegment(childKey))));

            save();
        }

        public List<File> getAllFilesInSubtree(File virtualDir) {
            String path = virtualDirName(virtualDir);
            Map<String, File> byName = new java.util.LinkedHashMap<>();
            for (File f : allFilesSupplier.get()) byName.put(f.getName(), f);
            List<File> result = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : data.entrySet()) {
                if (isDescendantOrSelf(path, entry.getKey())) {
                    for (String name : entry.getValue()) {
                        File f = byName.get(name);
                        if (f != null) result.add(f);
                    }
                }
            }
            return result;
        }

        public boolean hasDescendantFolders(File virtualDir) {
            String path = virtualDirName(virtualDir);
            return data.keySet().stream().anyMatch(k -> !k.equals(path) && isDescendantOrSelf(path, k));
        }

        void renameFile(File oldFile, File newFile) {
            String old = oldFile.getName(), nw = newFile.getName();
            for (List<String> list : data.values()) {
                int i = list.indexOf(old);
                if (i >= 0) { list.set(i, nw); save(); return; }
            }
        }

        void removeFiles(List<File> deleted) {
            Set<String> names = deleted.stream().map(File::getName).collect(java.util.stream.Collectors.toSet());
            data.values().forEach(l -> l.removeAll(names));
            save();
        }

        private boolean matches(File f, String ext, String filter) {
            return (ext == null || hasExtension(f, ext))
                    && (filter == null || filter.isBlank()
                    || f.getName().toLowerCase().contains(filter.toLowerCase()));
        }

        private Set<String> getAllAssigned() {
            Set<String> all = new java.util.HashSet<>();
            data.entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty())
                    .forEach(e -> all.addAll(e.getValue()));
            return all;
        }

        private void load() {
            if (!storeFile.exists()) return;
            try {
                String json = Files.readString(storeFile.toPath(), StandardCharsets.UTF_8);
                java.lang.reflect.Type type =
                        new com.google.gson.reflect.TypeToken<java.util.LinkedHashMap<String, List<String>>>() {}.getType();
                java.util.LinkedHashMap<String, List<String>> loaded = GSON.fromJson(json, type);
                if (loaded != null) data.putAll(loaded);
            } catch (Exception e) {
                logError("Failed to load virtual folder store from " + storeFile.getAbsolutePath(), e);
            }
        }

        private void save() {
            try {
                Files.writeString(storeFile.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logError("Failed to save virtual folder store to " + storeFile.getAbsolutePath(), e);
            }
        }
    }

    public static class VirtualFXBrowserPanel extends FXBrowserPanel {
        private VirtualFolderStore store;
        private final Supplier<File> rootDirGetter;

        public VirtualFXBrowserPanel(List<Supplier<File>> rootSuppliers, String extension, java.util.function.Function<File, Icon> iconProvider) {
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
            Set<File> prevFiles   = new HashSet<>(selectedFiles);
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
                net.mcreator.ui.component.util.ComponentUtils.deriveFont(sep, 11f);
                breadcrumbPanel.add(sep);
            }

            JLabel lbl = new JLabel(label);
            net.mcreator.ui.component.util.ComponentUtils.deriveFont(lbl, 11f);

            if (isCurrent) {
                lbl.setForeground(new Color(200, 200, 200));
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                breadcrumbPanel.add(lbl);
                return;
            }

            lbl.setForeground(new Color(130, 190, 100));
            lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lbl.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) navigateDirect(target);
                }
                @Override public void mouseEntered(MouseEvent e) { lbl.setForeground(new Color(160, 220, 130)); }
                @Override public void mouseExited(MouseEvent e) { lbl.setForeground(new Color(130, 190, 100)); }
            });

            new DropTarget(lbl, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                        dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                    else dtde.rejectDrag();
                }
                @Override
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (target != null) performMoveWithFeedback(files, target);
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        logWarn("Drag-and-drop move to breadcrumb failed: " + ex.getMessage());
                        dtde.dropComplete(false);
                    }
                }
            });

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
                store.moveFolderInto(store.virtualDirName(folder), destParent);
                moved++;
            }

            return moved;
        }
    }

    private static List<File> listFlatFilesByExtension(File dir, String extension) {
        List<File> result = new ArrayList<>();
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles(f -> f.isFile() && hasExtension(f, extension));
            if (files != null) result.addAll(Arrays.asList(files));
        }
        result.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {
        VirtualFolderStore store = new VirtualFolderStore(
                mcreator.getWorkspace().getWorkspaceFolder(),
                () -> listFlatFilesByExtension(targetDirGetter.get(), FX_EXTENSION));
        FXListManager.initialize(mcreator.getWorkspace().getWorkspaceFolder());

        VirtualFXBrowserPanel browser = new VirtualFXBrowserPanel(
                List.of(targetDirGetter),
                FX_EXTENSION,
                file -> {
                    try { return scaleIconTo(UIRES.get("64px_photon.fx"), 64); } catch (Exception ignored) { return null; }
                }
        );
        browser.setVirtualStore(store);

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildImportButton(mcreator, browser, targetDirGetter, FX_EXTENSION,
                (targetDir, importedFiles) -> {
                    File current = browser.getCurrentDir();
                    if (store.isVirtualDir(current)) store.assignToVirtualDir(importedFiles, current);
                    FXListManager.addEntries(mcreator.getWorkspace().getWorkspaceFolder(), importedFiles);
                }));
        toolBar.add(buildCloneButtonForVirtualBrowser(mcreator, browser, store, targetDirGetter));
        toolBar.add(buildDeleteButtonForVirtualBrowser(mcreator, browser, store));
        toolBar.add(buildExportButtonForVirtualBrowser(mcreator, browser, store));
        toolBar.add(buildRenameButtonForVirtualBrowser(mcreator, browser, store));
        toolBar.add(buildMoveButtonForVirtualBrowser(mcreator, browser, store, targetDirGetter));
        toolBar.add(buildCreateVirtualFolderButton(mcreator, browser, store));
        toolBar.add(buildSearchUsagesButton(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));

        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter, (root, changed, kind) -> {
            Log.bindWorkspace(mcreator.getWorkspace().getWorkspaceFolder());
            File file = changed.toFile();
            switch (kind) {
                case CREATE -> {
                    if (!file.isDirectory() && hasExtension(file, FX_EXTENSION)) {
                        FXListManager.addEntry(mcreator.getWorkspace().getWorkspaceFolder(), file);
                    }
                }
                case DELETE -> {
                    if (hasExtension(file, FX_EXTENSION)) {
                        FXListManager.removeEntry(mcreator.getWorkspace().getWorkspaceFolder(), file);
                    }
                }
                case OVERFLOW -> FXListManager.initialize(mcreator.getWorkspace().getWorkspaceFolder(), targetDirGetter.get());
                case MODIFY -> {  }
            }
        });

        return panel;
    }

    private static DefaultMutableTreeNode buildVirtualFolderTree(File rootDir, String rootLabel,
                                                                 VirtualFolderStore store, Set<File> excludeVirtualDirs) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FolderPickerNode(rootDir, rootLabel));
        addVirtualFolderTreeChildren(rootNode, null, store, excludeVirtualDirs);
        return rootNode;
    }

    private static void addVirtualFolderTreeChildren(DefaultMutableTreeNode parentNode, File parentVirtualDir,
                                                     VirtualFolderStore store, Set<File> excludeVirtualDirs) {
        for (File vDir : store.listVirtualDirs(parentVirtualDir)) {
            if (excludeVirtualDirs.contains(vDir)) continue;
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    new FolderPickerNode(vDir, store.lastSegmentOf(store.virtualDirName(vDir))));
            parentNode.add(node);
            addVirtualFolderTreeChildren(node, vDir, store, excludeVirtualDirs);
        }
    }

    private static JButton buildMoveButtonForVirtualBrowser(MCreator mcreator, VirtualFXBrowserPanel browser,
                                                            VirtualFolderStore store, Supplier<File> rootDirGetter) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.move.files"), "16px_photon.move");
        btn.addActionListener(e -> {
            List<File> selFiles = browser.getSelectedFiles();
            List<File> selFolders = browser.getSelectedFolders().stream()
                    .filter(store::isVirtualDir)
                    .collect(java.util.stream.Collectors.toList());
            if (selFiles.isEmpty() && selFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File currentDir = browser.getCurrentDir();
            Set<File> excludeDirs = new HashSet<>(selFolders);
            if (store.isVirtualDir(currentDir)) excludeDirs.add(currentDir);
            File rootDir = rootDirGetter.get();
            DefaultMutableTreeNode rootNode = buildVirtualFolderTree(rootDir, rootDir.getName(), store, excludeDirs);

            File destDir = showFolderPickerDialog(mcreator, rootNode, rootDirGetter.get());
            if (destDir == null) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }

            BulkConflictResolver resolver = new BulkConflictResolver(selFiles.size());
            int movedFiles;
            int movedFolders = 0;
            browser.beginInternalMutation();
            try {
                movedFiles = selFiles.isEmpty() ? 0 : browser.performMove(selFiles, destDir, resolver);

                for (File folder : selFolders) {
                    String destParent = store.isVirtualDir(destDir) ? store.virtualDirName(destDir) : "";
                    store.moveFolderInto(store.virtualDirName(folder), destParent);
                    movedFolders++;
                }
                if (movedFiles + movedFolders > 0) browser.refresh();
            } finally {
                browser.endInternalMutation();
            }

            if (movedFiles + movedFolders > 0) {
                JOptionPane.showMessageDialog(mcreator, buildItemLabel(movedFiles, movedFolders) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                        + " \u2192 \"" + browser.getFolderName(destDir) + "\"" + autoRenameNotice(resolver));
            } else {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            }
        });
        return btn;
    }

    private static JButton buildDeleteButtonForVirtualBrowser(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.delete.files"), "16px_photon.delete");
        btn.addActionListener(e -> performVirtualDeleteSelected(mcreator, browser, store));
        bindDeleteKey(mcreator, browser, () -> performVirtualDeleteSelected(mcreator, browser, store));
        return btn;
    }

    private record VirtualDeleteFolderDecision(File folder, boolean deleteAll, List<File> contents) {}

    private static void performVirtualDeleteSelected(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        List<File> fileTargets = browser.getSelectedFiles();
        List<File> folderTargets = browser.getSelectedFolders().stream()
                .filter(store::isVirtualDir)
                .collect(java.util.stream.Collectors.toList());
        if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean cancelAll = false;
        boolean deleteConfirmedFiles = false;

        if (!fileTargets.isEmpty()) {
            String msg = buildDeleteConfirmationMessage(browser, fileTargets, folderTargets);
            int choice = JOptionPane.showConfirmDialog(
                    mcreator, msg,
                    L10N.t("plugin.photon.resourcemenu.operation.warning"),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                cancelAll = true;
            } else if (choice == JOptionPane.YES_OPTION) {
                deleteConfirmedFiles = true;
            }
        }

        List<VirtualDeleteFolderDecision> decisions = new ArrayList<>();
        for (int fi = 0; fi < folderTargets.size() && !cancelAll; fi++) {
            File folder = folderTargets.get(fi);
            List<File> contents = store.getAllFilesInSubtree(folder);
            boolean hasSubfolders = store.hasDescendantFolders(folder);
            boolean isEmpty = contents.isEmpty() && !hasSubfolders;

            if (isEmpty) {
                int choice = JOptionPane.showConfirmDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.delete.folder.empty.confirm", browser.getFolderName(folder)),
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    cancelAll = true;
                } else if (choice == JOptionPane.YES_OPTION) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, false, contents));
                }
            } else {
                boolean hasSkip = folderTargets.size() > 1;
                String optMoveContents = L10N.t("plugin.photon.resourcemenu.delete.folder.option.move.contents");
                String optDeleteAll    = L10N.t("plugin.photon.resourcemenu.delete.folder.option.delete.all");
                String optSkip         = L10N.t("plugin.photon.resourcemenu.delete.folder.option.skip");
                String optCancelAll    = L10N.t("plugin.photon.resourcemenu.delete.folder.option.cancel");

                Object[] options = hasSkip
                        ? new Object[]{ optMoveContents, optDeleteAll, optSkip, optCancelAll }
                        : new Object[]{ optMoveContents, optDeleteAll, optCancelAll };

                int cancelOptIdx = options.length - 1;
                String progressPrefix = hasSkip ? "(" + (fi + 1) + " / " + folderTargets.size() + ")  " : "";

                String folderMsg = buildFolderContentsPreviewMessage(progressPrefix, browser.getFolderName(folder), contents);

                int action = JOptionPane.showOptionDialog(
                        mcreator, folderMsg,
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[options.length - 1]);

                if (action == JOptionPane.CLOSED_OPTION || action == cancelOptIdx) {
                    cancelAll = true;
                } else if (action == 0) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, false, contents));
                } else if (action == 1) {
                    decisions.add(new VirtualDeleteFolderDecision(folder, true, contents));
                }

            }
        }

        if (!deleteConfirmedFiles && decisions.isEmpty()) {
            if (!cancelAll) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            }
            return;
        }

        final boolean doDeleteFiles = deleteConfirmedFiles;
        final boolean wasCancelled = cancelAll;
        int[] counts = new int[2];

        browser.beginInternalMutation();
        runFileTaskInBackground(mcreator, () -> {
            if (doDeleteFiles) {
                List<File> actuallyDeleted = new ArrayList<>();
                for (File f : fileTargets) {
                    if (f.delete()) {
                        counts[0]++;
                        actuallyDeleted.add(f);
                    }
                }
                store.removeFiles(actuallyDeleted);
                FXListManager.removeEntries(mcreator.getWorkspace().getWorkspaceFolder(), actuallyDeleted);
            }

            for (VirtualDeleteFolderDecision decision : decisions) {
                File folder = decision.folder();
                if (!decision.deleteAll()) {
                    store.deleteFolder(folder);
                    counts[1]++;
                    continue;
                }
                List<File> actuallyDeleted = new ArrayList<>();
                boolean allDeleted = true;
                for (File f : decision.contents()) {
                    if (f.delete()) {
                        actuallyDeleted.add(f);
                    } else {
                        allDeleted = false;
                    }
                }
                if (!actuallyDeleted.isEmpty()) {
                    store.removeFiles(actuallyDeleted);
                    FXListManager.removeEntries(mcreator.getWorkspace().getWorkspaceFolder(), actuallyDeleted);
                }
                if (allDeleted) {
                    store.deleteFolderAndFiles(folder);
                    counts[1]++;
                } else {
                    onEdtVoid(() -> JOptionPane.showMessageDialog(
                            mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.delete.error") + "\n" + browser.getFolderName(folder),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                }
            }
        }, () -> {
            try {
                if (counts[0] + counts[1] > 0) {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.deleted"));
                } else if (!wasCancelled) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
    }

    private static JButton buildExportButtonForVirtualBrowser(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.export.files"), "16px_photon.export");
        btn.addActionListener(e -> {
            List<File> fileTargets = browser.getSelectedFiles();
            List<File> folderTargets = browser.getSelectedFolders().stream()
                    .filter(store::isVirtualDir)
                    .collect(java.util.stream.Collectors.toList());
            if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(mcreator) != JFileChooser.APPROVE_OPTION) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }

            File dest = chooser.getSelectedFile();
            int folderConflicts = 0;
            for (File folder : folderTargets) {
                if (new File(dest, browser.getFolderName(folder)).exists()) folderConflicts++;
            }
            int conflictCount = BulkConflictResolver.countConflicts(fileTargets, dest) + folderConflicts;
            BulkConflictResolver resolver = (conflictCount > 0) ? new BulkConflictResolver(conflictCount) : null;
            Map<File, List<File>> folderContents = new LinkedHashMap<>();
            Map<File, String> folderNames = new LinkedHashMap<>();
            for (File folder : folderTargets) {
                folderContents.put(folder, store.getFilesInFolder(folder));
                folderNames.put(folder, browser.getFolderName(folder));
            }

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            Exception[] failure = new Exception[1];

            runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File f : fileTargets) {
                        if (cancelAllHolder[0]) break;
                        File destFile = new File(dest, f.getName());
                        ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFile) : checkOverwrite(mcreator, destFile);
                        if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                        if (resolution.action() == ACTION_SKIP)   continue;
                        destFile = resolution.targetFile();
                        if (destFile.exists()) destFile.delete();
                        FileIO.copyFile(f, destFile);
                        counts[0]++;
                    }

                    for (File folder : folderTargets) {
                        if (cancelAllHolder[0]) break;
                        String folderName = folderNames.get(folder);
                        File destFolder = new File(dest, folderName);
                        if (destFolder.exists()) {
                            ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFolder) : checkOverwrite(mcreator, destFolder);
                            if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                            if (resolution.action() == ACTION_SKIP)   continue;
                            destFolder = resolution.targetFile();
                            if (destFolder.exists()) deleteRecursively(destFolder);
                        }
                        if (!destFolder.exists() && !destFolder.mkdirs()) {
                            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                    L10N.t("plugin.photon.resourcemenu.export.failed") + folderName,
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                            continue;
                        }
                        for (File f : folderContents.get(folder)) {
                            FileIO.copyFile(f, new File(destFolder, f.getName()));
                        }
                        counts[1]++;
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                if (failure[0] != null) {
                    logError("Failed to export selected FX resources", failure[0]);
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.export.failed") + failure[0].getMessage(),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int total = counts[0] + counts[1];
                if (total > 0) {
                    JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.exported") + autoRenameNotice(resolver));
                } else if (!cancelAllHolder[0]) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            });
        });
        return btn;
    }

    private static JButton buildCloneButtonForVirtualBrowser(MCreator mcreator, VirtualFXBrowserPanel browser,
                                                             VirtualFolderStore store, Supplier<File> targetDirGetter) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.clone.element"), "16px_photon.clone");
        btn.addActionListener(e -> {
            List<File> sourceFiles = browser.getSelectedFiles();
            List<File> sourceFolders = browser.getSelectedFolders().stream()
                    .filter(store::isVirtualDir)
                    .collect(java.util.stream.Collectors.toList());
            if (sourceFiles.isEmpty() && sourceFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File rootDir = targetDirGetter.get();
            if (!rootDir.exists()) rootDir.mkdirs();
            int total = sourceFiles.size() + sourceFolders.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);
            File currentDir = browser.getCurrentDir();
            Map<File, List<File>> folderContents = new LinkedHashMap<>();
            Map<File, String> folderCurrentNames = new LinkedHashMap<>();
            for (File src : sourceFolders) {
                folderContents.put(src, store.getFilesInFolder(src));
                folderCurrentNames.put(src, store.lastSegmentOf(store.virtualDirName(src)));
            }

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            int[] indexHolder = new int[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                List<File> singleClonedFiles = new ArrayList<>();

                for (File src : sourceFiles) {
                    if (cancelAllHolder[0]) break;
                    FileNameParts parts = FileNameParts.of(src);
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String cloneName = promptCloneName(mcreator, parts, progress, nameResolver);
                    if (cloneName == null) { cancelAllHolder[0] = true; break; }

                    File destFile = new File(rootDir, cloneName + canonicalExtension(parts.extension()));
                    ConflictResolution resolution = resolver.resolve(mcreator, destFile);
                    if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                    if (resolution.action() == ACTION_SKIP)   continue;
                    destFile = resolution.targetFile();

                    try {
                        if (destFile.exists()) destFile.delete();
                        FileIO.copyFile(src, destFile);
                        singleClonedFiles.add(destFile);
                        counts[0]++;
                    } catch (Exception ex) {
                        logError("Failed to clone file " + src.getAbsolutePath() + " to " + destFile.getAbsolutePath(), ex);
                        onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }

                if (!singleClonedFiles.isEmpty()) {
                    onEdtVoid(() -> {
                        if (store.isVirtualDir(currentDir)) store.assignToVirtualDir(singleClonedFiles, currentDir);
                        FXListManager.addEntries(mcreator.getWorkspace().getWorkspaceFolder(), singleClonedFiles);
                    });
                }

                for (File src : sourceFolders) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String currentName = folderCurrentNames.get(src);
                    String cloneName = promptUniqueVirtualFolderName(mcreator, store, currentName, progress, nameResolver);
                    if (cloneName == null) { cancelAllHolder[0] = true; break; }

                    List<File> filesToClone = folderContents.get(src);
                    List<File> clonedFileList = new ArrayList<>();
                    boolean folderFailed = false;
                    for (File f : filesToClone) {
                        FileNameParts parts = FileNameParts.of(f);
                        File destFile = generateUniqueClonePath(rootDir, parts.baseName(), canonicalExtension(parts.extension()));
                        try {
                            FileIO.copyFile(f, destFile);
                            clonedFileList.add(destFile);
                        } catch (Exception ex) {
                            folderFailed = true;
                            logError("Failed to clone file " + f.getAbsolutePath() + " to " + destFile.getAbsolutePath(), ex);
                            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                    progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                            break;
                        }
                    }

                    boolean anyCloned = !clonedFileList.isEmpty();
                    if (anyCloned) {
                        onEdtVoid(() -> {
                            store.createFolder(cloneName);
                            store.assignToVirtualDir(clonedFileList, store.virtualDirRef(cloneName));
                            FXListManager.addEntries(mcreator.getWorkspace().getWorkspaceFolder(), clonedFileList);
                        });
                    }
                    if (anyCloned && !folderFailed) counts[1]++;
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.cloned") + autoRenameNotice(resolver));
                    } else if (!cancelAllHolder[0]) {
                        JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    }
                } finally {
                    browser.endInternalMutation();
                }
            });
        });
        return btn;
    }

    private static JButton buildRenameButtonForVirtualBrowser(MCreator mcreator, VirtualFXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.rename.file"), "16px_photon.rename");
        btn.addActionListener(e -> {
            List<File> fileItems = browser.getSelectedFiles();
            List<File> folderItems = browser.getSelectedFolders().stream()
                    .filter(store::isVirtualDir)
                    .collect(java.util.stream.Collectors.toList());

            if (fileItems.isEmpty() && folderItems.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int total = fileItems.size() + folderItems.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);
            int[] counts = new int[2];
            int[] indexHolder = new int[1];
            boolean[] cancelAllHolder = new boolean[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                for (File item : fileItems) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    RenameOutcome outcome = promptAndRenameFile(mcreator, item, progress, nameResolver, resolver);
                    if (outcome.isCancelled()) { cancelAllHolder[0] = true; break; }
                    if (outcome.isRenamed()) {
                        store.renameFile(item, outcome.dest());
                        FXListManager.renameEntry(mcreator.getWorkspace().getWorkspaceFolder(), item, outcome.dest());
                        counts[0]++;
                    }
                }

                for (File folder : folderItems) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String fullPath = store.virtualDirName(folder);
                    String parent = store.parentOf(fullPath);
                    String currentName = store.lastSegmentOf(fullPath);
                    String newName = promptRenameVirtualFolderName(mcreator, store, parent, currentName, progress, nameResolver);
                    if (newName == null) { cancelAllHolder[0] = true; break; }
                    if (newName.equals(currentName)) continue;
                    store.renameFolder(fullPath, newName);
                    counts[1]++;
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " "
                                + L10N.t("plugin.photon.resourcemenu.action.renamed") + autoRenameNotice(resolver));
                    } else if (!cancelAllHolder[0]) {
                        JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    }
                } finally {
                    browser.endInternalMutation();
                }
            });
        });
        return btn;
    }

    private static String promptUniqueVirtualFolderName(MCreator mcreator, VirtualFolderStore store, String currentFolderName,
                                                        String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = promptCloneFolderName(mcreator, currentFolderName, progress, nameResolver);
            if (name == null) return null;
            if (store.folderExists(name)) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                continue;
            }
            return name;
        }
    }

    private static String promptRenameVirtualFolderName(MCreator mcreator, VirtualFolderStore store, String parent, String currentName,
                                                        String progress, InvalidNameResolver nameResolver) {
        String prefill = currentName;
        while (true) {
            String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message") + "\n"
                    + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + currentName;
            String currentPrefill = prefill;
            String newName = onEdt(() -> JOptionPane.showInputDialog(mcreator, hint, currentPrefill));
            if (newName == null) return null;
            newName = newName.trim();
            if (newName.equals(currentName)) return newName;
            if (newName.isEmpty()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }

            newName = nameResolver.resolve(mcreator, newName, progress, false);
            if (newName == null) continue;
            if (newName.equals(currentName)) return newName;
            String prospectivePath = parent.isEmpty() ? newName : parent + VirtualFolderStore.SEP + newName;
            if (store.folderExists(prospectivePath)) {
                String finalNewName = newName;
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                prefill = newName;
                continue;
            }
            return newName;
        }
    }

    private static File generateUniqueClonePath(File dir, String baseName, String extension) {
        File candidate = new File(dir, baseName + "_copy" + extension);
        int counter = 2;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + "_copy" + counter + extension);
            counter++;
        }
        return candidate;
    }

    private static JButton buildCreateVirtualFolderButton(MCreator mcreator, FXBrowserPanel browser, VirtualFolderStore store) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.create.folder"), "16px_photon.create_folder");
        btn.addActionListener(e -> {
            File currentDir = browser.getCurrentDir();
            String parentPath = store.isVirtualDir(currentDir) ? store.virtualDirName(currentDir) : "";
            InvalidNameResolver nameResolver = new InvalidNameResolver(1);
            while (true) {
                String name = JOptionPane.showInputDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.folder.name.message"),
                        L10N.t("plugin.photon.resourcemenu.folder.create.title"),
                        JOptionPane.QUESTION_MESSAGE);
                if (name == null) return;
                name = name.trim();
                if (name.isEmpty()) return;

                name = nameResolver.resolve(mcreator, name, false);
                if (name == null) continue;
                String fullPath = parentPath.isEmpty() ? name : parentPath + VirtualFolderStore.SEP + name;
                if (store.folderExists(fullPath)) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                store.createFolder(fullPath);
                browser.refresh();
                return;
            }
        });
        return btn;
    }

    private static JButton buildSearchUsagesButton(MCreator mcreator, FXBrowserPanel browser) {
        JButton button = createToolbarButton(L10N.t("plugin.photon.resourcemenu.search.usages"), "16px_photon.search");
        button.addActionListener(e -> {
            List<File> targets = browser.getSelectedFiles();
            if (targets.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.search.usages.no.selection"),
                        L10N.t("plugin.photon.resourcemenu.search.usages.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Map<String, List<ModElement>> usagesMap = new LinkedHashMap<>();
            for (File fxFile : targets) {
                FileNameParts parts = FileNameParts.of(fxFile);
                usagesMap.put(parts.baseName(), new ArrayList<>());
            }
            searchInWorkspace(mcreator, usagesMap);
            showUsagesDialog(mcreator, usagesMap);
        });
        return button;
    }

    private static void searchInWorkspace(MCreator mcreator, Map<String, List<ModElement>> usagesMap) {
        Map<String, ModElement> byName = new HashMap<>();
        for (ModElement me : mcreator.getWorkspace().getModElements())
            byName.put(me.getName(), me);

        List<String> namesByLengthDesc = byName.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(java.util.stream.Collectors.toList());

        String modPackage = mcreator.getWorkspace().getWorkspaceSettings().getModElementsPackage();
        File packageDir = new File(mcreator.getWorkspace().getWorkspaceFolder(),
                "src/main/java/" + modPackage.replace('.', '/'));
        searchSourceDir(packageDir, byName, namesByLengthDesc, usagesMap);
    }

    private static void searchSourceDir(File dir, Map<String, ModElement> byName, List<String> namesByLengthDesc,
                                        Map<String, List<ModElement>> usagesMap) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (!SKIP_DIRS.contains(child.getName()))
                    searchSourceDir(child, byName, namesByLengthDesc, usagesMap);
            } else {
                String fileName = child.getName();
                if (!fileName.endsWith(".java")) continue;
                String baseName = fileName.substring(0, fileName.length() - 5);
                String elementName = matchOwningElementName(baseName, namesByLengthDesc);
                if (elementName == null) continue;
                ModElement owner = byName.get(elementName);
                if (owner != null) checkFileAndAddUsages(child, owner, usagesMap);
            }
        }
    }

    private static String matchOwningElementName(String baseName, List<String> namesByLengthDesc) {
        for (String name : namesByLengthDesc) {
            if (baseName.equals(name)) return name;
            if (baseName.length() > name.length() && baseName.startsWith(name)
                    && Character.isUpperCase(baseName.charAt(name.length()))) {
                return name;
            }
        }
        return null;
    }

    private static void checkFileAndAddUsages(File file, ModElement me, Map<String, List<ModElement>> usagesMap) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Set<String> referencedKeys = extractPhotonFxKeys(content);
            if (referencedKeys.isEmpty()) return;
            for (Map.Entry<String, List<ModElement>> entry : usagesMap.entrySet()) {
                List<ModElement> found = entry.getValue();
                if (!found.contains(me) && referencedKeys.contains(entry.getKey())) found.add(me);
            }
        } catch (Exception e) {
            logWarn("Failed to read " + file.getAbsolutePath() + " while searching FX usages: " + e.getMessage());
        }
    }

    private static final java.util.regex.Pattern FX_ACTION_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:(?:Summon|Remove)FX(?:Entity|Block)(?:Server|Client)\\s*\\.\\s*(?:create|destroy)"
                    + "|CheckFX(?:Entity|Block)\\s*\\.\\s*check)"
                    + "\\s*\\([^;{}]*\\)\\s*\\.\\s*name\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"
    );

    private static Set<String> extractPhotonFxKeys(String content) {
        Set<String> keys = new HashSet<>();
        java.util.regex.Matcher m = FX_ACTION_PATTERN.matcher(content);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    private record UsagesHeaderRow(String text) {}

    private static final Object NO_USAGES_ROW = new Object();

    private static void showUsagesDialog(MCreator mcreator, Map<String, List<ModElement>> usagesMap) {
        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.resourcemenu.search.usages.title"), true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 380);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(true);
        DefaultListModel<Object> listModel = new DefaultListModel<>();

        for (Map.Entry<String, List<ModElement>> entry : usagesMap.entrySet()) {
            listModel.addElement(new UsagesHeaderRow(entry.getKey() + FX_EXTENSION));
            if (entry.getValue().isEmpty()) {
                listModel.addElement(NO_USAGES_ROW);
            } else {
                entry.getValue().forEach(listModel::addElement);
            }
        }

        JList<Object> resultList = new JList<>(listModel);
        resultList.setCellRenderer(new ModElementResultRenderer());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setOpaque(false);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                Object selected = resultList.getSelectedValue();
                if (!(selected instanceof ModElement me)) return;
                dialog.dispose();
                try {
                    ModElementGUI<?> gui = me.getType().getModElementGUI(mcreator, me, true);
                    if (gui != null) gui.showView();
                } catch (Exception ex) {
                    logError("Failed to open mod element editor for " + me.getName(), ex);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        dialog.add(scroll, BorderLayout.CENTER);

        JButton closeBtn = new JButton(L10N.t("common.close"));
        closeBtn.addActionListener(ev -> dialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnPanel.setOpaque(false);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static class ModElementResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value == NO_USAGES_ROW) {
                setText("    " + L10N.t("plugin.photon.resourcemenu.search.usages.no.usages"));
                setFont(getFont().deriveFont(Font.ITALIC, 11f));
                setIcon(null);
                setForeground(isSelected ? Color.WHITE : Color.GRAY);
                setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            } else if (value instanceof UsagesHeaderRow header) {
                setText(header.text());
                setFont(getFont().deriveFont(Font.BOLD, 12f));
                setIcon(null);
                setForeground(isSelected ? Color.WHITE : new Color(210, 210, 210));
                setBorder(BorderFactory.createEmptyBorder(8, 6, 3, 6));
            } else if (value instanceof ModElement me) {
                setText(me.getName());
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
                setForeground(isSelected ? Color.WHITE : new Color(150, 210, 110));
                setBorder(BorderFactory.createEmptyBorder(2, 22, 2, 6));
                setIcon(resolveModElementIcon(me));
            }
            return this;
        }

        private static Icon resolveModElementIcon(ModElement me) {
            try {
                Icon icon = me.getType().getIcon();
                if (icon != null) return icon;
            } catch (Exception ignored) {}
            try { return UIRES.get("16px." + me.getType().getRegistryName()); } catch (Exception ignored) {}
            return null;
        }
    }
}
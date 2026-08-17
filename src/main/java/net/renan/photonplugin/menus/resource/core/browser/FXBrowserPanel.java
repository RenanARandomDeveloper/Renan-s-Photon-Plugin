package net.renan.photonplugin.menus.resource.core.browser;

import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class FXBrowserPanel extends JPanel {
    List<Supplier<File>> rootSuppliers;
    protected final String extension;
    protected final Set<String> displayExtensions;
    protected final Function<File, Icon> iconProvider;
    protected File currentDir;
    Deque<File> navHistory = new ArrayDeque<>();
    protected final Set<File> selectedFiles = new LinkedHashSet<>();
    protected final Set<File> selectedFolders = new LinkedHashSet<>();
    protected ItemCard lastSelected = null;
    protected String filterText = "";
    protected FilterType filterType = FilterType.ALL;
    private final JButton btnUp;
    protected final JPanel breadcrumbPanel;
    protected final JPanel grid;
    protected final List<ItemCard> allCards = new ArrayList<>();
    private final JScrollPane scroll;
    private final AtomicInteger activeInternalMutations = new AtomicInteger(0);
    private volatile long suppressWatcherUntilMillis = 0L;
    private static final long WATCHER_ECHO_GRACE_MILLIS = 800L;
    private BiConsumer<File, File> postMoveHook = null;

    static final File VIRTUAL_ROOT_SENTINEL = new File("\0");

    public void setPostMoveHook(BiConsumer<File, File> hook) {
        this.postMoveHook = hook;
    }

    private BooleanSupplier dragAndDropEnabledSupplier = () -> true;

    public void setDragAndDropEnabled(boolean enabled) {
        this.dragAndDropEnabledSupplier = () -> enabled;
    }

    public void setDragAndDropEnabledSupplier(BooleanSupplier supplier) {
        this.dragAndDropEnabledSupplier = (supplier != null) ? supplier : (() -> true);
    }

    public boolean isDragAndDropEnabled() {
        return dragAndDropEnabledSupplier.getAsBoolean();
    }

    public void beginInternalMutation() {
        activeInternalMutations.incrementAndGet();
    }

    public void endInternalMutation() {
        if (activeInternalMutations.decrementAndGet() <= 0) {
            activeInternalMutations.set(0);
            suppressWatcherUntilMillis = System.currentTimeMillis() + WATCHER_ECHO_GRACE_MILLIS;
        }
    }

    public boolean isWatcherRefreshSuppressed() {
        return activeInternalMutations.get() > 0 || System.currentTimeMillis() < suppressWatcherUntilMillis;
    }

    public String getExtension() {
        return extension;
    }

    public JPanel getGrid() {
        return grid;
    }

    public FXBrowserPanel(List<Supplier<File>> rootSuppliers, String extension, Function<File, Icon> iconProvider) {
        this(rootSuppliers, extension, extension != null ? Set.of(extension) : null, iconProvider);
    }

    public FXBrowserPanel(List<Supplier<File>> rootSuppliers, String extension, Set<String> displayExtensions,
                          Function<File, Icon> iconProvider) {
        super(new BorderLayout(0, 0));
        this.rootSuppliers = new ArrayList<>(rootSuppliers);
        this.extension = extension;
        this.displayExtensions = (displayExtensions != null) ? new LinkedHashSet<>(displayExtensions) : null;
        this.iconProvider = iconProvider;
        setOpaque(false);

        currentDir = (rootSuppliers.size() == 1) ? rootSuppliers.get(0).get() : null;

        btnUp = new JButton(L10N.t("plugin.photon.resourcemenu.action.back"));
        btnUp.setFocusable(false);
        btnUp.setEnabled(false);
        ComponentUtils.deriveFont(btnUp, 11f);
        btnUp.addActionListener(e -> navigateUp());

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        breadcrumbPanel.setOpaque(false);
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        JPanel navBar = new JPanel(new BorderLayout(4, 0));
        navBar.setOpaque(false);
        navBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
        navBar.add(btnUp, BorderLayout.WEST);
        navBar.add(breadcrumbPanel, BorderLayout.CENTER);

        FileDropSupport.install(btnUp, this, "the 'up' navigation button",
                () -> navHistory.isEmpty() ? null : navHistory.peek(),
                () -> !navHistory.isEmpty(), null, null);

        grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 8));
        grid.setOpaque(false);
        grid.setFocusable(true);
        JPanel wrapper = new ScrollableViewportPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(grid, BorderLayout.NORTH);

        MouseAdapter deselect = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearSelection();
                grid.requestFocusInWindow();
            }
        };
        grid.addMouseListener(deselect);
        wrapper.addMouseListener(deselect);

        scroll = new JScrollPane(wrapper);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        RubberBandSelectionLayer rubberBandLayer = new RubberBandSelectionLayer(this, scroll);
        JLayeredPane layered = new JLayeredPane();
        layered.add(scroll, JLayeredPane.DEFAULT_LAYER);
        layered.add(rubberBandLayer, JLayeredPane.PALETTE_LAYER);
        layered.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent ev) {
                int w = layered.getWidth(), h = layered.getHeight();
                scroll.setBounds(0, 0, w, h);
                rubberBandLayer.setBounds(0, 0, w, h);
            }
        });

        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        northContainer.setOpaque(false);
        northContainer.add(navBar);
        if (rootSuppliers.size() > 1) {
            northContainer.add(RootShortcutsBar.build(this));
        }

        add(northContainer, BorderLayout.NORTH);
        add(layered, BorderLayout.CENTER);
        setupGridKeyNavigation();
        refresh();
    }

    private void setupGridKeyNavigation() {
        InputMap inputMap = grid.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = grid.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "photon.navLeft");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "photon.navRight");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "photon.navUp");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "photon.navDown");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "photon.selectAll");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "photon.clearSelection");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "photon.openSelected");

        actionMap.put("photon.navLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateGrid(-1, 0);
            }
        });
        actionMap.put("photon.navRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateGrid(1, 0);
            }
        });
        actionMap.put("photon.navUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateGrid(0, -1);
            }
        });
        actionMap.put("photon.navDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateGrid(0, 1);
            }
        });
        actionMap.put("photon.selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllVisible();
            }
        });
        actionMap.put("photon.clearSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearSelection();
            }
        });
        actionMap.put("photon.openSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedFolder();
            }
        });
    }

    private void openSelectedFolder() {
        if (lastSelected instanceof FolderCard && allCards.contains(lastSelected)) {
            navigate(lastSelected.getFile());
        }
    }

    void navigateGrid(int dCol, int dRow) {
        if (allCards.isEmpty()) return;
        if (lastSelected == null || !allCards.contains(lastSelected)) {
            selectCardExclusive(allCards.get(0));
            return;
        }
        ItemCard target = (dRow != 0) ? adjacentByRow(lastSelected, dRow) : adjacentByFlow(lastSelected, dCol);
        if (target != null) selectCardExclusive(target);
    }

    private ItemCard adjacentByFlow(ItemCard current, int delta) {
        int idx = allCards.indexOf(current);
        int next = idx + delta;
        return (next >= 0 && next < allCards.size()) ? allCards.get(next) : null;
    }

    private ItemCard adjacentByRow(ItemCard current, int rowDelta) {
        List<List<ItemCard>> rows = new ArrayList<>();
        for (ItemCard c : allCards) {
            List<ItemCard> lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (lastRow != null && lastRow.get(0).getY() == c.getY()) {
                lastRow.add(c);
            } else {
                List<ItemCard> newRow = new ArrayList<>();
                newRow.add(c);
                rows.add(newRow);
            }
        }

        int currentRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(current)) {
                currentRow = i;
                break;
            }
        }
        int targetRow = currentRow + rowDelta;
        if (currentRow < 0 || targetRow < 0 || targetRow >= rows.size()) return null;

        int currentCenterX = current.getX() + current.getWidth() / 2;
        List<ItemCard> candidates = rows.get(targetRow);
        ItemCard best = candidates.get(0);
        int bestDist = Math.abs(best.getX() + best.getWidth() / 2 - currentCenterX);
        for (ItemCard c : candidates) {
            int dist = Math.abs(c.getX() + c.getWidth() / 2 - currentCenterX);
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    private void selectCardExclusive(ItemCard card) {
        clearSelection();
        if (card instanceof FolderCard) {
            selectedFolders.add(card.getFile());
        } else if (card instanceof FileCard) {
            selectedFiles.add(card.getFile());
        }
        card.setSelected(true);
        lastSelected = card;
        grid.requestFocusInWindow();
        card.scrollRectToVisible(new Rectangle(0, 0, card.getWidth(), card.getHeight()));
    }

    public String getFolderName(File dir) {
        return dir != null ? dir.getName() : "";
    }

    public String getFolderTooltip(File dir) {
        return dir != null ? dir.getAbsolutePath() : "";
    }

    public void navigate(File dir) {
        navHistory.push(currentDir != null ? currentDir : VIRTUAL_ROOT_SENTINEL);
        currentDir = dir;
        clearSelection();
        refresh();
    }

    public void navigateUp() {
        if (navHistory.isEmpty()) return;
        File prev = navHistory.pop();
        currentDir = (prev == VIRTUAL_ROOT_SENTINEL) ? null : prev;
        clearSelection();
        refresh();
    }

    public void navigateDirect(File target) {
        if (Objects.equals(target, currentDir)) return;
        List<File> path = new ArrayList<>(navHistory);
        Collections.reverse(path);
        path.add(currentDir);
        int idx = path.indexOf(target);
        if (idx < 0) {
            navigate(target);
            return;
        }
        navHistory.clear();
        for (int i = 0; i < idx; i++) {
            navHistory.push(path.get(i));
        }
        currentDir = target;
        clearSelection();
        refresh();
    }

    protected void handleFolderCardClick(FolderCard card, MouseEvent e) {
        boolean ctrl = e.isControlDown() || e.isMetaDown();
        boolean shift = e.isShiftDown();
        if (shift && lastSelected != null) {
            selectRange(lastSelected, card, ctrl);
        } else if (ctrl) {
            File dir = card.getFile();
            if (selectedFolders.contains(dir)) {
                selectedFolders.remove(dir);
                card.setSelected(false);
            } else {
                selectedFolders.add(dir);
                card.setSelected(true);
            }
            lastSelected = card;
        } else {
            clearSelection();
            selectedFolders.add(card.getFile());
            card.setSelected(true);
            lastSelected = card;
        }
        grid.requestFocusInWindow();
    }

    protected void handleFileCardClick(FileCard card, MouseEvent e) {
        boolean ctrl = e.isControlDown() || e.isMetaDown();
        boolean shift = e.isShiftDown();
        if (shift && lastSelected != null) {
            selectRange(lastSelected, card, ctrl);
        } else if (ctrl) {
            if (selectedFiles.contains(card.getFile())) {
                selectedFiles.remove(card.getFile());
                card.setSelected(false);
            } else {
                selectedFiles.add(card.getFile());
                card.setSelected(true);
            }
            lastSelected = card;
        } else {
            clearSelection();
            selectedFiles.add(card.getFile());
            card.setSelected(true);
            lastSelected = card;
        }
        grid.requestFocusInWindow();
    }

    private void selectRange(ItemCard anchor, ItemCard target, boolean additive) {
        int anchorIdx = allCards.indexOf(anchor);
        int targetIdx = allCards.indexOf(target);
        if (anchorIdx < 0 || targetIdx < 0) {
            clearSelection();
            if (target instanceof FolderCard) {
                selectedFolders.add(target.getFile());
            } else if (target instanceof FileCard) {
                selectedFiles.add(target.getFile());
            }
            target.setSelected(true);
            lastSelected = target;
            return;
        }

        int lo = Math.min(anchorIdx, targetIdx);
        int hi = Math.max(anchorIdx, targetIdx);

        if (!additive) {
            allCards.forEach(c -> c.setSelected(false));
            selectedFiles.clear();
            selectedFolders.clear();
        }
        for (int i = lo; i <= hi; i++) {
            ItemCard c = allCards.get(i);
            c.setSelected(true);
            if (c instanceof FolderCard) {
                selectedFolders.add(c.getFile());
            } else if (c instanceof FileCard) {
                selectedFiles.add(c.getFile());
            }
        }
        grid.repaint();
    }

    protected void clearSelection() {
        allCards.forEach(c -> c.setSelected(false));
        selectedFiles.clear();
        selectedFolders.clear();
        lastSelected = null;
        grid.repaint();
    }

    protected void selectAllVisible() {
        if (allCards.isEmpty()) return;
        selectedFiles.clear();
        selectedFolders.clear();
        for (ItemCard c : allCards) {
            c.setSelected(true);
            if (c instanceof FolderCard) selectedFolders.add(c.getFile());
            else if (c instanceof FileCard) selectedFiles.add(c.getFile());
        }
        lastSelected = allCards.get(allCards.size() - 1);
        grid.repaint();
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    public List<File> getSelectedFolders() {
        return new ArrayList<>(selectedFolders);
    }

    protected boolean containsSelectedFile(File f) {
        return selectedFiles.contains(f);
    }

    protected boolean containsSelectedFolder(File f) {
        return selectedFolders.contains(f);
    }

    protected void clearSelectionExposed() {
        clearSelection();
    }

    public void setFilter(String text) {
        filterText = (text == null) ? "" : text.trim().toLowerCase();
        refresh();
    }

    public void setFilterType(FilterType type) {
        filterType = (type == null) ? FilterType.ALL : type;
        refresh();
    }

    public FilterType getFilterType() {
        return filterType;
    }

    protected boolean isFolderItem(File f) {
        return f != null && f.isDirectory();
    }

    protected boolean isFileItem(File f) {
        return f != null && f.isFile();
    }

    public int performMove(List<File> filesToMove, File destDir) {
        int conflictCount = BulkConflictResolver.countConflicts(filesToMove, destDir);
        return performMove(filesToMove, destDir, new BulkConflictResolver(conflictCount));
    }

    public int performMove(List<File> filesToMove, File destDir, BulkConflictResolver resolver) {
        if (destDir == null || !destDir.isDirectory()) return 0;
        String destAbs = destDir.getAbsolutePath();
        int count = 0;
        for (File f : filesToMove) {
            if (f == null || !f.exists()) continue;
            if (f.getParentFile() != null && f.getParentFile().getAbsolutePath().equals(destAbs)) continue;
            File dest = new File(destDir, f.getName());
            boolean isDir = f.isDirectory();
            if (isDir) {
                String srcAbs = f.getAbsolutePath();
                if (destAbs.equals(srcAbs) || destAbs.startsWith(srcAbs + File.separator)) continue;
            }
            if (dest.exists()) {
                ConflictResolution resolution = resolver.resolve(this, dest);
                if (resolution.action() == ConflictResolution.ACTION_CANCEL) break;
                if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                dest = resolution.targetFile();
                if (dest.exists()) {
                    boolean removed = isDir ? FileOps.deleteRecursively(dest) : dest.delete();
                    if (!removed) {
                        MenuLog.warn("Could not remove conflicting destination before move: " + dest.getAbsolutePath());
                        continue;
                    }
                }
            }
            File finalDest = dest;
            File source = f;
            if (FileOps.moveFileOrDirectory(source, finalDest)) {
                count++;
                if (postMoveHook != null) postMoveHook.accept(source, finalDest);
            }
        }
        return count;
    }

    public void performMoveWithFeedback(List<File> filesToMove, File destDir) {
        if (filesToMove == null || filesToMove.isEmpty() || destDir == null) return;

        List<File> files = new ArrayList<>();
        List<File> folders = new ArrayList<>();
        for (File f : filesToMove) {
            if (isFolderItem(f)) folders.add(f);
            else if (isFileItem(f)) files.add(f);
        }

        if (files.isEmpty() && folders.isEmpty()) return;
        int conflictCount = BulkConflictResolver.countConflicts(files, destDir)
                + BulkConflictResolver.countConflicts(folders, destDir);
        BulkConflictResolver resolver = new BulkConflictResolver(conflictCount);
        int[] counts = new int[2];

        beginInternalMutation();
        EdtSupport.runFileTaskInBackground(this, () -> {
            counts[0] = files.isEmpty() ? 0 : performMove(files, destDir, resolver);
            counts[1] = (folders.isEmpty() || resolver.wasCancelled())
                    ? 0 : performMove(folders, destDir, resolver);
        }, () -> {
            try {
                if (counts[0] + counts[1] > 0) {
                    refresh();
                    JOptionPane.showMessageDialog(this,
                            MenuMessages.buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                                    + " \u2192 \"" + getFolderName(destDir) + "\"" + BulkConflictResolver.autoRenameNotice(resolver));
                } else if (resolver.wasCancelled()) {
                    JOptionPane.showMessageDialog(this, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                }
            } finally {
                endInternalMutation();
            }
        });
    }

    private boolean matchesDisplayExtension(File file) {
        if (displayExtensions == null) return true;
        for (String ext : displayExtensions) {
            if (FileNameRules.hasExtension(file, ext)) return true;
        }
        return false;
    }

    public void refresh() {
        List<File> roots = new ArrayList<>();
        for (Supplier<File> s : rootSuppliers) roots.add(s.get());
        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();

        if (currentDir == null) {
            dirs.addAll(roots);
        } else {
            File[] entries = currentDir.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory()) {
                        dirs.add(entry);
                    } else if (entry.isFile() && matchesDisplayExtension(entry)) {
                        files.add(entry);
                    }
                }
            }
            dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        if (!filterText.isEmpty()) {
            dirs.removeIf(d -> !d.getName().toLowerCase().contains(filterText));
            files.removeIf(f -> !f.getName().toLowerCase().contains(filterText));
        }
        if (filterType == FilterType.FILE) dirs.clear();
        else if (filterType == FilterType.FOLDER) files.clear();

        Set<File> prevFiles = new HashSet<>(selectedFiles);
        Set<File> prevFolders = new HashSet<>(selectedFolders);
        selectedFiles.clear();
        selectedFolders.clear();
        lastSelected = null;
        grid.removeAll();
        allCards.clear();

        for (File dir : dirs) {
            FolderCard card = new FolderCard(dir, this);
            if (prevFolders.contains(dir)) {
                card.setSelected(true);
                selectedFolders.add(dir);
                lastSelected = card;
            }
            grid.add(card);
            allCards.add(card);
        }
        for (File file : files) {
            FileCard card = new FileCard(file, this, iconProvider);
            if (prevFiles.contains(file)) {
                card.setSelected(true);
                selectedFiles.add(file);
                lastSelected = card;
            }
            grid.add(card);
            allCards.add(card);
        }
        grid.revalidate();
        grid.repaint();
        updateNavBar(roots);
    }

    protected void updateNavBar(List<File> roots) {
        btnUp.setEnabled(!navHistory.isEmpty());
        rebuildBreadcrumbPanel(roots);
    }

    protected void rebuildBreadcrumbPanel(List<File> roots) {
        breadcrumbPanel.removeAll();
        List<BreadcrumbBuilder.Segment> segments = BreadcrumbBuilder.buildSegments(
                currentDir, roots, rootSuppliers.size(), this::getFolderName);

        for (int i = 0; i < segments.size(); i++) {
            final File segDir = segments.get(i).dir();
            final String label = segments.get(i).label();
            boolean isCurrent = (i == segments.size() - 1);

            JLabel lbl = new JLabel(label);
            ComponentUtils.deriveFont(lbl, 11f);
            if (isCurrent) {
                lbl.setForeground(new Color(200, 200, 200));
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            } else {
                lbl.setForeground(MenuTheme.accentColor());
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                lbl.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) navigateDirect(segDir);
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        lbl.setForeground(MenuTheme.shade(MenuTheme.accentColor(), 30));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        lbl.setForeground(MenuTheme.accentColor());
                    }
                });
                FileDropSupport.install(lbl, this, "breadcrumb '" + lbl.getText() + "'",
                        () -> segDir, null, null, null);
            }
            breadcrumbPanel.add(lbl);
            if (i < segments.size() - 1) {
                JLabel sep = new JLabel(" / ");
                sep.setForeground(new Color(90, 90, 90));
                ComponentUtils.deriveFont(sep, 11f);
                breadcrumbPanel.add(sep);
            }
        }
        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }
}
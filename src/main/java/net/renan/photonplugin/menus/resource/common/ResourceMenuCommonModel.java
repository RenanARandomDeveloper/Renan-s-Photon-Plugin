package net.renan.photonplugin.menus.resource.common;

import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.Log;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public final class ResourceMenuCommonModel {

    public static final String OBJ_EXTENSION = ".obj";
    public static final String MTL_EXTENSION = ".mtl";
    public static final String JSON_EXTENSION = ".json";

    private ResourceMenuCommonModel() {}

    @FunctionalInterface
    public interface CheckedBiConsumer<T, U> {
        void accept(T t, U u) throws Exception;
    }

    public static File mtlFileFor(File objFile) {
        return new File(objFile.getParentFile(), FileNameParts.of(objFile).baseName() + MTL_EXTENSION);
    }

    public static String relativeSubPath(File root, File target) {
        if (target == null) return "";
        Path rootPath = canonicalPath(root);
        Path targetPath = canonicalPath(target);
        if (!targetPath.startsWith(rootPath) || targetPath.equals(rootPath)) return "";
        return rootPath.relativize(targetPath).toString();
    }

    public static Path canonicalPath(File file) {
        try {
            return file.getCanonicalFile().toPath();
        } catch (IOException ex) {
            return file.toPath().toAbsolutePath().normalize();
        }
    }

    public static File mirrorJsonFileFor(File objRoot, File jsonRoot, File objFile) {
        String relDir = relativeSubPath(objRoot, objFile.getParentFile());
        File dir = relDir.isEmpty() ? jsonRoot : new File(jsonRoot, relDir);
        return new File(dir, FileNameParts.of(objFile).baseName() + JSON_EXTENSION);
    }

    public static File mirrorJsonFolderFor(File objRoot, File jsonRoot, File objFolder) {
        String relDir = relativeSubPath(objRoot, objFolder);
        return relDir.isEmpty() ? jsonRoot : new File(jsonRoot, relDir);
    }

    public static JPanel assembleModelPanel(FXBrowserPanel browser, Supplier<File> watchDirGetter,
                                            Component... toolbarItems) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();
        for (Component item : toolbarItems) {
            toolBar.add(item);
        }
        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, watchDirGetter);

        return panel;
    }

    public static JButton buildModelDeleteButtonForBrowser(MCreator mcreator, FXBrowserPanel browser,
                                                           Supplier<File> objDirGetter, Supplier<File> jsonDirGetter) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.delete.files"), "16px_photon.delete");
        Runnable action = () -> performModelDeleteSelected(mcreator, browser, objDirGetter, jsonDirGetter);
        btn.addActionListener(e -> action.run());
        bindDeleteKey(mcreator, browser, action);
        return btn;
    }

    private static void performModelDeleteSelected(MCreator mcreator, FXBrowserPanel browser,
                                                   Supplier<File> objDirGetter, Supplier<File> jsonDirGetter) {
        List<File> fileTargets = browser.getSelectedFiles();
        List<File> folderTargets = browser.getSelectedFolders().stream()
                .filter(File::isDirectory)
                .collect(Collectors.toList());
        if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String msg = buildDeleteConfirmationMessage(browser, fileTargets, folderTargets);
        int choice = JOptionPane.showConfirmDialog(
                mcreator, msg,
                L10N.t("plugin.photon.resourcemenu.operation.warning"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
            return;
        }

        File objRoot = objDirGetter.get();
        File jsonRoot = jsonDirGetter.get();
        int[] counts = new int[2];

        browser.beginInternalMutation();
        runFileTaskInBackground(mcreator, () -> {
            for (File f : fileTargets) {
                boolean isJsonSource = f.getName().toLowerCase().endsWith(JSON_EXTENSION);
                if (isJsonSource) {
                    if (f.delete()) counts[0]++;
                    continue;
                }
                File mtl = mtlFileFor(f);
                File json = mirrorJsonFileFor(objRoot, jsonRoot, f);
                if (f.delete()) {
                    if (mtl.exists()) mtl.delete();
                    if (json.exists()) json.delete();
                    counts[0]++;
                }
            }
            for (File folder : folderTargets) {
                File jsonFolder = mirrorJsonFolderFor(objRoot, jsonRoot, folder);
                if (deleteRecursively(folder)) {
                    if (jsonFolder.exists()) deleteRecursively(jsonFolder);
                    counts[1]++;
                }
            }
        }, () -> {
            try {
                if (counts[0] + counts[1] > 0) {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.deleted"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
    }

    public static boolean renameModelFolder(MCreator mcreator, FXBrowserPanel browser, File folder, String progress,
                                            InvalidNameResolver nameResolver, BulkConflictResolver resolver,
                                            boolean[] cancelAllHolder, BiConsumer<File, File> jsonFolderRelocateHook) {
        String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message") + "\n"
                + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + browser.getFolderName(folder);
        String newName = onEdt(() -> JOptionPane.showInputDialog(mcreator, hint, browser.getFolderName(folder)));
        if (newName == null) { cancelAllHolder[0] = true; return false; }
        newName = newName.trim();
        if (newName.isEmpty()) {
            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return false;
        }

        newName = nameResolver.resolve(mcreator, newName, progress, false);
        if (newName == null) return false;

        File dest = new File(folder.getParentFile(), newName);
        if (isSamePathExact(dest, folder)) return false;

        if (!isSamePathIgnoreCase(dest, folder) && dest.exists()) {
            ConflictResolution resolution = resolver.resolve(mcreator, dest);
            if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; return false; }
            if (resolution.action() == ACTION_SKIP) return false;
            dest = resolution.targetFile();
            if (dest.exists() && !deleteRecursively(dest)) {
                Log.warn("Could not remove conflicting destination before rename: " + dest.getAbsolutePath());
                return false;
            }
        }

        File finalDest = dest;
        if (!renameFile(folder, finalDest)) {
            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + browser.getFolderName(folder),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return false;
        }

        jsonFolderRelocateHook.accept(folder, finalDest);
        return true;
    }

    public static JButton buildModelRenameButtonForBrowser(MCreator mcreator, FXBrowserPanel browser,
                                                           BiConsumer<File, File> jsonFolderRelocateHook,
                                                           BiConsumer<File, File> fileSyncHook) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.rename.file"), "16px_photon.rename");
        btn.addActionListener(e -> {
            List<File> allItems = new ArrayList<>(browser.getSelectedFiles());
            allItems.addAll(browser.getSelectedFolders());

            if (allItems.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int total = allItems.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);
            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                for (int i = 0; i < total; i++) {
                    if (cancelAllHolder[0]) break;
                    File item = allItems.get(i);
                    String progress = "(" + (i + 1) + " / " + total + ")";

                    if (item.isDirectory()) {
                        if (renameModelFolder(mcreator, browser, item, progress, nameResolver, resolver, cancelAllHolder, jsonFolderRelocateHook)) {
                            counts[1]++;
                        }
                    } else {
                        RenameOutcome outcome = promptAndRenameFile(mcreator, item, progress, nameResolver, resolver);
                        if (outcome.isCancelled()) { cancelAllHolder[0] = true; break; }
                        if (outcome.isRenamed()) {
                            fileSyncHook.accept(item, outcome.dest());
                            counts[0]++;
                        }
                    }
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

    public static JButton buildModelMoveButtonForBrowser(MCreator mcreator, FXBrowserPanel browser,
                                                         Supplier<File> pickerRootGetter,
                                                         BiConsumer<File, File> folderRelocateHook,
                                                         BiConsumer<File, File> fileSyncHook) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.move.files"), "16px_photon.move");
        btn.addActionListener(e -> {
            List<File> selFiles = browser.getSelectedFiles();
            List<File> selFolders = browser.getSelectedFolders();
            if (selFiles.isEmpty() && selFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            final List<File> itemsToMove;
            if (!selFiles.isEmpty() && !selFolders.isEmpty()) {
                String optFiles = L10N.t("plugin.photon.resourcemenu.move.type.files") + " (" + selFiles.size() + ")";
                String optFolders = L10N.t("plugin.photon.resourcemenu.move.type.folders") + " (" + selFolders.size() + ")";
                String optBoth = L10N.t("plugin.photon.resourcemenu.move.type.both");
                Object[] options = { optFiles, optFolders, optBoth };
                int typeChoice = JOptionPane.showOptionDialog(
                        mcreator, L10N.t("plugin.photon.resourcemenu.move.type.question"),
                        L10N.t("plugin.photon.resourcemenu.move.target.title"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, options, options[0]);
                if (typeChoice == JOptionPane.CLOSED_OPTION) return;
                if (typeChoice == 0) {
                    itemsToMove = new ArrayList<>(selFiles);
                } else if (typeChoice == 1) {
                    itemsToMove = new ArrayList<>(selFolders);
                } else {
                    itemsToMove = new ArrayList<>(selFiles);
                    itemsToMove.addAll(selFolders);
                }
            } else if (!selFiles.isEmpty()) {
                itemsToMove = new ArrayList<>(selFiles);
            } else {
                itemsToMove = new ArrayList<>(selFolders);
            }

            if (itemsToMove.isEmpty()) return;
            File root = pickerRootGetter.get();
            if (!root.exists()) {
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.move.no.base.dir"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            Set<String> excludedPaths = new HashSet<>();
            for (File f : itemsToMove) excludedPaths.add(f.getAbsolutePath());
            File destDir = showFolderPickerDialog(mcreator, root, root.getName(), excludedPaths);
            if (destDir == null) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                return;
            }
            if (!destDir.exists()) destDir.mkdirs();

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            int conflictCount = BulkConflictResolver.countConflicts(itemsToMove, destDir);
            BulkConflictResolver resolver = new BulkConflictResolver(conflictCount);

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                for (File f : itemsToMove) {
                    if (cancelAllHolder[0]) break;
                    boolean isDir = f.isDirectory();
                    File dest = new File(destDir, f.getName());
                    if (isSamePathExact(dest, f)) continue;
                    if (dest.exists() && !isSamePathIgnoreCase(dest, f)) {
                        ConflictResolution resolution = resolver.resolve(mcreator, dest);
                        if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                        if (resolution.action() == ACTION_SKIP) continue;
                        dest = resolution.targetFile();
                        if (dest.exists()) {
                            boolean removed = isDir ? deleteRecursively(dest) : dest.delete();
                            if (!removed) {
                                Log.warn("Could not remove conflicting destination before move: " + dest.getAbsolutePath());
                                continue;
                            }
                        }
                    }
                    File finalDest = dest;
                    File source = f;
                    if (renameFile(source, finalDest)) {
                        if (isDir) {
                            folderRelocateHook.accept(source, finalDest);
                            counts[1]++;
                        } else {
                            fileSyncHook.accept(source, finalDest);
                            counts[0]++;
                        }
                    }
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                                + " \u2192 \"" + browser.getFolderName(destDir) + "\"" + autoRenameNotice(resolver));
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

    public static JButton buildModelCloneButtonForBrowser(MCreator mcreator, FXBrowserPanel browser,
                                                          Supplier<File> cloneRootGetter,
                                                          CheckedBiConsumer<File, File> jsonCloneHook) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.clone.element"), "16px_photon.clone");
        btn.addActionListener(e -> {
            List<File> sourceFiles = browser.getSelectedFiles();
            if (sourceFiles.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File cloneRoot = cloneRootGetter.get();
            File currentDir = onEdt(browser::getCurrentDir);
            File cloneDestDir = (currentDir != null && currentDir.exists()) ? currentDir : cloneRoot;
            int total = sourceFiles.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);
            int[] counts = new int[1];
            boolean[] cancelAllHolder = new boolean[1];
            int[] indexHolder = new int[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                for (File src : sourceFiles) {
                    if (cancelAllHolder[0]) break;
                    FileNameParts parts = FileNameParts.of(src);
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String cloneName = promptCloneName(mcreator, parts, progress, nameResolver);
                    if (cloneName == null) { cancelAllHolder[0] = true; break; }

                    File destDir = cloneDestDir;
                    boolean isJsonSource = src.getName().toLowerCase().endsWith(JSON_EXTENSION);
                    String destExtension = isJsonSource ? JSON_EXTENSION : OBJ_EXTENSION;

                    File destFile = new File(destDir, cloneName + destExtension);
                    ConflictResolution resolution = resolver.resolve(mcreator, destFile);
                    if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                    if (resolution.action() == ACTION_SKIP) continue;
                    destFile = resolution.targetFile();

                    try {
                        if (isJsonSource) {
                            if (destFile.exists()) destFile.delete();
                            FileIO.copyFile(src, destFile);
                        } else {
                            File destObj = destFile;
                            if (destObj.exists()) destObj.delete();
                            FileIO.copyFile(src, destObj);

                            String destBaseName = FileNameParts.of(destObj).baseName();
                            File srcMtl = mtlFileFor(src);
                            File destMtl = new File(destDir, destBaseName + MTL_EXTENSION);
                            if (srcMtl.exists()) {
                                if (destMtl.exists()) destMtl.delete();
                                FileIO.copyFile(srcMtl, destMtl);
                            }

                            jsonCloneHook.accept(src, destObj);
                        }
                        counts[0]++;
                    } catch (Exception ex) {
                        File finalDestFile = destFile;
                        Log.error("Failed to clone model '" + src.getAbsolutePath() + "' to '" + finalDestFile.getAbsolutePath() + "'", ex);
                        onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }
            }, () -> {
                try {
                    if (counts[0] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(counts[0], 0) + " "
                                + L10N.t("plugin.photon.resourcemenu.action.cloned") + autoRenameNotice(resolver));
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
}
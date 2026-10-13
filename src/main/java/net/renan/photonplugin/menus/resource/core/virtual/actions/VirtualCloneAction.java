package net.renan.photonplugin.menus.resource.core.virtual.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserPanel;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualBrowserSupport;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualFolderStore;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualResourceProfile;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.CloneAction.promptCloneFolderName;
import static net.renan.photonplugin.menus.resource.core.actions.CloneAction.promptCloneName;
import static net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver.autoRenameNotice;
import static net.renan.photonplugin.menus.resource.core.naming.FileNameRules.canonicalExtension;
import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.onEdtVoid;
import static net.renan.photonplugin.menus.resource.core.support.EdtSupport.runFileTaskInBackground;
import static net.renan.photonplugin.menus.resource.core.support.MenuMessages.buildItemLabel;

public final class VirtualCloneAction {

    private VirtualCloneAction() {
    }

    public static JButton build(MCreator mcreator, VirtualBrowserPanel browser, VirtualFolderStore store,
                                Supplier<File> targetDirGetter, VirtualResourceProfile profile) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.clone.element"), "16px_photon.clone");
        btn.addActionListener(e -> {
            List<File> sourceFiles = browser.getSelectedFiles();
            List<File> sourceFolders = VirtualBrowserSupport.selectedVirtualFolders(browser, store);
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

            Map<File, LinkedHashMap<String, List<File>>> folderSubtrees = new LinkedHashMap<>();
            Map<File, String> folderCurrentNames = new LinkedHashMap<>();
            Map<File, String> folderParentPaths = new LinkedHashMap<>();
            for (File src : sourceFolders) {
                folderSubtrees.put(src, store.snapshotSubtree(src));
                String fullPath = store.virtualDirName(src);
                folderCurrentNames.put(src, store.lastSegmentOf(fullPath));
                folderParentPaths.put(src, store.parentOf(fullPath));
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
                    if (cloneName == null) {
                        cancelAllHolder[0] = true;
                        break;
                    }

                    File destFile = new File(rootDir, cloneName + canonicalExtension(parts.extension()));
                    ConflictResolution resolution = resolver.resolve(mcreator, destFile);
                    if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                        cancelAllHolder[0] = true;
                        break;
                    }
                    if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                    destFile = resolution.targetFile();

                    try {
                        FileOps.copyReplacing(src, destFile);
                        singleClonedFiles.add(destFile);
                        counts[0]++;
                    } catch (Exception ex) {
                        MenuLog.error("Failed to clone file " + src.getAbsolutePath() + " to " + destFile.getAbsolutePath(), ex);
                        onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }

                if (!singleClonedFiles.isEmpty()) {
                    onEdtVoid(() -> {
                        if (store.isVirtualDir(currentDir)) store.assignToVirtualDir(singleClonedFiles, currentDir);
                        profile.entriesAdded(mcreator.getWorkspace().getWorkspaceFolder(), singleClonedFiles);
                    });
                }

                for (File src : sourceFolders) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String parentPath = folderParentPaths.get(src);
                    String currentName = folderCurrentNames.get(src);
                    String cloneName = promptUniqueVirtualFolderName(mcreator, store, parentPath, currentName, progress, nameResolver);
                    if (cloneName == null) {
                        cancelAllHolder[0] = true;
                        break;
                    }

                    LinkedHashMap<String, List<File>> subtree = folderSubtrees.get(src);
                    Map<String, List<File>> clonedByRelative = new LinkedHashMap<>();
                    List<File> allClonedFiles = new ArrayList<>();
                    boolean subtreeFailed = false;

                    outer:
                    for (Map.Entry<String, List<File>> branch : subtree.entrySet()) {
                        List<File> clonedList = new ArrayList<>();
                        for (File f : branch.getValue()) {
                            FileNameParts parts = FileNameParts.of(f);
                            File destFile = generateUniqueClonePath(rootDir, parts.baseName(), canonicalExtension(parts.extension()));
                            try {
                                FileOps.copyReplacing(f, destFile);
                                clonedList.add(destFile);
                                allClonedFiles.add(destFile);
                            } catch (Exception ex) {
                                subtreeFailed = true;
                                MenuLog.error("Failed to clone file " + f.getAbsolutePath() + " to " + destFile.getAbsolutePath(), ex);
                                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                        progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                                clonedByRelative.put(branch.getKey(), clonedList);
                                break outer;
                            }
                        }
                        clonedByRelative.put(branch.getKey(), clonedList);
                    }

                    boolean anyCloned = !allClonedFiles.isEmpty();
                    if (anyCloned) {
                        String newRootFullPath = store.childPath(parentPath, cloneName);
                        onEdtVoid(() -> {
                            recreateSubtree(store, newRootFullPath, clonedByRelative);
                            profile.entriesAdded(mcreator.getWorkspace().getWorkspaceFolder(), allClonedFiles);
                        });
                    }
                    if (anyCloned && !subtreeFailed) counts[1]++;
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

    private static void recreateSubtree(VirtualFolderStore store, String newRootFullPath,
                                        Map<String, List<File>> clonedByRelative) {
        List<String> relativePaths = new ArrayList<>(clonedByRelative.keySet());
        relativePaths.sort(Comparator.comparingInt(VirtualCloneAction::depthOf));
        for (String relative : relativePaths) {
            String fullPath = relative.isEmpty() ? newRootFullPath : newRootFullPath + VirtualFolderStore.SEP + relative;
            store.createFolder(fullPath);
            List<File> files = clonedByRelative.get(relative);
            if (files != null && !files.isEmpty()) {
                store.assignToVirtualDir(files, store.virtualDirRef(fullPath));
            }
        }
    }

    private static int depthOf(String relativePath) {
        return relativePath.isEmpty() ? 0 : (int) relativePath.chars().filter(c -> c == VirtualFolderStore.SEP.charAt(0)).count() + 1;
    }

    private static String promptUniqueVirtualFolderName(MCreator mcreator, VirtualFolderStore store, String parentPath,
                                                        String currentFolderName, String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = promptCloneFolderName(mcreator, currentFolderName, progress, nameResolver);
            if (name == null) return null;
            name = VirtualFolderStore.sanitizeSegmentName(name);
            if (name.isEmpty()) continue;
            if (store.folderExists(store.childPath(parentPath, name))) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                continue;
            }
            return name;
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
}

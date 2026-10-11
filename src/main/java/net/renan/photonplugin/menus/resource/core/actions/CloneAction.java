package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.conflict.BulkConflictResolver;
import net.renan.photonplugin.menus.resource.core.conflict.ConflictResolution;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public final class CloneAction {

    private CloneAction() {
    }

    public static JButton buildCloneButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        return buildCloneButtonForBrowser(mcreator, browser, targetDirGetter, "16px_photon.clone");
    }

    public static JButton buildCloneButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter, String iconKey) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.clone.element"), iconKey);
        btn.addActionListener(e -> {
            List<File> sourceFiles = browser.getSelectedFiles();
            List<File> sourceFolders = browser.getSelectedFolders().stream()
                    .filter(File::isDirectory)
                    .collect(java.util.stream.Collectors.toList());
            if (sourceFiles.isEmpty() && sourceFolders.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File rootDir = targetDirGetter.get();
            if (!rootDir.exists()) rootDir.mkdirs();
            File currentDir = EdtSupport.onEdt(browser::getCurrentDir);
            File cloneDestDir = (currentDir != null && currentDir.exists()) ? currentDir : rootDir;
            int total = sourceFiles.size() + sourceFolders.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);

            int[] counts = new int[2];
            boolean[] cancelAllHolder = new boolean[1];
            int[] indexHolder = new int[1];

            browser.beginInternalMutation();
            EdtSupport.runFileTaskInBackground(mcreator, () -> {
                for (File src : sourceFiles) {
                    if (cancelAllHolder[0]) break;
                    FileNameParts parts = FileNameParts.of(src);
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String cloneName = promptCloneName(mcreator, parts, progress, nameResolver);
                    if (cloneName == null) {
                        cancelAllHolder[0] = true;
                        break;
                    }

                    File destFile = new File(cloneDestDir, cloneName + FileNameRules.canonicalExtension(parts.extension()));
                    ConflictResolution resolution = resolver.resolve(mcreator, destFile);
                    if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                        cancelAllHolder[0] = true;
                        break;
                    }
                    if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                    destFile = resolution.targetFile();

                    try {
                        FileOps.copyReplacing(src, destFile);
                        counts[0]++;
                    } catch (Exception ex) {
                        MenuLog.error("Failed to clone file '" + src.getAbsolutePath() + "' to '" + destFile.getAbsolutePath() + "'", ex);
                        EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }

                for (File src : sourceFolders) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String cloneName = promptCloneFolderName(mcreator, browser.getFolderName(src), progress, nameResolver);
                    if (cloneName == null) {
                        cancelAllHolder[0] = true;
                        break;
                    }

                    File destFolder = new File(cloneDestDir, cloneName);
                    if (destFolder.exists()) {
                        ConflictResolution resolution = resolver.resolve(mcreator, destFolder);
                        if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        if (resolution.action() == ConflictResolution.ACTION_SKIP) continue;
                        destFolder = resolution.targetFile();
                    }

                    boolean hasSubDirs = false;
                    File[] children = src.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            if (child.isDirectory()) {
                                hasSubDirs = true;
                                break;
                            }
                        }
                    }
                    boolean recursive = true;
                    if (hasSubDirs) {
                        int choice = EdtSupport.onEdtChoice(() -> JOptionPane.showConfirmDialog(
                                mcreator,
                                L10N.t("plugin.photon.resourcemenu.clone.folder.has.subfolders", browser.getFolderName(src)),
                                L10N.t("plugin.photon.resourcemenu.operation.question"),
                                JOptionPane.YES_NO_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE));
                        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        recursive = (choice == JOptionPane.YES_OPTION);
                    }
                    File finalDestFolder = destFolder;
                    boolean finalRecursive = recursive;
                    try {
                        FileOps.replaceDirectory(finalDestFolder, () -> {
                            if (finalRecursive) FileOps.copyFolderContents(src, finalDestFolder);
                            else FileOps.copyFolderContentsShallow(src, finalDestFolder);
                        });
                        counts[1]++;
                    } catch (Exception ex) {
                        MenuLog.error("Failed to clone folder '" + src.getAbsolutePath() + "' to '" + finalDestFolder.getAbsolutePath() + "'", ex);
                        EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.cloned") + BulkConflictResolver.autoRenameNotice(resolver));
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

    public static String promptCloneName(MCreator mcreator, FileNameParts parts, String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(mcreator,
                    progress + "  " + L10N.t("plugin.photon.resourcemenu.clone.name.message", FileNameRules.canonicalExtension(parts.extension())) + "\n"
                            + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + parts.baseName(),
                    parts.baseName() + "_copy"));
            if (name == null) return null;
            name = name.trim();
            if (name.isEmpty()) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            String typedExtension = parts.extension();
            if (!typedExtension.isEmpty() && name.length() > typedExtension.length() && FileNameRules.hasExtension(name, typedExtension)) {
                name = name.substring(0, name.length() - typedExtension.length());
            }
            if (FileNameRules.requiresStrictNaming(parts.extension())) {
                name = nameResolver.resolve(mcreator, name, progress);
                if (name == null) continue;
            } else if (!FileNameRules.isValidFileName(name)) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            if (name.equalsIgnoreCase(parts.baseName())) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.clone.same.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            return name;
        }
    }

    public static String promptCloneFolderName(MCreator mcreator, String currentFolderName, String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(mcreator,
                    progress + "  " + L10N.t("plugin.photon.resourcemenu.clone.folder.name.message") + "\n"
                            + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + currentFolderName,
                    currentFolderName + "_copy"));
            if (name == null) return null;
            name = name.trim();
            if (name.isEmpty()) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }

            name = nameResolver.resolve(mcreator, name, progress, false);
            if (name == null) continue;
            if (name.equalsIgnoreCase(currentFolderName)) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.clone.same.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            return name;
        }
    }
}

package net.renan.photonplugin.menus.resource.core.naming;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class StrictNameEnforcer {

    private StrictNameEnforcer() {
    }

    public static final class BlockingInvalidNameResolver {
        private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

        private final boolean showApplyAll;
        private Boolean globalAutoFix = null;

        public BlockingInvalidNameResolver(int totalItems) {
            this.showApplyAll = totalItems >= MIN_ITEMS_FOR_APPLY_ALL;
        }

        String resolve(Component parent, String originalName) {
            String name = originalName;
            if (FileNameRules.isValidStrictFileName(name)) return name;

            while (!FileNameRules.isValidStrictFileName(name)) {
                if (globalAutoFix != null) {
                    return FileNameRules.sanitizeStrictFileName(name);
                }

                String suggestion = FileNameRules.sanitizeStrictFileName(name);

                JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
                String message = FileNameRules.buildInvalidNameMessage(
                        "plugin.photon.resourcemenu.name.invalid.chars.blocking.message",
                        "plugin.photon.resourcemenu.name.invalid.case.blocking.message",
                        name, suggestion);
                Object[] content = showApplyAll ? new Object[]{message, applyAll} : new Object[]{message};
                Object[] options = {
                        L10N.t("plugin.photon.resourcemenu.name.auto.fix"),
                        L10N.t("plugin.photon.resourcemenu.name.type.manually")
                };
                int choice = EdtSupport.onEdt(() -> showUndismissableOptionDialog(parent, content, options));

                if (choice == 0) {
                    if (applyAll != null && applyAll.isSelected()) globalAutoFix = true;
                    return suggestion;
                } else {
                    String typed = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(parent,
                            L10N.t("plugin.photon.resourcemenu.name.manual.entry.message"), suggestion));
                    if (typed == null) continue;
                    typed = typed.trim();
                    if (typed.isEmpty()) {
                        EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                                L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                        continue;
                    }
                    name = typed;
                }
            }
            return name;
        }
    }

    private static int showUndismissableOptionDialog(Component parent, Object message, Object[] options) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        JDialog dialog = pane.createDialog(parent, L10N.t("plugin.photon.resourcemenu.operation.warning"));
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        for (WindowListener wl : dialog.getWindowListeners()) {
            dialog.removeWindowListener(wl);
        }
        dialog.getRootPane().registerKeyboardAction(ev -> {
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
        dialog.dispose();

        Object selected = pane.getValue();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selected)) return i;
        }
        return -1;
    }

    private static String promptUniqueNameOnConflictBlocking(Component parent, File dir, String conflictingName,
                                                              String extension, File exceptFile) {
        String prefill = conflictingName;
        EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                L10N.t("plugin.photon.resourcemenu.name.conflict.blocking.message", conflictingName),
                L10N.t("plugin.photon.resourcemenu.operation.warning"), JOptionPane.WARNING_MESSAGE));
        while (true) {
            String currentPrefill = prefill;
            String typed = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(parent,
                    L10N.t("plugin.photon.resourcemenu.name.manual.entry.message"), currentPrefill));
            if (typed == null) continue;
            typed = typed.trim();
            if (typed.isEmpty() || !FileNameRules.isValidStrictFileName(typed)) {
                String finalTyped = typed;
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t(finalTyped.isEmpty() ? "plugin.photon.resourcemenu.rename.empty.name.error" : "plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = typed;
                continue;
            }
            File candidate = new File(dir, typed + extension);
            if (candidate.exists() && !candidate.equals(exceptFile)) {
                String finalCandidateName = candidate.getName();
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.overwrite.rename.still.conflicts", finalCandidateName),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = typed;
                continue;
            }
            return typed;
        }
    }

    public static File enforceValidNameBlocking(Component parent, File file, String extension) {
        return enforceValidNameBlocking(parent, file, extension, new BlockingInvalidNameResolver(1));
    }

    public static File enforceValidNameBlocking(Component parent, File file, String extension, BlockingInvalidNameResolver resolver) {
        if (file == null || !file.exists() || file.isDirectory()) return file;
        if (!FileNameRules.requiresStrictNaming(extension)) return file;

        FileNameParts initialParts = FileNameParts.of(file);
        boolean baseValid = FileNameRules.isValidStrictFileName(initialParts.baseName());
        boolean extensionValid = initialParts.extension().equals(extension);
        if (baseValid && extensionValid) return file;

        File dir = file.getParentFile();
        File current = file;
        File lastFailedDest = null;

        while (true) {
            FileNameParts currentParts = FileNameParts.of(current);
            String validName = resolver.resolve(parent, currentParts.baseName());

            File dest = new File(dir, validName + extension);
            if (FileOps.isSamePathExact(dest, current)) return current;

            if (dest.exists() && !FileOps.isSamePathIgnoreCase(dest, current)) {
                validName = promptUniqueNameOnConflictBlocking(parent, dir, validName, extension, current);
                dest = new File(dir, validName + extension);
                if (FileOps.isSamePathExact(dest, current)) return current;
            }

            if (FileOps.renameFile(current, dest)) {
                return dest;
            }

            MenuLog.warn("Failed to auto-correct invalid file name: " + current.getAbsolutePath() + " -> " + dest.getAbsolutePath());

            File finalDest = dest;
            if (dest.equals(lastFailedDest)) {
                Object[] retryOptions = {
                        L10N.t("plugin.photon.resourcemenu.rename.retry"),
                        L10N.t("plugin.photon.resourcemenu.rename.leave.as.is")
                };
                int choice = EdtSupport.onEdt(() -> JOptionPane.showOptionDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + current.getName(),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, retryOptions, retryOptions[0]));
                if (choice != 0) {
                    return current;
                }
            } else {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + current.getName(),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            }
            lastFailedDest = finalDest;
        }
    }

    private static List<File> listFilesRecursively(File dir, String extension) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return result;
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (child.isDirectory()) {
                result.addAll(listFilesRecursively(child, extension));
            } else if (FileNameRules.hasExtension(child, extension)) {
                result.add(child);
            }
        }
        return result;
    }

    public static void validateExistingFileNamesBlocking(Component parent, File rootDir, String extension) {
        if (!FileNameRules.requiresStrictNaming(extension)) return;
        List<File> candidates = listFilesRecursively(rootDir, extension);
        int invalidNameCount = 0;
        for (File file : candidates) {
            FileNameParts parts = FileNameParts.of(file);
            boolean baseValid = FileNameRules.isValidStrictFileName(parts.baseName());
            boolean extensionValid = parts.extension().equals(extension);
            if (!baseValid || !extensionValid) invalidNameCount++;
        }
        BlockingInvalidNameResolver resolver = new BlockingInvalidNameResolver(invalidNameCount);
        for (File file : candidates) {
            enforceValidNameBlocking(parent, file, extension, resolver);
        }
    }
}

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
import net.renan.photonplugin.menus.resource.core.support.MenuMessages;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class RenameAction {

    private RenameAction() {
    }

    public static RenameOutcome promptAndRenameFile(Component parent, File item, String progress,
                                                    InvalidNameResolver nameResolver, BulkConflictResolver resolver) {
        FileNameParts parts = FileNameParts.of(item);
        String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message.file", FileNameRules.canonicalExtension(parts.extension())) + "\n"
                + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + item.getName();
        String newName = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(parent, hint, parts.baseName()));
        if (newName == null) return RenameOutcome.cancelled();
        newName = newName.trim();
        if (newName.isEmpty()) {
            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        String extension = parts.extension();
        if (!extension.isEmpty() && newName.length() > extension.length() && FileNameRules.hasExtension(newName, extension)) {
            newName = newName.substring(0, newName.length() - extension.length());
        }
        if (FileNameRules.requiresStrictNaming(extension)) {
            newName = nameResolver.resolve(parent, newName, progress);
            if (newName == null) return RenameOutcome.skipped();
            extension = FileNameRules.canonicalExtension(extension);
        } else if (!FileNameRules.isValidFileName(newName)) {
            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        File dest = new File(item.getParentFile(), newName + extension);
        if (FileOps.isSamePathExact(dest, item)) return RenameOutcome.skipped();

        if (FileOps.isSamePathIgnoreCase(dest, item)) {
            if (FileOps.renameFile(item, dest)) {
                return RenameOutcome.renamed(dest);
            }
            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + item.getName(),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        ConflictResolution resolution = resolver.resolve(parent, dest);
        if (resolution.action() == ConflictResolution.ACTION_CANCEL) return RenameOutcome.cancelled();
        if (resolution.action() == ConflictResolution.ACTION_SKIP) return RenameOutcome.skipped();
        dest = resolution.targetFile();
        if (FileOps.replaceMove(item, dest)) {
            return RenameOutcome.renamed(dest);
        }
        EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + item.getName(),
                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
        return RenameOutcome.skipped();
    }

    private static void renameOneFolder(MCreator mcreator, FXBrowserPanel browser, File item, String progress,
                                        InvalidNameResolver nameResolver, BulkConflictResolver resolver,
                                        int[] counts, boolean[] cancelAllHolder) {
        String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message") + "\n"
                + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + browser.getFolderName(item);
        String newName = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(mcreator, hint, browser.getFolderName(item)));
        if (newName == null) {
            cancelAllHolder[0] = true;
            return;
        }
        newName = newName.trim();
        if (newName.isEmpty()) {
            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return;
        }

        newName = nameResolver.resolve(mcreator, newName, progress, false);
        if (newName == null) return;

        File dest = new File(item.getParentFile(), newName);
        if (FileOps.isSamePathExact(dest, item)) return;

        if (FileOps.isSamePathIgnoreCase(dest, item)) {
            if (FileOps.renameFile(item, dest)) {
                counts[1]++;
            } else {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + browser.getFolderName(item),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            }
            return;
        }

        ConflictResolution resolution = resolver.resolve(mcreator, dest);
        if (resolution.action() == ConflictResolution.ACTION_CANCEL) {
            cancelAllHolder[0] = true;
            return;
        }
        if (resolution.action() == ConflictResolution.ACTION_SKIP) return;
        dest = resolution.targetFile();
        if (FileOps.replaceMove(item, dest)) {
            counts[1]++;
        } else {
            EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + browser.getFolderName(item),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
        }
    }

    public static JButton buildRenameButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.rename.file"), "16px_photon.rename");
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
            EdtSupport.runFileTaskInBackground(mcreator, () -> {
                for (int i = 0; i < total; i++) {
                    if (cancelAllHolder[0]) break;
                    File item = allItems.get(i);
                    String progress = "(" + (i + 1) + " / " + total + ")";

                    if (item.isDirectory()) {
                        renameOneFolder(mcreator, browser, item, progress, nameResolver, resolver, counts, cancelAllHolder);
                    } else {
                        RenameOutcome outcome = promptAndRenameFile(mcreator, item, progress, nameResolver, resolver);
                        if (outcome.isCancelled()) {
                            cancelAllHolder[0] = true;
                            break;
                        }
                        if (outcome.isRenamed()) counts[0]++;
                    }
                }
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, MenuMessages.buildItemLabel(counts[0], counts[1]) + " "
                                + L10N.t("plugin.photon.resourcemenu.action.renamed") + BulkConflictResolver.autoRenameNotice(resolver));
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

    public record RenameOutcome(int status, File dest) {
        private static final int RENAMED = 0, SKIPPED = 1, CANCELLED = 2;

        static RenameOutcome renamed(File dest) {
            return new RenameOutcome(RENAMED, dest);
        }

        static RenameOutcome skipped() {
            return new RenameOutcome(SKIPPED, null);
        }

        static RenameOutcome cancelled() {
            return new RenameOutcome(CANCELLED, null);
        }

        public boolean isRenamed() {
            return status == RENAMED;
        }

        public boolean isCancelled() {
            return status == CANCELLED;
        }
    }
}

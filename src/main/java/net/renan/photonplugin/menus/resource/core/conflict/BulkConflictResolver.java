package net.renan.photonplugin.menus.resource.core.conflict;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.naming.InvalidNameResolver;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BulkConflictResolver {

    private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

    private final boolean allowSkip;
    private final boolean showApplyAll;
    private int globalDecision = -1;
    private boolean cancelled = false;
    private final List<String> autoRenamedNames = new ArrayList<>();

    public BulkConflictResolver(int totalItems) {
        this(totalItems, true);
    }

    public BulkConflictResolver(int totalItems, boolean allowSkip) {
        this.allowSkip = allowSkip;
        this.showApplyAll = totalItems >= MIN_ITEMS_FOR_APPLY_ALL;
    }

    public static BulkConflictResolver forDestinations(List<File> prospectiveDestinations) {
        return new BulkConflictResolver(countConflicts(prospectiveDestinations), true);
    }

    public static BulkConflictResolver forDestinations(List<File> prospectiveDestinations, boolean allowSkip) {
        return new BulkConflictResolver(countConflicts(prospectiveDestinations), allowSkip);
    }

    public static int countConflicts(List<File> prospectiveDestinations) {
        int count = 0;
        for (File f : prospectiveDestinations) {
            if (f != null && f.exists()) count++;
        }
        return count;
    }

    public static int countConflicts(Collection<File> sources, File destDir) {
        int count = 0;
        for (File src : sources) {
            if (src != null && new File(destDir, src.getName()).exists()) count++;
        }
        return count;
    }

    public static ConflictResolution checkOverwrite(Component parent, File destFile) {
        if (!destFile.exists()) return ConflictResolution.proceed(destFile);
        while (true) {
            String message = L10N.t("plugin.photon.resourcemenu.overwrite.message") + "\n[" + destFile.getName() + "]";
            Object[] options = {
                    L10N.t("plugin.photon.resourcemenu.overwrite.replace"),
                    L10N.t("plugin.photon.resourcemenu.overwrite.rename"),
                    L10N.t("plugin.photon.resourcemenu.overwrite.skip"),
                    L10N.t("plugin.photon.resourcemenu.operation.cancel.button")
            };
            int choice = EdtSupport.onEdt(() -> JOptionPane.showOptionDialog(parent, message,
                    L10N.t("common.confirmation"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]));

            if (choice == 1) {
                File renamed = promptRenameOnConflict(parent, destFile);
                if (renamed == null) continue;
                return ConflictResolution.proceed(renamed);
            }
            return switch (choice) {
                case 0 -> ConflictResolution.proceed(destFile);
                case 2 -> ConflictResolution.skip();
                default -> ConflictResolution.cancel();
            };
        }
    }

    public ConflictResolution resolve(Component parent, File destFile) {
        if (!destFile.exists()) return ConflictResolution.proceed(destFile);
        if (globalDecision != -1) {
            return switch (globalDecision) {
                case ConflictResolution.ACTION_PROCEED -> ConflictResolution.proceed(destFile);
                case ConflictResolution.ACTION_RENAME_ALL -> ConflictResolution.proceed(recordAutoRename(destFile));
                default -> ConflictResolution.skip();
            };
        }

        while (true) {
            String message = L10N.t("plugin.photon.resourcemenu.overwrite.message") + "\n[" + destFile.getName() + "]";
            JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
            Object[] content = showApplyAll ? new Object[]{message, applyAll} : new Object[]{message};
            Object[] options = allowSkip
                    ? new Object[]{
                    L10N.t("plugin.photon.resourcemenu.overwrite.replace"),
                    L10N.t("plugin.photon.resourcemenu.overwrite.rename"),
                    L10N.t("plugin.photon.resourcemenu.overwrite.skip"),
                    L10N.t("plugin.photon.resourcemenu.operation.cancel.button")
            }
                    : new Object[]{
                    L10N.t("plugin.photon.resourcemenu.overwrite.replace"),
                    L10N.t("plugin.photon.resourcemenu.overwrite.rename"),
                    L10N.t("plugin.photon.resourcemenu.operation.cancel.button")
            };
            int skipIndex = allowSkip ? 2 : -1;

            int choice = EdtSupport.onEdt(() -> JOptionPane.showOptionDialog(
                    parent, content,
                    L10N.t("common.confirmation"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]));

            boolean applyToAll = applyAll != null && applyAll.isSelected();
            if (choice == JOptionPane.CLOSED_OPTION) {
                cancelled = true;
                return ConflictResolution.cancel();
            }
            if (choice == 1) {
                if (applyToAll) {
                    globalDecision = ConflictResolution.ACTION_RENAME_ALL;
                    return ConflictResolution.proceed(recordAutoRename(destFile));
                }
                File renamed = promptRenameOnConflict(parent, destFile);
                if (renamed == null) continue;
                return ConflictResolution.proceed(renamed);
            }
            if (allowSkip && choice == skipIndex) {
                if (applyToAll) globalDecision = ConflictResolution.ACTION_SKIP;
                return ConflictResolution.skip();
            }
            if (choice != 0) {
                cancelled = true;
                return ConflictResolution.cancel();
            }
            if (applyToAll) globalDecision = ConflictResolution.ACTION_PROCEED;
            return ConflictResolution.proceed(destFile);
        }
    }

    private File recordAutoRename(File destFile) {
        File candidate = generateAutoRenameCandidate(destFile);
        autoRenamedNames.add(destFile.getName() + " \u2192 " + candidate.getName());
        return candidate;
    }

    public boolean hasAutoRenamed() {
        return !autoRenamedNames.isEmpty();
    }

    public List<String> getAutoRenamedNames() {
        return new ArrayList<>(autoRenamedNames);
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    private static File generateAutoRenameCandidate(File destFile) {
        File dir = destFile.getParentFile();
        FileNameParts parts = destFile.isDirectory() ? new FileNameParts(destFile.getName(), "") : FileNameParts.of(destFile);
        String extension = FileNameRules.canonicalExtension(parts.extension());
        int counter = 1;
        File candidate;
        do {
            candidate = new File(dir, parts.baseName() + "_" + counter + extension);
            counter++;
        } while (candidate.exists());
        return candidate;
    }

    public static String autoRenameNotice(BulkConflictResolver resolver) {
        if (resolver == null || !resolver.hasAutoRenamed()) return "";
        List<String> names = resolver.getAutoRenamedNames();
        StringBuilder sb = new StringBuilder("\n\n")
                .append(L10N.t("plugin.photon.resourcemenu.rename.auto.suffixed.notice", names.size()));
        int shown = Math.min(names.size(), 10);
        for (int i = 0; i < shown; i++) {
            sb.append("\n    ").append(names.get(i));
        }
        if (names.size() > shown) {
            sb.append("\n  ... and more");
        }
        return sb.toString();
    }

    private static File promptRenameOnConflict(Component parent, File destFile) {
        File dir = destFile.getParentFile();
        FileNameParts parts = destFile.isDirectory() ? new FileNameParts(destFile.getName(), "") : FileNameParts.of(destFile);
        String prefill = parts.baseName();
        boolean hasExt = !parts.extension().isEmpty();
        String extension = FileNameRules.canonicalExtension(parts.extension());

        boolean autoFixEligible = destFile.isDirectory() || FileNameRules.requiresStrictNaming(parts.extension());
        InvalidNameResolver nameResolver = autoFixEligible ? new InvalidNameResolver(1) : null;
        while (true) {
            String messageKey = hasExt ? "plugin.photon.resourcemenu.overwrite.rename.message" : "plugin.photon.resourcemenu.overwrite.rename.message.folder";
            String hint = hasExt ? L10N.t(messageKey, extension) : L10N.t(messageKey);
            String currentPrefill = prefill;
            String input = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(parent,
                    hint + "\n" + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + destFile.getName(),
                    currentPrefill));
            if (input == null) return null;
            String newName = input.trim();
            if (newName.isEmpty()) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            if (autoFixEligible) {
                newName = nameResolver.resolve(parent, newName, FileNameRules.requiresStrictNaming(parts.extension()));
                if (newName == null) continue;
            } else if (!FileNameRules.isValidFileName(newName)) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            File candidate = new File(dir, newName + extension);
            if (candidate.exists()) {
                EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.overwrite.rename.still.conflicts", candidate.getName()),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            return candidate;
        }
    }
}

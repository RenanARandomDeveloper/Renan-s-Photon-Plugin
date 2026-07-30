package net.renan.photonplugin.menus.resource.common;

import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;
import net.renan.photonplugin.Log;
import net.renan.photonplugin.WatchService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ResourceMenuCommon {

    public static final int ACTION_PROCEED = 0;
    public static final int ACTION_SKIP = 1;
    public static final int ACTION_CANCEL = 2;
    private static final int ACTION_RENAME_ALL = 3;
    private static Color themeAccentColor() {
        try {
            Color c = Theme.current().getInterfaceAccentColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(90, 165, 70);
    }

    private static Color themeAccentColor(int alpha) {
        Color c = themeAccentColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
    private static Color themeBackgroundColor() {
        try {
            Color c = Theme.current().getBackgroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(45, 45, 45);
    }

    private static Color themeBackgroundColor(int alpha) {
        Color c = themeBackgroundColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static Color themeAltBackgroundColor() {
        try {
            Color c = Theme.current().getSecondAltBackgroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(55, 55, 55);
    }

    private static Color themeForegroundColor() {
        try {
            Color c = Theme.current().getForegroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(210, 210, 210);
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }
    private static Color shade(Color c, int amount) {
        return new Color(clampChannel(c.getRed() + amount), clampChannel(c.getGreen() + amount),
                clampChannel(c.getBlue() + amount), c.getAlpha());
    }

    private static final Set<String> STRICT_NAMING_EXTENSIONS = Set.of(".fx", ".fxpack", ".png", ".nbt", ".obj");
    private static final Pattern STRICT_FILE_NAME_PATTERN = Pattern.compile("^[a-z0-9_\\-.]+$");
    private static final Set<Character> OS_FORBIDDEN_CHARS = Set.of('<', '>', ':', '"', '/', '\\', '|', '?', '*');
    private static final Set<String> OS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private ResourceMenuCommon() {}

    public record FileNameParts(String baseName, String extension) {
        public static FileNameParts of(File file) {
            return of(file.getName());
        }
        public static FileNameParts of(String fileName) {
            int dot = fileName.lastIndexOf('.');
            return dot >= 0
                    ? new FileNameParts(fileName.substring(0, dot), fileName.substring(dot))
                    : new FileNameParts(fileName, "");
        }
    }

    public static boolean isValidFileName(String name) {
        return isValidLooseFileName(name);
    }

    public static boolean requiresStrictNaming(String extension) {
        return extension != null && STRICT_NAMING_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    static boolean hasExtension(File file, String extension) {
        return file != null && hasExtension(file.getName(), extension);
    }

    static boolean hasExtension(String fileName, String extension) {
        if (fileName == null || extension == null || extension.isEmpty()) return false;
        return fileName.regionMatches(true, fileName.length() - extension.length(), extension, 0, extension.length());
    }

    static String canonicalExtension(String extension) {
        return requiresStrictNaming(extension) ? extension.toLowerCase(Locale.ROOT) : extension;
    }

    public static boolean isValidStrictFileName(String name) {
        return name != null && !name.isBlank() && STRICT_FILE_NAME_PATTERN.matcher(name).matches();
    }

    public static Set<Character> findInvalidStrictChars(String name) {
        Set<Character> invalid = new LinkedHashSet<>();
        if (name == null) return invalid;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isStrictAllowedChar(Character.toLowerCase(c))) invalid.add(c);
        }
        return invalid;
    }

    public static String sanitizeStrictFileName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char lower = Character.toLowerCase(name.charAt(i));
            sb.append(isStrictAllowedChar(lower) ? lower : '_');
        }
        return sb.toString();
    }

    private static boolean isStrictAllowedChar(char lower) {
        return (lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9')
                || lower == '_' || lower == '-' || lower == '.';
    }

    public static boolean isValidLooseFileName(String name) {
        if (name == null || name.isBlank()) return false;
        if (name.equals(".") || name.equals("..")) return false;
        if (!findInvalidLooseChars(name).isEmpty()) return false;
        char last = name.charAt(name.length() - 1);
        if (last == '.' || last == ' ') return false;
        return !OS_RESERVED_NAMES.contains(looseReservedNameCandidate(name));
    }

    public static Set<Character> findInvalidLooseChars(String name) {
        Set<Character> invalid = new LinkedHashSet<>();
        if (name == null) return invalid;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isLooseAllowedChar(c)) invalid.add(c);
        }
        return invalid;
    }

    public static String sanitizeLooseFileName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(isLooseAllowedChar(c) ? c : '_');
        }
        int end = sb.length();
        while (end > 0 && (sb.charAt(end - 1) == '.' || sb.charAt(end - 1) == ' ')) end--;
        String result = sb.substring(0, end);
        if (result.isEmpty()) result = "_";
        if (OS_RESERVED_NAMES.contains(looseReservedNameCandidate(result))) {
            result = "_" + result;
        }
        return result;
    }

    private static boolean isLooseAllowedChar(char c) {
        return c >= 0x20 && !OS_FORBIDDEN_CHARS.contains(c);
    }

    private static String looseReservedNameCandidate(String name) {
        int dot = name.indexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        return base.toUpperCase(Locale.ROOT);
    }

    private static String formatInvalidCharsLabel(Set<Character> invalidChars) {
        StringBuilder sb = new StringBuilder();
        for (Character c : invalidChars) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('[').append(c == ' ' ? L10N.t("plugin.photon.resourcemenu.name.space.char") : c).append(']');
        }
        return sb.toString();
    }

    private static String buildInvalidNameMessage(String charsMessageKey, String caseOnlyMessageKey, String name, String suggestion) {
        Set<Character> invalidChars = findInvalidStrictChars(name);
        if (invalidChars.isEmpty()) {
            return L10N.t(caseOnlyMessageKey, name, suggestion);
        }
        return L10N.t(charsMessageKey, name, formatInvalidCharsLabel(invalidChars), suggestion);
    }

    private static String buildInvalidNameMessageLoose(String name, String suggestion) {
        Set<Character> invalidChars = findInvalidLooseChars(name);
        if (!invalidChars.isEmpty()) {
            return L10N.t("plugin.photon.resourcemenu.name.invalid.chars.loose.message",
                    name, formatInvalidCharsLabel(invalidChars), suggestion);
        }
        return L10N.t("plugin.photon.resourcemenu.name.invalid.reserved.message", name, suggestion);
    }

    public record ConflictResolution(int action, File targetFile) {
        static ConflictResolution proceed(File file) { return new ConflictResolution(ACTION_PROCEED, file); }
        static ConflictResolution skip()              { return new ConflictResolution(ACTION_SKIP, null); }
        static ConflictResolution cancel()             { return new ConflictResolution(ACTION_CANCEL, null); }
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
            int choice = onEdt(() -> JOptionPane.showOptionDialog(parent, message,
                    L10N.t("common.confirmation"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]));

            if (choice == 1) {
                File renamed = promptRenameOnConflict(parent, destFile);
                if (renamed == null) continue;
                return ConflictResolution.proceed(renamed);
            }
            return switch (choice) {
                case 0  -> ConflictResolution.proceed(destFile);
                case 2  -> ConflictResolution.skip();
                default -> ConflictResolution.cancel();
            };
        }
    }

    public static class BulkConflictResolver {
        private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

        private final boolean allowSkip;
        private final boolean showApplyAll;
        private int globalDecision = -1;
        private boolean cancelled  = false;
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

        public ConflictResolution resolve(Component parent, File destFile) {
            if (!destFile.exists()) return ConflictResolution.proceed(destFile);
            if (globalDecision != -1) {
                return switch (globalDecision) {
                    case ACTION_PROCEED    -> ConflictResolution.proceed(destFile);
                    case ACTION_RENAME_ALL -> ConflictResolution.proceed(recordAutoRename(destFile));
                    default -> ConflictResolution.skip();
                };
            }

            while (true) {
                String message = L10N.t("plugin.photon.resourcemenu.overwrite.message") + "\n[" + destFile.getName() + "]";
                JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
                Object[] content = showApplyAll ? new Object[]{ message, applyAll } : new Object[]{ message };
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
                int skipIndex   = allowSkip ? 2 : -1;

                int choice = onEdt(() -> JOptionPane.showOptionDialog(
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
                        globalDecision = ACTION_RENAME_ALL;
                        return ConflictResolution.proceed(recordAutoRename(destFile));
                    }
                    File renamed = promptRenameOnConflict(parent, destFile);
                    if (renamed == null) continue;
                    return ConflictResolution.proceed(renamed);
                }
                if (allowSkip && choice == skipIndex) {
                    if (applyToAll) globalDecision = ACTION_SKIP;
                    return ConflictResolution.skip();
                }
                if (choice != 0) {
                    cancelled = true;
                    return ConflictResolution.cancel();
                }
                if (applyToAll) globalDecision = ACTION_PROCEED;
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
    }

    private static File generateAutoRenameCandidate(File destFile) {
        File dir = destFile.getParentFile();
        FileNameParts parts = destFile.isDirectory() ? new FileNameParts(destFile.getName(), "") : FileNameParts.of(destFile);
        String extension = canonicalExtension(parts.extension());
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
        String extension = canonicalExtension(parts.extension());

        boolean autoFixEligible = destFile.isDirectory() || requiresStrictNaming(parts.extension());
        InvalidNameResolver nameResolver = autoFixEligible ? new InvalidNameResolver(1) : null;
        while (true) {
            String messageKey = hasExt ? "plugin.photon.resourcemenu.overwrite.rename.message" : "plugin.photon.resourcemenu.overwrite.rename.message.folder";
            String hint = hasExt ? L10N.t(messageKey, extension) : L10N.t(messageKey);
            String currentPrefill = prefill;
            String input = onEdt(() -> JOptionPane.showInputDialog(parent,
                    hint + "\n" + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + destFile.getName(),
                    currentPrefill));
            if (input == null) return null;
            String newName = input.trim();
            if (newName.isEmpty()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            if (autoFixEligible) {

                newName = nameResolver.resolve(parent, newName, requiresStrictNaming(parts.extension()));
                if (newName == null) continue;
            } else if (!isValidFileName(newName)) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            File candidate = new File(dir, newName + extension);
            if (candidate.exists()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t("plugin.photon.resourcemenu.overwrite.rename.still.conflicts", candidate.getName()),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = newName;
                continue;
            }
            return candidate;
        }
    }

    public static class InvalidNameResolver {
        private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

        private final boolean showApplyAll;
        private Boolean globalAutoFix = null;

        public InvalidNameResolver(int totalItems) {
            this.showApplyAll = totalItems >= MIN_ITEMS_FOR_APPLY_ALL;
        }

        public String resolve(Component parent, String originalName) {
            return resolve(parent, originalName, null, true);
        }

        public String resolve(Component parent, String originalName, String progress) {
            return resolve(parent, originalName, progress, true);
        }

        public String resolve(Component parent, String originalName, boolean strict) {
            return resolve(parent, originalName, null, strict);
        }

        public String resolve(Component parent, String originalName, String progress, boolean strict) {
            String name = originalName;
            if (isNameValid(name, strict)) return name;

            String messagePrefix = (progress != null && !progress.isEmpty()) ? progress + "  " : "";

            while (!isNameValid(name, strict)) {
                if (globalAutoFix != null) {
                    return globalAutoFix ? sanitizeName(name, strict) : null;
                }

                String suggestion = sanitizeName(name, strict);

                JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
                String message = messagePrefix + (strict
                        ? buildInvalidNameMessage(
                        "plugin.photon.resourcemenu.name.invalid.chars.message",
                        "plugin.photon.resourcemenu.name.invalid.case.message",
                        name, suggestion)
                        : buildInvalidNameMessageLoose(name, suggestion));
                Object[] content = showApplyAll ? new Object[]{ message, applyAll } : new Object[]{ message };
                Object[] options = {
                        L10N.t("plugin.photon.resourcemenu.name.auto.fix"),
                        L10N.t("plugin.photon.resourcemenu.name.type.manually"),
                        L10N.t("plugin.photon.resourcemenu.operation.cancel.button")
                };
                int choice = onEdt(() -> JOptionPane.showOptionDialog(
                        parent, content,
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[0]));

                if (choice == 0) {
                    if (applyAll != null && applyAll.isSelected()) globalAutoFix = true;
                    return suggestion;
                } else if (choice == 1) {
                    String manualEntryKey = strict
                            ? "plugin.photon.resourcemenu.name.manual.entry.message"
                            : "plugin.photon.resourcemenu.name.manual.entry.loose.message";
                    String typed = onEdt(() -> JOptionPane.showInputDialog(parent,
                            messagePrefix + L10N.t(manualEntryKey), suggestion));
                    if (typed == null) continue;
                    typed = typed.trim();
                    if (typed.isEmpty()) {
                        onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                                messagePrefix + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                        continue;
                    }
                    name = typed;
                } else {
                    if (applyAll != null && applyAll.isSelected()) globalAutoFix = false;
                    return null;
                }
            }
            return name;
        }

        private static boolean isNameValid(String name, boolean strict) {
            return strict ? isValidStrictFileName(name) : isValidLooseFileName(name);
        }

        private static String sanitizeName(String name, boolean strict) {
            return strict ? sanitizeStrictFileName(name) : sanitizeLooseFileName(name);
        }
    }

    public static String buildItemLabel(int fileCount, int folderCount) {
        if (fileCount == 0 && folderCount == 0) return "0";
        StringBuilder sb = new StringBuilder();
        if (fileCount > 0) {
            sb.append(fileCount).append(' ').append(fileCount == 1
                    ? L10N.t("plugin.photon.resourcemenu.item.type.file")
                    : L10N.t("plugin.photon.resourcemenu.item.type.files"));
        }
        if (fileCount > 0 && folderCount > 0) {
            sb.append(' ').append(L10N.t("plugin.photon.resourcemenu.and")).append(' ');
        }
        if (folderCount > 0) {
            sb.append(folderCount).append(' ').append(folderCount == 1
                    ? L10N.t("plugin.photon.resourcemenu.item.type.folder")
                    : L10N.t("plugin.photon.resourcemenu.item.type.folders"));
        }
        return sb.toString();
    }

    public static String buildDeleteConfirmationMessage(FXBrowserPanel browser, List<File> fileTargets, List<File> folderTargets) {
        StringBuilder msgBuilder = new StringBuilder();
        if (fileTargets.size() == 1) {
            msgBuilder.append(L10N.t("plugin.photon.resourcemenu.delete.confirm.single.file", fileTargets.get(0).getName()));
        } else {
            msgBuilder.append(L10N.t("plugin.photon.resourcemenu.delete.confirm.multiple.files", fileTargets.size()));
            int shown = Math.min(fileTargets.size(), 10);
            for (int j = 0; j < shown; j++) {
                msgBuilder.append("\n    ").append(fileTargets.get(j).getName());
            }
            if (fileTargets.size() > shown) {
                msgBuilder.append("\n  ... and more");
            }
        }
        if (!folderTargets.isEmpty()) {
            msgBuilder.append("\n\n").append(L10N.t("plugin.photon.resourcemenu.delete.folders.also.selected", folderTargets.size())).append(":");
            int shownFolders = Math.min(folderTargets.size(), 10);
            for (int j = 0; j < shownFolders; j++) {
                msgBuilder.append("\n    \uD83D\uDCC2 ").append(browser.getFolderName(folderTargets.get(j))).append("/");
            }
            if (folderTargets.size() > shownFolders) {
                msgBuilder.append("\n  ... and more");
            }
        }
        return msgBuilder.toString();
    }

    static String buildFolderContentsPreviewMessage(String progressPrefix, String folderLabel, List<File> contents) {
        StringBuilder folderMsg = new StringBuilder(
                progressPrefix + L10N.t(
                        "plugin.photon.resourcemenu.delete.folder.nonempty.question",
                        folderLabel, contents.size()));

        List<File> sorted = new ArrayList<>(contents);
        sorted.sort(Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        int shown = Math.min(sorted.size(), 10);
        for (int k = 0; k < shown; k++) {
            File child = sorted.get(k);
            if (child.isDirectory()) {
                folderMsg.append("\n  \uD83D\uDCC2 ").append(child.getName()).append("/");
            } else {
                folderMsg.append("\n    ").append(child.getName());
            }
        }
        if (sorted.size() > shown) {
            folderMsg.append("\n  ... and more");
        }
        return folderMsg.toString();
    }

    private static final class BlockingInvalidNameResolver {
        private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

        private final boolean showApplyAll;
        private Boolean globalAutoFix = null;

        BlockingInvalidNameResolver(int totalItems) {
            this.showApplyAll = totalItems >= MIN_ITEMS_FOR_APPLY_ALL;
        }

        String resolve(Component parent, String originalName) {
            String name = originalName;
            if (isValidStrictFileName(name)) return name;

            while (!isValidStrictFileName(name)) {
                if (globalAutoFix != null) {
                    return sanitizeStrictFileName(name);
                }

                String suggestion = sanitizeStrictFileName(name);

                JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
                String message = buildInvalidNameMessage(
                        "plugin.photon.resourcemenu.name.invalid.chars.blocking.message",
                        "plugin.photon.resourcemenu.name.invalid.case.blocking.message",
                        name, suggestion);
                Object[] content = showApplyAll ? new Object[]{ message, applyAll } : new Object[]{ message };
                Object[] options = {
                        L10N.t("plugin.photon.resourcemenu.name.auto.fix"),
                        L10N.t("plugin.photon.resourcemenu.name.type.manually")

                };
                int choice = onEdt(() -> showUndismissableOptionDialog(parent, content, options));

                if (choice == 0) {
                    if (applyAll != null && applyAll.isSelected()) globalAutoFix = true;
                    return suggestion;
                } else {
                    String typed = onEdt(() -> JOptionPane.showInputDialog(parent,
                            L10N.t("plugin.photon.resourcemenu.name.manual.entry.message"), suggestion));
                    if (typed == null) continue;
                    typed = typed.trim();
                    if (typed.isEmpty()) {
                        onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
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
        dialog.getRootPane().registerKeyboardAction(ev -> {  },
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
        onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                L10N.t("plugin.photon.resourcemenu.name.conflict.blocking.message", conflictingName),
                L10N.t("plugin.photon.resourcemenu.operation.warning"), JOptionPane.WARNING_MESSAGE));
        while (true) {
            String currentPrefill = prefill;
            String typed = onEdt(() -> JOptionPane.showInputDialog(parent,
                    L10N.t("plugin.photon.resourcemenu.name.manual.entry.message"), currentPrefill));
            if (typed == null) continue;
            typed = typed.trim();
            if (typed.isEmpty() || !isValidStrictFileName(typed)) {
                String finalTyped = typed;
                onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                        L10N.t(finalTyped.isEmpty() ? "plugin.photon.resourcemenu.rename.empty.name.error" : "plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                prefill = typed;
                continue;
            }
            File candidate = new File(dir, typed + extension);
            if (candidate.exists() && !candidate.equals(exceptFile)) {
                String finalCandidateName = candidate.getName();
                onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
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
        if (!requiresStrictNaming(extension)) return file;

        FileNameParts initialParts = FileNameParts.of(file);
        boolean baseValid = isValidStrictFileName(initialParts.baseName());
        boolean extensionValid = initialParts.extension().equals(extension);
        if (baseValid && extensionValid) return file;

        File dir = file.getParentFile();
        File current = file;

        while (true) {
            FileNameParts currentParts = FileNameParts.of(current);
            String validName = resolver.resolve(parent, currentParts.baseName());

            File dest = new File(dir, validName + extension);
            if (isSamePathExact(dest, current)) return current;

            if (dest.exists() && !isSamePathIgnoreCase(dest, current)) {

                validName = promptUniqueNameOnConflictBlocking(parent, dir, validName, extension, current);
                dest = new File(dir, validName + extension);
                if (isSamePathExact(dest, current)) return current;
            }

            if (renameFile(current, dest)) {
                return dest;
            }
            logWarn("Failed to auto-correct invalid file name: " + current.getAbsolutePath() + " -> " + dest.getAbsolutePath());
            onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + current.getName(),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));

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
            } else if (hasExtension(child, extension)) {
                result.add(child);
            }
        }
        return result;
    }

    public static void validateExistingFileNamesBlocking(Component parent, File rootDir, String extension) {
        if (!requiresStrictNaming(extension)) return;
        List<File> candidates = listFilesRecursively(rootDir, extension);
        int invalidNameCount = 0;
        for (File file : candidates) {
            FileNameParts parts = FileNameParts.of(file);
            boolean baseValid = isValidStrictFileName(parts.baseName());
            boolean extensionValid = parts.extension().equals(extension);
            if (!baseValid || !extensionValid) invalidNameCount++;
        }
        BlockingInvalidNameResolver resolver = new BlockingInvalidNameResolver(invalidNameCount);
        for (File file : candidates) {
            enforceValidNameBlocking(parent, file, extension, resolver);
        }
    }

    @FunctionalInterface
    public interface ResourceTabPanelFactory {
        JPanel create(MCreator mcreator);
    }

    private static final String LEGACY_RESOURCES_PANEL_CLASS_NAME = "WorkspacePanelResources";

    static void logInfo(String message) {
        Log.info("%s", message);
    }

    public static void logWarn(String message) {
        Log.warn("%s", message);
    }

    public static void logError(String message, Throwable throwable) {
        Log.error(message, throwable);
    }

    public static <T> T onEdt(Supplier<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.get();
        }
        final Object[] box = new Object[1];
        try {
            SwingUtilities.invokeAndWait(() -> box[0] = task.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException ite) {
            logError("Error while showing a dialog from a background task", ite.getCause());
        }
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }

    public static void onEdtVoid(Runnable task) {
        onEdt(() -> { task.run(); return null; });
    }

    public static void runFileTaskInBackground(Component parent, Runnable ioWork, Runnable onDone) {
        Component root = SwingUtilities.getRoot(parent);
        Component cursorTarget = (root != null) ? root : parent;
        Cursor previousCursor = cursorTarget.getCursor();
        cursorTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                ioWork.run();
                return null;
            }
            @Override
            protected void done() {
                cursorTarget.setCursor(previousCursor);
                try {
                    get();
                } catch (Exception ex) {
                    logError("Unexpected error during background file operation", ex);
                }
                onDone.run();
            }
        }.execute();
    }

    private static Object invokeNoArgMethod(Object target, String methodName) {
        if (target == null) return null;
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Method method = cls.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logError("Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                logError("Failed to read field '" + fieldName + "' on " + target.getClass().getName(), e);
                return null;
            }
        }
        return null;
    }

    private static Container findContainerBySimpleClassName(Component root, String simpleName, int depth) {
        if (root == null || depth > 24) return null;
        if (root instanceof Container container) {
            if (simpleName.equals(root.getClass().getSimpleName())) return container;
            for (Component child : container.getComponents()) {
                Container found = findContainerBySimpleClassName(child, simpleName, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Container resolveResourcesPan(MCreator mcreator) {
        if (mcreator == null) return null;

        Object workspacePanel = invokeNoArgMethod(mcreator, "getWorkspacePanel");
        Object holder = workspacePanel != null ? workspacePanel : mcreator;

        Object resourcesPanValue = readField(holder, "resourcesPan");
        if (resourcesPanValue instanceof Container container) return container;

        if (holder != mcreator) {
            resourcesPanValue = readField(mcreator, "resourcesPan");
            if (resourcesPanValue instanceof Container container) return container;
        }

        Container legacyContainer = findContainerBySimpleClassName(mcreator.getContentPane(),
                LEGACY_RESOURCES_PANEL_CLASS_NAME, 0);
        if (legacyContainer != null) {
            logInfo("Resolved resources panel through legacy Swing tree lookup for " + mcreator.getClass().getName());
            return legacyContainer;
        }

        logError("Unable to resolve the resources panel for MCreator instance of type "
                + mcreator.getClass().getName(), null);
        return null;
    }

    public static JTabbedPane findResourceTabbedPane(MCreator mcreator) {
        Container resourcesPan = resolveResourcesPan(mcreator);
        if (resourcesPan == null) return null;
        for (Component component : resourcesPan.getComponents()) {
            if (component instanceof JTabbedPane tabbedPane) {
                return tabbedPane;
            }
        }
        return null;
    }

    public static boolean isTabPresent(JTabbedPane tabbedPane, JPanel panel) {
        if (tabbedPane == null || panel == null) return false;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == panel) return true;
        }
        return false;
    }

    private static void removeTabsByTitle(JTabbedPane tabbedPane, String title, Component keep) {
        if (tabbedPane == null || title == null) return;
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            Component component = tabbedPane.getComponentAt(i);
            if (component == keep) continue;
            if (title.equals(tabbedPane.getTitleAt(i))) {
                tabbedPane.removeTabAt(i);
            }
        }
    }

    public static JPanel setupResourceTab(MCreator mcreator, JPanel panelInstance, String tabTitle, ResourceTabPanelFactory panelFactory) {
        Container resourcesPan = resolveResourcesPan(mcreator);
        if (resourcesPan == null) return panelInstance;

        JTabbedPane resourceTabs = findResourceTabbedPane(mcreator);

        if (panelInstance != null && isTabPresent(resourceTabs, panelInstance)) {
            removeTabsByTitle(resourceTabs, tabTitle, panelInstance);
            return panelInstance;
        }

        removeTabsByTitle(resourceTabs, tabTitle, null);

        JPanel panel = panelFactory.create(mcreator);
        boolean addedViaNewApi = false;

        try {
            var addMethod = resourcesPan.getClass().getMethod("addResourcesTab", String.class, JPanel.class);
            addMethod.invoke(resourcesPan, tabTitle, panel);
            addedViaNewApi = true;
        } catch (NoSuchMethodException e) {
            if (resourceTabs != null) resourceTabs.addTab(tabTitle, panel);
        } catch (Exception e) {
            logWarn("addResourcesTab invocation failed on " + resourcesPan.getClass().getName()
                    + ", falling back to JTabbedPane.addTab");
            if (resourceTabs != null) resourceTabs.addTab(tabTitle, panel);
        }

        if (addedViaNewApi) {
            resourceTabs = findResourceTabbedPane(mcreator);
            removeTabsByTitle(resourceTabs, tabTitle, panel);
        }
        if (resourceTabs != null) {
            resourceTabs.revalidate();
            resourceTabs.repaint();
        }
        return panel;
    }

    public static void removeResourceTab(MCreator mcreator, JPanel panelInstance) {
        JTabbedPane resourceTabs = findResourceTabbedPane(mcreator);
        if (resourceTabs == null) return;

        if (panelInstance != null) {
            for (int i = 0; i < resourceTabs.getTabCount(); i++) {
                if (resourceTabs.getComponentAt(i) == panelInstance) {
                    resourceTabs.remove(i);
                    resourceTabs.revalidate();
                    resourceTabs.repaint();
                    return;
                }
            }
        }
    }

    public static TransparentToolBar createToolBar() {
        TransparentToolBar toolBar = new TransparentToolBar();
        toolBar.setOpaque(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 0));
        return toolBar;
    }

    public static JButton createToolbarButton(String text, String iconKey) {
        JButton button = new JButton(text);
        if (iconKey != null) {
            try {
                button.setIcon(UIRES.get(iconKey));
            } catch (Exception ignored) {}
        }
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        ComponentUtils.deriveFont(button, 12.0F);
        return button;
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        attachDirectoryWatcher(panel, browser, targetDirGetter, null);
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                              WatchService.ChangeListener extraListener) {
        File dir = targetDirGetter.get();
        if (dir == null) return;
        if (!dir.exists() && !dir.mkdirs()) {
            logWarn("Could not create watched directory: " + dir.getAbsolutePath());
        }

        validateExistingFileNamesBlocking(panel, dir, browser.extension);

        browser.refresh();

        WatchService watchService = new WatchService();
        watchService.start(List.of(dir.toPath()), p -> false, (root, changed, kind) -> {
            if (kind == WatchService.ChangeKind.CREATE) {
                File changedFile = changed.toFile();
                if (changedFile.isFile() && requiresStrictNaming(browser.extension)
                        && hasExtension(changedFile, browser.extension)) {

                    FileNameParts initialParts = FileNameParts.of(changedFile);
                    boolean nameAlreadyValid = isValidStrictFileName(initialParts.baseName())
                            && initialParts.extension().equals(browser.extension);

                    if (!nameAlreadyValid) {
                        File result;
                        browser.beginInternalMutation();
                        try {
                            result = enforceValidNameBlocking(panel, changedFile, browser.extension);
                        } finally {
                            browser.endInternalMutation();
                        }
                        if (!isSamePathExact(result, changedFile)) {

                            SwingUtilities.invokeLater(browser::refresh);
                            return;
                        }
                    }

                }
            }

            if (extraListener != null) extraListener.onChange(root, changed, kind);
            if (browser.isWatcherRefreshSuppressed()) return;
            SwingUtilities.invokeLater(browser::refresh);
        });

        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !panel.isDisplayable()) {
                Thread stopper = new Thread(watchService::stop, "Photon-WatchService-Stop");
                stopper.setDaemon(true);
                stopper.start();
            }
        });
    }

    public static boolean isSamePathExact(File a, File b) {
        if (a == null || b == null) return a == b;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    public static boolean isSamePathIgnoreCase(File a, File b) {
        if (a == null || b == null) return a == b;
        File parentA = a.getParentFile();
        File parentB = b.getParentFile();
        String dirA = parentA != null ? parentA.getAbsolutePath() : "";
        String dirB = parentB != null ? parentB.getAbsolutePath() : "";
        return dirA.equalsIgnoreCase(dirB) && a.getName().equalsIgnoreCase(b.getName());
    }

    public static boolean renameFile(File src, File dest) {
        if (isSamePathExact(src, dest)) return true;
        if (isSamePathIgnoreCase(src, dest)) {
            File parent = dest.getParentFile();
            File tmp;
            int attempt = 0;
            do {
                tmp = new File(parent, ".photon_rename_tmp_" + System.nanoTime() + "_" + (attempt++));
            } while (tmp.exists());
            if (!src.renameTo(tmp)) return false;
            if (tmp.renameTo(dest)) return true;
            tmp.renameTo(src);
            return false;
        }
        return moveFileOrDirectory(src, dest);
    }

    static boolean moveFileOrDirectory(File source, File dest) {
        if (source.renameTo(dest)) return true;
        try {
            if (source.isDirectory()) {
                if (!dest.exists() && !dest.mkdirs()) return false;
                copyFolderContents(source, dest);
                return deleteRecursively(source);
            } else {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
                FileIO.copyFile(source, dest);
                return source.delete();
            }
        } catch (IOException ex) {
            logWarn("Move fallback (copy+delete) failed for '" + source.getAbsolutePath()
                    + "' -> '" + dest.getAbsolutePath() + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean deleteRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return dir.delete();
    }

    public static int copyDirectoryRecursively(File srcDir, File destParent) throws IOException {
        File destDir = new File(destParent, srcDir.getName());
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot create destination directory: " + destDir.getAbsolutePath());
        }
        int copied = 0;
        File[] entries = srcDir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    copyDirectoryRecursively(entry, destDir);
                } else {
                    FileIO.copyFile(entry, new File(destDir, entry.getName()));
                }
                copied++;
            }
        }
        return copied;
    }

    public static void copyFolderContents(File src, File dest) throws IOException {
        File[] entries = src.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                copyDirectoryRecursively(entry, dest);
            } else {
                FileIO.copyFile(entry, new File(dest, entry.getName()));
            }
        }
    }

    public static void copyFolderContentsShallow(File src, File dest) throws IOException {
        File[] files = src.listFiles(File::isFile);
        if (files == null) return;
        for (File f : files) {
            FileIO.copyFile(f, new File(dest, f.getName()));
        }
    }

    public static Icon scaleIconTo(Icon source, int targetPx) {
        if (source == null) return null;
        int sw = source.getIconWidth(), sh = source.getIconHeight();
        if (sw <= 0 || sh <= 0) return null;

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(sw, sh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        source.paintIcon(new JLabel(), g2, 0, 0);
        g2.dispose();

        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(targetPx, targetPx, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g3 = scaled.createGraphics();
        g3.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g3.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int newW, newH;
        if (sw >= sh) {
            newW = targetPx;
            newH = Math.max(1, targetPx * sh / sw);
        } else {
            newH = targetPx;
            newW = Math.max(1, targetPx * sw / sh);
        }
        int xOff = (targetPx - newW) / 2;
        int yOff = (targetPx - newH) / 2;

        g3.drawImage(img, xOff, yOff, newW, newH, null);
        g3.dispose();
        return new ImageIcon(scaled);
    }

    public static abstract class ItemCard extends JPanel {
        private static Color bgHoverColor()    { return new Color(100, 100, 100, 210); }
        private static Color bgSelectedColor() { return themeAccentColor(120); }
        private static Color bdSelectedColor() { return themeAccentColor(); }

        protected static final int ICON_PAD  = 12;
        protected static final int TEXT_GAP  = 10;

        protected final File file;
        protected final int iconSize;
        protected boolean hovered  = false;
        protected boolean selected = false;
        protected boolean dropHighlight = false;

        ItemCard(File file, int prefW, int prefH, int iconSize) {
            this.file = file;
            this.iconSize = iconSize;
            setPreferredSize(new Dimension(prefW, prefH));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public File getFile() { return file; }

        public void setSelected(boolean v) {
            if (selected != v) {
                selected = v;
                repaint();
            }
        }

        protected abstract void paintContent(Graphics2D g2, int w, int h);

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                int w = getWidth(), h = getHeight();
                if (dropHighlight) {
                    g2.setColor(new Color(60, 120, 200, 160));
                    g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                    g2.setColor(new Color(100, 170, 240));
                    g2.setStroke(new BasicStroke(2.0f));
                    g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);
                } else if (selected) {
                    g2.setColor(bgSelectedColor());
                    g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                    g2.setColor(bdSelectedColor());
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);
                } else if (hovered) {
                    g2.setColor(bgHoverColor());
                    g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                }
                paintContent(g2, w, h);
            } finally {
                g2.dispose();
            }
        }

        protected void drawIcon(Graphics2D g2, Icon icon, int h) {
            if (icon == null) return;
            icon.paintIcon(this, g2, ICON_PAD, (h - iconSize) / 2);
        }

        protected int textX() { return ICON_PAD + iconSize + TEXT_GAP; }

        protected static Font cardFont(int style, float size) {
            Font base = UIManager.getFont("Label.font");
            if (base == null) base = new Font("Dialog", Font.PLAIN, 12);
            return base.deriveFont(style, size);
        }

        protected static String truncToWidth(Graphics2D g2, String s, int maxPx) {
            if (s == null || s.isEmpty()) return "";
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(s) <= maxPx) return s;

            String ellipsis = "...";
            while (s.length() > 1 && fm.stringWidth(s + ellipsis) > maxPx) {
                s = s.substring(0, s.length() - 1);
            }
            return s.isEmpty() ? ellipsis : s + ellipsis;
        }

        protected static final int MAX_DISPLAY_NAME_CHARS = 15;
        protected static String truncToChars(String s, int maxChars) {
            if (s == null || s.isEmpty()) return "";
            if (s.length() <= maxChars) return s;

            return s.substring(0, maxChars) + "...";
        }
    }

    public static class FolderCard extends ItemCard {
        private static final int W = 90;
        private static final int H = 100;
        private static final int FOLDER_ICON_SIZE = 64;
        private final Icon   icon;
        private final String name;
        private boolean nameTruncated = false;

        public FolderCard(File dir, FXBrowserPanel browser) {
            super(dir, W, H, FOLDER_ICON_SIZE);
            Icon resolved = null;
            try   { resolved = scaleIconTo(UIRES.get("16px_photon.folder"), FOLDER_ICON_SIZE); } catch (Exception ignored) {}
            if (resolved == null)
                try   { resolved = scaleIconTo(UIManager.getIcon("FileView.directoryIcon"), FOLDER_ICON_SIZE); } catch (Exception ignored) {}
            if (resolved == null)
                try   { resolved = scaleIconTo(UIManager.getIcon("Tree.openIcon"), FOLDER_ICON_SIZE); } catch (Exception ignored) {}

            this.icon = resolved;
            this.name = browser.getFolderName(dir);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    browser.handleFolderCardClick(FolderCard.this, e);
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                        browser.navigate(dir);
                    }
                }
            });

            new DropTarget(this, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    if (browser.isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                        dropHighlight = true;
                        repaint();
                    } else {
                        dtde.rejectDrag();
                    }
                }
                @Override
                public void dragExit(DropTargetEvent dte) {
                    dropHighlight = false;
                    repaint();
                }
                @Override
                public void dragOver(DropTargetDragEvent dtde) {
                    if (browser.isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                    }
                }
                @Override
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent dtde) {
                    dropHighlight = false;
                    repaint();
                    if (!browser.isDragAndDropEnabled()) {
                        dtde.rejectDrop();
                        return;
                    }
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        browser.performMoveWithFeedback(files, dir);
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        logWarn("Drag-and-drop onto folder '" + dir.getName() + "' failed: " + ex.getMessage());
                        dtde.dropComplete(false);
                    }
                }
            });

            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    this, DnDConstants.ACTION_MOVE, (DragGestureEvent dge) -> {
                        if (!browser.isDragAndDropEnabled()) return;
                        final List<File> dragFolders;
                        if (browser.containsSelectedFolder(file)) {
                            dragFolders = new ArrayList<>(browser.getSelectedFolders());
                        } else {
                            browser.clearSelectionExposed();
                            browser.handleFolderCardClick(FolderCard.this,
                                    new MouseEvent(FolderCard.this, MouseEvent.MOUSE_CLICKED,
                                            System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1));
                            dragFolders = List.of(file);
                        }
                        if (dragFolders.isEmpty()) return;
                        dge.startDrag(DragSource.DefaultMoveDrop, new Transferable() {
                            @Override
                            public DataFlavor[] getTransferDataFlavors() {
                                return new DataFlavor[]{ DataFlavor.javaFileListFlavor };
                            }
                            @Override
                            public boolean isDataFlavorSupported(DataFlavor flavor) {
                                return DataFlavor.javaFileListFlavor.equals(flavor);
                            }
                            @Override
                            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                                return dragFolders;
                            }
                        });
                    });
        }

        @Override
        protected void paintContent(Graphics2D g2, int w, int h) {
            g2.setFont(cardFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            int contentH = iconSize + 5 + fm.getAscent() + fm.getDescent();
            int topOffset = Math.max(4, (h - contentH) / 2);

            if (icon != null) {
                int ix = (w - iconSize) / 2;
                icon.paintIcon(this, g2, ix, topOffset);
            }
            g2.setColor(themeForegroundColor());
            String displayName = truncToWidth(g2, truncToChars(name, MAX_DISPLAY_NAME_CHARS), w - 8);
            boolean isTruncated = !displayName.equals(name);
            if (isTruncated != nameTruncated) {
                nameTruncated = isTruncated;
                setToolTipText(isTruncated ? name : null);
            }
            int tx = Math.max(4, (w - fm.stringWidth(displayName)) / 2);
            int ty = topOffset + iconSize + 5 + fm.getAscent();
            g2.drawString(displayName, tx, ty);
        }
    }

    public static class FileCard extends ItemCard {
        private static final int W = 90;
        private static final int H = 100;
        private static final int ICON_SIZE = 64;
        private final Icon   icon;
        private final String name;
        private boolean nameTruncated = false;

        public FileCard(File file, FXBrowserPanel browser, Function<File, Icon> iconProvider) {
            super(file, W, H, ICON_SIZE);
            this.icon = iconProvider != null ? iconProvider.apply(file) : null;
            this.name = FileNameParts.of(file).baseName();

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1)
                        browser.handleFileCardClick(FileCard.this, e);
                }
            });

            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    this, DnDConstants.ACTION_MOVE, (DragGestureEvent dge) -> {
                        if (!browser.isDragAndDropEnabled()) return;
                        final List<File> dragFiles;
                        if (browser.containsSelectedFile(file)) {
                            dragFiles = new ArrayList<>(browser.getSelectedFiles());
                        } else {
                            browser.clearSelectionExposed();
                            browser.handleFileCardClick(FileCard.this,
                                    new MouseEvent(FileCard.this, MouseEvent.MOUSE_CLICKED,
                                            System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1));
                            dragFiles = List.of(file);
                        }
                        if (dragFiles.isEmpty()) return;
                        dge.startDrag(DragSource.DefaultMoveDrop, new Transferable() {
                            @Override
                            public DataFlavor[] getTransferDataFlavors() {
                                return new DataFlavor[]{ DataFlavor.javaFileListFlavor };
                            }
                            @Override
                            public boolean isDataFlavorSupported(DataFlavor flavor) {
                                return DataFlavor.javaFileListFlavor.equals(flavor);
                            }
                            @Override
                            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                                return dragFiles;
                            }
                        });
                    });
        }

        @Override
        protected void paintContent(Graphics2D g2, int w, int h) {
            g2.setFont(cardFont(Font.PLAIN, 11f));
            FontMetrics fm = g2.getFontMetrics();
            int contentH = iconSize + 5 + fm.getAscent() + fm.getDescent();
            int topOffset = Math.max(4, (h - contentH) / 2);
            if (icon != null) {
                int ix = (w - iconSize) / 2;
                icon.paintIcon(this, g2, ix, topOffset);
            }
            g2.setColor(themeForegroundColor());
            String displayName = truncToWidth(g2, truncToChars(name, MAX_DISPLAY_NAME_CHARS), w - 8);
            boolean isTruncated = !displayName.equals(name);
            if (isTruncated != nameTruncated) {
                nameTruncated = isTruncated;
                setToolTipText(isTruncated ? name : null);
            }
            int tx = Math.max(4, (w - fm.stringWidth(displayName)) / 2);
            int ty = topOffset + iconSize + 5 + fm.getAscent();
            g2.drawString(displayName, tx, ty);
        }
    }

    private static class ScrollableViewportPanel extends JPanel implements Scrollable {
        ScrollableViewportPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                Container container = target;
                while (targetWidth == 0 && container.getParent() != null) {
                    container = container.getParent();
                    targetWidth = container.getWidth();
                }
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth  = 0;
                int rowHeight = 0;

                int memberCount = target.getComponentCount();
                for (int i = 0; i < memberCount; i++) {
                    Component member = target.getComponent(i);
                    if (!member.isVisible()) continue;

                    Dimension memberSize = preferred ? member.getPreferredSize() : member.getMinimumSize();
                    if (rowWidth + memberSize.width > maxWidth && rowWidth > 0) {
                        dim.width   = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + (dim.height > 0 ? vgap : 0);
                        rowWidth  = 0;
                        rowHeight = 0;
                    }

                    if (rowWidth != 0) rowWidth += hgap;
                    rowWidth += memberSize.width;
                    rowHeight = Math.max(rowHeight, memberSize.height);
                }

                dim.width   = Math.max(dim.width, rowWidth);
                dim.height += rowHeight + (dim.height > 0 ? vgap : 0);

                dim.width  += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
    }

    public enum FilterType { ALL, FILE, FOLDER }

    public static class FXBrowserPanel extends JPanel {
        private final List<Supplier<File>> rootSuppliers;
        protected final String extension;
        protected final Set<String> displayExtensions;
        protected final Function<File, Icon> iconProvider;
        protected File currentDir;
        private final Deque<File> navHistory = new ArrayDeque<>();
        protected final Set<File> selectedFiles   = new LinkedHashSet<>();
        protected final Set<File> selectedFolders = new LinkedHashSet<>();
        protected ItemCard lastSelected = null;
        protected String filterText = "";
        protected FilterType filterType = FilterType.ALL;
        private final JButton btnUp;
        protected final JPanel  breadcrumbPanel;
        protected final JPanel  grid;
        protected final List<ItemCard> allCards = new ArrayList<>();
        private final JScrollPane scroll;
        private final AtomicInteger activeInternalMutations = new AtomicInteger(0);
        private volatile long suppressWatcherUntilMillis = 0L;
        private static final long WATCHER_ECHO_GRACE_MILLIS = 800L;
        private BiConsumer<File, File> postMoveHook = null;

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

        boolean isWatcherRefreshSuppressed() {
            return activeInternalMutations.get() > 0 || System.currentTimeMillis() < suppressWatcherUntilMillis;
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
            this.iconProvider  = iconProvider;
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

            new DropTarget(btnUp, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    if (isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && !navHistory.isEmpty()) {
                        dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                    } else {
                        dtde.rejectDrag();
                    }
                }
                @Override
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent dtde) {
                    if (!isDragAndDropEnabled() || navHistory.isEmpty()) { dtde.dropComplete(false); return; }
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        File parentDir = navHistory.peek();
                        if (parentDir != null) performMoveWithFeedback(files, parentDir);
                        dtde.dropComplete(true);
                    } catch (Exception ex) {
                        logWarn("Drag-and-drop onto the 'up' navigation button failed: " + ex.getMessage());
                        dtde.dropComplete(false);
                    }
                }
            });

            grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 8));
            grid.setOpaque(false);
            grid.setFocusable(true);
            JPanel wrapper = new ScrollableViewportPanel(new BorderLayout());
            wrapper.setOpaque(false);
            wrapper.add(grid, BorderLayout.NORTH);

            MouseAdapter deselect = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
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

            RubberBandLayer rubberBandLayer = new RubberBandLayer();
            JLayeredPane layered = new JLayeredPane();
            layered.add(scroll, JLayeredPane.DEFAULT_LAYER);
            layered.add(rubberBandLayer, JLayeredPane.PALETTE_LAYER);
            layered.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent ev) {
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
                northContainer.add(buildPersistentRootsBar());
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

            actionMap.put("photon.navLeft", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { navigateGrid(-1, 0); }
            });
            actionMap.put("photon.navRight", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { navigateGrid(1, 0); }
            });
            actionMap.put("photon.navUp", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { navigateGrid(0, -1); }
            });
            actionMap.put("photon.navDown", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { navigateGrid(0, 1); }
            });
            actionMap.put("photon.selectAll", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { selectAllVisible(); }
            });
            actionMap.put("photon.clearSelection", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { clearSelection(); }
            });
        }

        private void navigateGrid(int dCol, int dRow) {
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
                if (rows.get(i).contains(current)) { currentRow = i; break; }
            }
            int targetRow = currentRow + rowDelta;
            if (currentRow < 0 || targetRow < 0 || targetRow >= rows.size()) return null;

            int currentCenterX = current.getX() + current.getWidth() / 2;
            List<ItemCard> candidates = rows.get(targetRow);
            ItemCard best = candidates.get(0);
            int bestDist = Math.abs(best.getX() + best.getWidth() / 2 - currentCenterX);
            for (ItemCard c : candidates) {
                int dist = Math.abs(c.getX() + c.getWidth() / 2 - currentCenterX);
                if (dist < bestDist) { bestDist = dist; best = c; }
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

        private static final File VIRTUAL_ROOT_SENTINEL = new File("\0");

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
            boolean ctrl  = e.isControlDown() || e.isMetaDown();
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
            boolean ctrl  = e.isControlDown() || e.isMetaDown();
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

        private JPanel buildPersistentRootsBar() {
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            bar.setOpaque(false);
            bar.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));

            final Color normalBg = new Color(55, 55, 55);
            final Color hoverBg  = new Color(75, 75, 75);
            final Color dropBg   = themeAccentColor();

            for (Supplier<File> rootSupplier : rootSuppliers) {
                File rootDir = rootSupplier.get();
                JLabel pill = new JLabel("\u2192 " + rootDir.getName());
                pill.setFont(pill.getFont().deriveFont(Font.PLAIN, 11f));
                pill.setForeground(new Color(190, 190, 190));
                pill.setBackground(normalBg);
                pill.setOpaque(true);
                pill.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(90, 90, 90), 1),
                        BorderFactory.createEmptyBorder(2, 8, 2, 8)));
                pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                pill.setToolTipText(rootDir.getAbsolutePath());
                pill.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getButton() != MouseEvent.BUTTON1) return;
                        navHistory.clear();
                        navHistory.push(VIRTUAL_ROOT_SENTINEL);
                        currentDir = rootSupplier.get();
                        clearSelection();
                        refresh();
                    }
                    @Override public void mouseEntered(MouseEvent e) {
                        pill.setBackground(hoverBg); pill.repaint();
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        pill.setBackground(normalBg); pill.repaint();
                    }
                });
                new DropTarget(pill, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                    @Override
                    public void dragEnter(DropTargetDragEvent dtde) {
                        if (isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                            pill.setBackground(dropBg); pill.repaint();
                        } else { dtde.rejectDrag(); }
                    }
                    @Override public void dragExit(DropTargetEvent dte) {
                        pill.setBackground(normalBg); pill.repaint();
                    }
                    @Override public void dragOver(DropTargetDragEvent dtde) {
                        if (isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                            dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                    }
                    @Override
                    @SuppressWarnings("unchecked")
                    public void drop(DropTargetDropEvent dtde) {
                        pill.setBackground(normalBg);
                        pill.repaint();
                        if (!isDragAndDropEnabled()) { dtde.dropComplete(false); return; }
                        try {
                            dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                            List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                            performMoveWithFeedback(files, rootSupplier.get());
                            dtde.dropComplete(true);
                        } catch (Exception ex) {
                            logWarn("Drag-and-drop onto root shortcut '" + pill.getText() + "' failed: " + ex.getMessage());
                            dtde.dropComplete(false);
                        }
                    }
                });
                bar.add(pill);
            }
            return bar;
        }

        public File getCurrentDir() { return currentDir; }
        public List<File> getSelectedFiles()   { return new ArrayList<>(selectedFiles); }
        public List<File> getSelectedFolders() { return new ArrayList<>(selectedFolders); }

        protected boolean containsSelectedFile(File f) { return selectedFiles.contains(f); }
        protected boolean containsSelectedFolder(File f) { return selectedFolders.contains(f); }
        protected void clearSelectionExposed() { clearSelection(); }

        public void setFilter(String text) {
            filterText = (text == null) ? "" : text.trim().toLowerCase();
            refresh();
        }

        public void setFilterType(FilterType type) {
            filterType = (type == null) ? FilterType.ALL : type;
            refresh();
        }

        public FilterType getFilterType() { return filterType; }

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
                    if (resolution.action() == ACTION_CANCEL) break;
                    if (resolution.action() == ACTION_SKIP)   continue;
                    dest = resolution.targetFile();
                    if (dest.exists()) {
                        boolean removed = isDir ? deleteRecursively(dest) : dest.delete();
                        if (!removed) {
                            logWarn("Could not remove conflicting destination before move: " + dest.getAbsolutePath());
                            continue;
                        }
                    }
                }
                File finalDest = dest;
                File source = f;
                if (moveFileOrDirectory(source, finalDest)) {
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
            runFileTaskInBackground(this, () -> {
                counts[0] = files.isEmpty() ? 0 : performMove(files, destDir, resolver);
                counts[1] = (folders.isEmpty() || resolver.wasCancelled())
                        ? 0 : performMove(folders, destDir, resolver);
            }, () -> {
                try {
                    if (counts[0] + counts[1] > 0) {
                        refresh();
                        JOptionPane.showMessageDialog(this,
                                buildItemLabel(counts[0], counts[1]) + " " + L10N.t("plugin.photon.resourcemenu.action.moved")
                                        + " \u2192 \"" + getFolderName(destDir) + "\"" + autoRenameNotice(resolver));
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
                if (hasExtension(file, ext)) return true;
            }
            return false;
        }

        public void refresh() {
            List<File> roots = new ArrayList<>();
            for (Supplier<File> s : rootSuppliers) roots.add(s.get());
            List<File> dirs  = new ArrayList<>();
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
                dirs.sort((a, b)  -> a.getName().compareToIgnoreCase(b.getName()));
                files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            }

            if (!filterText.isEmpty()) {
                dirs.removeIf(d  -> !d.getName().toLowerCase().contains(filterText));
                files.removeIf(f -> !f.getName().toLowerCase().contains(filterText));
            }
            if (filterType == FilterType.FILE) dirs.clear();
            else if (filterType == FilterType.FOLDER) files.clear();

            Set<File> prevFiles   = new HashSet<>(selectedFiles);
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
            List<Object[]> segments = buildBreadcrumbSegments(roots);

            for (int i = 0; i < segments.size(); i++) {
                final File segDir  = (File)   segments.get(i)[0];
                final String label = (String) segments.get(i)[1];
                boolean isCurrent  = (i == segments.size() - 1);

                JLabel lbl = new JLabel(label);
                ComponentUtils.deriveFont(lbl, 11f);
                if (isCurrent) {
                    lbl.setForeground(new Color(200, 200, 200));
                    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                } else {
                    lbl.setForeground(themeAccentColor());
                    lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    lbl.addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) {
                            if (e.getButton() == MouseEvent.BUTTON1) navigateDirect(segDir);
                        }
                        @Override public void mouseEntered(MouseEvent e) {
                            lbl.setForeground(shade(themeAccentColor(), 30));
                        }
                        @Override public void mouseExited(MouseEvent e) {
                            lbl.setForeground(themeAccentColor());
                        }
                    });
                    new DropTarget(lbl, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                        @Override
                        public void dragEnter(DropTargetDragEvent dtde) {
                            if (isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                            else dtde.rejectDrag();
                        }
                        @Override
                        @SuppressWarnings("unchecked")
                        public void drop(DropTargetDropEvent dtde) {
                            if (!isDragAndDropEnabled()) { dtde.dropComplete(false); return; }
                            try {
                                dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                                List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                                if (segDir != null) performMoveWithFeedback(files, segDir);
                                dtde.dropComplete(true);
                            } catch (Exception ex) {
                                logWarn("Drag-and-drop onto breadcrumb '" + lbl.getText() + "' failed: " + ex.getMessage());
                                dtde.dropComplete(false);
                            }
                        }
                    });
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

        private List<Object[]> buildBreadcrumbSegments(List<File> roots) {
            List<Object[]> segments = new ArrayList<>();
            if (currentDir == null) {
                segments.add(new Object[]{ null, "/" });
                return segments;
            }
            for (File root : roots) {
                String rootAbs = root.getAbsolutePath();
                String currAbs = currentDir.getAbsolutePath();
                boolean isInside = currAbs.equals(rootAbs) || currAbs.startsWith(rootAbs + File.separator);
                if (!isInside) continue;

                if (rootSuppliers.size() > 1) {
                    segments.add(new Object[]{ null, "/" });
                }
                segments.add(new Object[]{ root, getFolderName(root) });

                String rel = currAbs.substring(rootAbs.length());
                if (!rel.isEmpty()) {
                    String[] parts = rel.substring(1).split(Pattern.quote(File.separator));
                    File cursor = root;
                    for (String part : parts) {
                        cursor = new File(cursor, part);
                        segments.add(new Object[]{ cursor, getFolderName(cursor) });
                    }
                }
                return segments;
            }
            segments.add(new Object[]{ currentDir, getFolderName(currentDir) });
            return segments;
        }

        private class RubberBandLayer extends JPanel {
            private static final int   THRESHOLD = 6;
            private static Color fillClr() { return themeAccentColor(50); }
            private static Color edgeClr() { return themeAccentColor(190); }
            private Point pressPoint;
            private Point dragPoint;
            private boolean banding = false;
            private boolean dispatching = false;
            private Component hoveredCard = null;
            private Component pressedComponent = null;
            private Component lastClickedComponent = null;

            RubberBandLayer() {
                setOpaque(false);
                MouseAdapter ma = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        pressPoint = e.getPoint();
                        dragPoint = e.getPoint();
                        banding = false;
                        pressedComponent = bestTargetAt(e.getPoint());
                        lastClickedComponent = pressedComponent;

                        if (!(pressedComponent instanceof ItemCard) && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
                            clearSelection();
                            grid.requestFocusInWindow();
                        }

                        redispatchTo(pressedComponent, e);
                    }
                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (pressPoint == null) return;
                        dragPoint = e.getPoint();
                        if (pressedComponent instanceof ItemCard) {
                            redispatchTo(pressedComponent, e);
                            return;
                        }
                        int dx = Math.abs(dragPoint.x - pressPoint.x);
                        int dy = Math.abs(dragPoint.y - pressPoint.y);
                        if (!banding && (dx > THRESHOLD || dy > THRESHOLD)) {
                            banding = true;
                            updateHover(null);
                        }
                        if (banding) {
                            applyRubberBandSelection(e.isControlDown() || e.isMetaDown());
                            repaint();
                        }
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        boolean wasBanding = banding;
                        banding    = false;
                        pressPoint = null;
                        dragPoint  = null;
                        repaint();
                        if (!wasBanding && pressedComponent != null) redispatchTo(pressedComponent, e);
                        if (wasBanding) lastClickedComponent = null;
                        pressedComponent = null;
                    }
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (lastClickedComponent != null) {
                            redispatchTo(lastClickedComponent, e);
                            lastClickedComponent = null;
                        }
                    }
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        updateHover(e.getPoint());
                        if (hoveredCard != null) {
                            Point cp = SwingUtilities.convertPoint(RubberBandLayer.this, e.getPoint(), hoveredCard);
                            synthesise(hoveredCard, MouseEvent.MOUSE_MOVED, cp.x, cp.y);
                        }
                    }
                    @Override public void mouseExited(MouseEvent e) { updateHover(null); }
                };
                addMouseListener(ma);
                addMouseMotionListener(ma);
            }

            private Component deepestAt(Point layerPt) {
                Point sp = SwingUtilities.convertPoint(this, layerPt, scroll);
                Component c = SwingUtilities.getDeepestComponentAt(scroll, sp.x, sp.y);
                return c != null ? c : scroll;
            }

            private Component bestTargetAt(Point layerPt) {
                Component deep = deepestAt(layerPt);
                if (deep == null) return null;
                Component c = deep;
                while (c != null && !(c instanceof ItemCard)) {
                    if (c == scroll) return deep;
                    c = c.getParent();
                }
                return (c instanceof ItemCard) ? c : deep;
            }

            private Component deepestCard(Point layerPt) {
                Component c = deepestAt(layerPt);
                while (c != null && !(c instanceof ItemCard)) {
                    if (c == scroll) return null;
                    c = c.getParent();
                }
                return (c instanceof ItemCard) ? c : null;
            }

            private void redispatchTo(Component target, MouseEvent src) {
                if (target == null || target == this || dispatching) return;
                dispatching = true;
                try { target.dispatchEvent(SwingUtilities.convertMouseEvent(this, src, target)); }
                finally { dispatching = false; }
            }

            private void updateHover(Point layerPt) {
                Component under = (layerPt != null) ? deepestCard(layerPt) : null;
                if (under == hoveredCard) return;
                if (hoveredCard != null) synthesise(hoveredCard, MouseEvent.MOUSE_EXITED, -1, -1);
                if (under != null && layerPt != null) {
                    Point cp = SwingUtilities.convertPoint(this, layerPt, under);
                    synthesise(under, MouseEvent.MOUSE_ENTERED, cp.x, cp.y);
                }
                hoveredCard = under;
            }

            private void synthesise(Component target, int id, int x, int y) {
                if (dispatching) return;
                dispatching = true;
                try {
                    target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), 0, x, y, 0, false));
                } finally { dispatching = false; }
            }

            private void applyRubberBandSelection(boolean additive) {
                Rectangle band = getBandRect();
                if (band == null) return;

                for (ItemCard card : allCards) {
                    Rectangle cb = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), RubberBandLayer.this);
                    boolean hit = band.intersects(cb);
                    File f = card.getFile();
                    if (additive) {
                        if (hit) {
                            card.setSelected(true);
                            if (card instanceof FolderCard) selectedFolders.add(f);
                            else if (card instanceof FileCard) selectedFiles.add(f);
                        }
                    } else {
                        card.setSelected(hit);
                        if (card instanceof FolderCard) {
                            if (hit) selectedFolders.add(f);
                            else selectedFolders.remove(f);
                        } else if (card instanceof FileCard) {
                            if (hit) selectedFiles.add(f);
                            else selectedFiles.remove(f);
                        }
                    }
                }
                lastSelected = null;
            }

            private Rectangle getBandRect() {
                if (pressPoint == null || dragPoint == null) return null;
                return new Rectangle(
                        Math.min(pressPoint.x, dragPoint.x),
                        Math.min(pressPoint.y, dragPoint.y),
                        Math.abs(pressPoint.x - dragPoint.x),
                        Math.abs(pressPoint.y - dragPoint.y));
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Rectangle r = getBandRect();
                if (!banding || r == null || (r.width < 1 && r.height < 1)) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fillClr());
                g2.fillRect(r.x, r.y, r.width, r.height);
                g2.setColor(edgeClr());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(r.x, r.y, r.width, r.height);
                g2.dispose();
            }
        }
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension,
                "plugin.photon.resourcemenu.import", "16px_photon.import", null);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, BiConsumer<File, List<File>> onImported) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension,
                "plugin.photon.resourcemenu.import", "16px_photon.import", onImported);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, String menuKey, String iconKey) {
        return buildImportButton(mcreator, browser, targetDirGetter, extension, menuKey, iconKey, null);
    }

    public static JButton buildImportButton(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                            String extension, String menuKey, String iconKey,
                                            BiConsumer<File, List<File>> onImported) {
        JButton button = createToolbarButton(L10N.t(menuKey), iconKey);
        button.addActionListener(e -> {
            File[] sourceFiles = FileDialogs.getMultiOpenDialog(mcreator, new String[]{extension});
            if (sourceFiles == null || sourceFiles.length == 0) return;

            File targetDir;
            try {
                File navigatedDir = browser.getCurrentDir();
                targetDir = (navigatedDir != null && navigatedDir.isDirectory()) ? navigatedDir : targetDirGetter.get();
                if (!targetDir.exists()) targetDir.mkdirs();
            } catch (Exception ex) {
                logError("Failed to resolve import target directory", ex);
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            final File finalTargetDir = targetDir;
            boolean strict = requiresStrictNaming(extension);
            int conflictCount = (int) Arrays.stream(sourceFiles)
                    .filter(src -> new File(finalTargetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver overwriteResolver = new BulkConflictResolver(conflictCount, sourceFiles.length >= 2);
            int invalidNameCount = strict
                    ? (int) Arrays.stream(sourceFiles)
                    .filter(src -> !isValidStrictFileName(src.getName()))
                    .count()
                    : 0;
            InvalidNameResolver nameResolver = new InvalidNameResolver(invalidNameCount);
            List<File> importedFiles = new ArrayList<>();
            Exception[] failure = new Exception[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File source : sourceFiles) {
                        String fileName = source.getName();
                        if (strict) {
                            fileName = nameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }
                        File destFile = new File(finalTargetDir, fileName);
                        ConflictResolution resolution = overwriteResolver.resolve(mcreator, destFile);
                        if (resolution.action() == ACTION_CANCEL) break;
                        if (resolution.action() == ACTION_SKIP) continue;
                        destFile = resolution.targetFile();
                        FileIO.copyFile(source, destFile);
                        importedFiles.add(destFile);
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                try {
                    if (failure[0] != null) {
                        logError("Failed to import files into '" + finalTargetDir.getAbsolutePath() + "'", failure[0]);
                        JOptionPane.showMessageDialog(mcreator,
                                L10N.t("plugin.photon.resourcemenu.import.failed") + failure[0].getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    } else if (!importedFiles.isEmpty()) {
                        if (onImported != null) onImported.accept(finalTargetDir, importedFiles);
                        browser.refresh();
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(importedFiles.size(), 0) + " " + L10N.t("plugin.photon.resourcemenu.action.imported")
                                + " \u2192 \"" + finalTargetDir.getName() + "\"" + autoRenameNotice(overwriteResolver));
                    } else {
                        JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    }
                } finally {
                    browser.endInternalMutation();
                }
            });
        });
        return button;
    }

    public static JButton buildDeleteButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.delete.files"), "16px_photon.delete");
        btn.addActionListener(e -> performDeleteSelected(mcreator, browser));
        bindDeleteKey(mcreator, browser, () -> performDeleteSelected(mcreator, browser));
        return btn;
    }

    public static void bindDeleteKey(MCreator mcreator, FXBrowserPanel browser, Runnable action) {
        browser.grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "photon.deleteSelected");
        browser.grid.getActionMap().put("photon.deleteSelected", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private enum FolderDeleteAction { DELETE_EMPTY, MOVE_CONTENTS, DELETE_ALL }

    private record FolderDeleteDecision(File folder, FolderDeleteAction action, File[] contents) {}

    private static void performDeleteSelected(MCreator mcreator, FXBrowserPanel browser) {
        List<File> fileTargets = browser.getSelectedFiles();
        List<File> folderTargets = browser.getSelectedFolders().stream()
                .filter(File::isDirectory)
                .collect(java.util.stream.Collectors.toList());
        if (fileTargets.isEmpty() && folderTargets.isEmpty()) {
            JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.nothing.selected"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean cancelAll = false;
        boolean deleteConfirmedFiles = false;

        if (!fileTargets.isEmpty()) {
            final String msg = buildDeleteConfirmationMessage(browser, fileTargets, folderTargets);
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

        List<FolderDeleteDecision> decisions = new ArrayList<>();
        for (int fi = 0; fi < folderTargets.size() && !cancelAll; fi++) {
            File folder = folderTargets.get(fi);
            if (!folder.isDirectory()) continue;
            File[] contents    = folder.listFiles();
            int    contentCount = (contents != null) ? contents.length : 0;

            if (contentCount == 0) {
                int choice = JOptionPane.showConfirmDialog(
                        mcreator,
                        L10N.t("plugin.photon.resourcemenu.delete.folder.empty.confirm", browser.getFolderName(folder)),
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                    cancelAll = true;
                } else if (choice == JOptionPane.YES_OPTION) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.DELETE_EMPTY, contents));
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

                String folderMsg = buildFolderContentsPreviewMessage(progressPrefix, browser.getFolderName(folder),
                        contents != null ? Arrays.asList(contents) : List.of());

                int action = JOptionPane.showOptionDialog(
                        mcreator, folderMsg,
                        L10N.t("plugin.photon.resourcemenu.operation.warning"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                        null, options, options[options.length - 1]);

                if (action == JOptionPane.CLOSED_OPTION || action == cancelOptIdx) {
                    cancelAll = true;
                } else if (action == 0) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.MOVE_CONTENTS, contents));
                } else if (action == 1) {
                    decisions.add(new FolderDeleteDecision(folder, FolderDeleteAction.DELETE_ALL, contents));
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
                for (File f : fileTargets) {
                    if (f.delete()) counts[0]++;
                }
            }
            for (FolderDeleteDecision decision : decisions) {
                File folder = decision.folder();
                switch (decision.action()) {
                    case DELETE_EMPTY -> {
                        if (folder.delete()) counts[1]++;
                    }
                    case MOVE_CONTENTS -> {
                        File parent = folder.getParentFile();
                        int failed = 0;
                        File[] contents = decision.contents();
                        if (contents != null) {
                            for (File child : contents) {
                                File dest = new File(parent, child.getName());
                                if (dest.exists() || !moveFileOrDirectory(child, dest)) failed++;
                            }
                        }
                        if (failed > 0) {
                            int finalFailed = failed;
                            onEdtVoid(() -> JOptionPane.showMessageDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.delete.move.contents.partial.fail", finalFailed, browser.getFolderName(folder)),
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.WARNING_MESSAGE));
                        }
                        File[] remaining = folder.listFiles();
                        if (remaining == null || remaining.length == 0) {
                            if (folder.delete()) counts[1]++;
                        }
                    }
                    case DELETE_ALL -> {
                        if (deleteRecursively(folder)) {
                            counts[1]++;
                        } else {
                            onEdtVoid(() -> JOptionPane.showMessageDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.folder.delete.error") + "\n" + browser.getFolderName(folder),
                                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                        }
                    }
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

    public static JButton buildCreateFolderButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.create.folder"), "16px_photon.create_folder");
        btn.addActionListener(e -> {
            browser.beginInternalMutation();
            try {
                File targetDir = browser.getCurrentDir();
                if (targetDir == null) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.select.root.first"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.create.error"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String folderName = JOptionPane.showInputDialog(mcreator, L10N.t("plugin.photon.resourcemenu.folder.name"));
                if (folderName == null || folderName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    return;
                }
                folderName = folderName.trim();

                folderName = new InvalidNameResolver(1).resolve(mcreator, folderName, false);
                if (folderName == null) {
                    JOptionPane.showMessageDialog(mcreator, L10N.t("plugin.photon.resourcemenu.operation.canceled"));
                    return;
                }
                File newFolder = new File(targetDir, folderName);
                if (newFolder.exists()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.already.exists"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.WARNING_MESSAGE);
                } else if (!newFolder.mkdirs()) {
                    JOptionPane.showMessageDialog(mcreator,
                            L10N.t("plugin.photon.resourcemenu.folder.create.error"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"),
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    browser.refresh();
                    JOptionPane.showMessageDialog(mcreator, "\"" + folderName + "\" " + L10N.t("plugin.photon.resourcemenu.action.created"));
                }
            } finally {
                browser.endInternalMutation();
            }
        });
        return btn;
    }

    public static JButton buildExportButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.export.files"), "16px_photon.export");
        btn.addActionListener(e -> {
            List<File> fileTargets   = browser.getSelectedFiles();
            List<File> folderTargets = browser.getSelectedFolders().stream()
                    .filter(File::isDirectory)
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
            int conflictCount = BulkConflictResolver.countConflicts(fileTargets, dest)
                    + BulkConflictResolver.countConflicts(folderTargets, dest);
            BulkConflictResolver resolver = (conflictCount > 0) ? new BulkConflictResolver(conflictCount) : null;

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
                        File destFolder = new File(dest, folder.getName());
                        boolean folderRenamed = false;
                        if (destFolder.exists()) {
                            ConflictResolution resolution = (resolver != null) ? resolver.resolve(mcreator, destFolder) : checkOverwrite(mcreator, destFolder);
                            if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                            if (resolution.action() == ACTION_SKIP)   continue;
                            folderRenamed = !resolution.targetFile().equals(destFolder);
                            destFolder = resolution.targetFile();
                            if (destFolder.exists()) deleteRecursively(destFolder);
                        }
                        boolean recursive = true;
                        boolean hasSubDirs = false;
                        File[] children = folder.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                if (child.isDirectory()) { hasSubDirs = true; break; }
                            }
                        }
                        if (hasSubDirs) {
                            int choice = onEdt(() -> JOptionPane.showConfirmDialog(
                                    mcreator,
                                    L10N.t("plugin.photon.resourcemenu.export.folder.has.subfolders", browser.getFolderName(folder)),
                                    L10N.t("plugin.photon.resourcemenu.operation.question"),
                                    JOptionPane.YES_NO_CANCEL_OPTION,
                                    JOptionPane.QUESTION_MESSAGE));
                            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                                cancelAllHolder[0] = true; break;
                            }
                            recursive = (choice == JOptionPane.YES_OPTION);
                        }
                        if (recursive) {
                            if (folderRenamed) {
                                if (!destFolder.exists()) destFolder.mkdirs();
                                copyFolderContents(folder, destFolder);
                            } else {
                                copyDirectoryRecursively(folder, dest);
                            }
                        } else {
                            if (!destFolder.exists()) destFolder.mkdirs();
                            copyFolderContentsShallow(folder, destFolder);
                        }
                        counts[1]++;
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                if (failure[0] != null) {
                    logError("Failed to export selected files/folders", failure[0]);
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

    public static JButton buildCloneButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        return buildCloneButtonForBrowser(mcreator, browser, targetDirGetter, "16px_photon.clone");
    }

    public static JButton buildCloneButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter, String iconKey) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.clone.element"), iconKey);
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
            File currentDir = onEdt(browser::getCurrentDir);
            File cloneDestDir = (currentDir != null && currentDir.exists()) ? currentDir : rootDir;
            int total = sourceFiles.size() + sourceFolders.size();
            BulkConflictResolver resolver = new BulkConflictResolver(total);
            InvalidNameResolver nameResolver = new InvalidNameResolver(total);

            int[] counts = new int[2];
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

                    File destFile = new File(destDir, cloneName + canonicalExtension(parts.extension()));
                    ConflictResolution resolution = resolver.resolve(mcreator, destFile);
                    if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                    if (resolution.action() == ACTION_SKIP)   continue;
                    destFile = resolution.targetFile();

                    try {
                        if (destFile.exists()) destFile.delete();
                        FileIO.copyFile(src, destFile);
                        counts[0]++;
                    } catch (Exception ex) {
                        logError("Failed to clone file '" + src.getAbsolutePath() + "' to '" + destFile.getAbsolutePath() + "'", ex);
                        onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
                }

                for (File src : sourceFolders) {
                    if (cancelAllHolder[0]) break;
                    String progress = "(" + (++indexHolder[0]) + " / " + total + ")";
                    String cloneName = promptCloneFolderName(mcreator, browser.getFolderName(src), progress, nameResolver);
                    if (cloneName == null) { cancelAllHolder[0] = true; break; }

                    File destDir = cloneDestDir;

                    File destFolder = new File(destDir, cloneName);
                    if (destFolder.exists()) {
                        ConflictResolution resolution = resolver.resolve(mcreator, destFolder);
                        if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; break; }
                        if (resolution.action() == ACTION_SKIP)   continue;
                        destFolder = resolution.targetFile();
                        if (destFolder.exists()) deleteRecursively(destFolder);
                    }

                    boolean hasSubDirs = false;
                    File[] children = src.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            if (child.isDirectory()) { hasSubDirs = true; break; }
                        }
                    }
                    boolean recursive = true;
                    if (hasSubDirs) {
                        int choice = onEdt(() -> JOptionPane.showConfirmDialog(
                                mcreator,
                                L10N.t("plugin.photon.resourcemenu.clone.folder.has.subfolders", browser.getFolderName(src)),
                                L10N.t("plugin.photon.resourcemenu.operation.question"),
                                JOptionPane.YES_NO_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE));
                        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                            cancelAllHolder[0] = true; break;
                        }
                        recursive = (choice == JOptionPane.YES_OPTION);
                    }
                    File finalDestFolder = destFolder;
                    boolean finalRecursive = recursive;
                    try {
                        if (!finalDestFolder.mkdirs()) throw new IOException("Cannot create directory: " + finalDestFolder.getAbsolutePath());
                        if (finalRecursive) copyFolderContents(src, finalDestFolder);
                        else copyFolderContentsShallow(src, finalDestFolder);
                        counts[1]++;
                    } catch (Exception ex) {
                        logError("Failed to clone folder '" + src.getAbsolutePath() + "' to '" + finalDestFolder.getAbsolutePath() + "'", ex);
                        onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                                progress + " " + L10N.t("plugin.photon.resourcemenu.import.failed") + ex.getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    }
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

    public static String promptCloneName(MCreator mcreator, FileNameParts parts, String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = onEdt(() -> JOptionPane.showInputDialog(mcreator,
                    progress + "  " + L10N.t("plugin.photon.resourcemenu.clone.name.message", canonicalExtension(parts.extension())) + "\n"
                            + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + parts.baseName(),
                    parts.baseName() + "_copy"));
            if (name == null) return null;
            name = name.trim();
            if (name.isEmpty()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            if (requiresStrictNaming(parts.extension())) {

                name = nameResolver.resolve(mcreator, name, progress);
                if (name == null) continue;
            } else if (!isValidFileName(name)) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            if (name.equalsIgnoreCase(parts.baseName())) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.clone.same.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            return name;
        }
    }

    static String promptCloneFolderName(MCreator mcreator, String currentFolderName, String progress, InvalidNameResolver nameResolver) {
        while (true) {
            String name = onEdt(() -> JOptionPane.showInputDialog(mcreator,
                    progress + "  " + L10N.t("plugin.photon.resourcemenu.clone.folder.name.message") + "\n"
                            + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + currentFolderName,
                    currentFolderName + "_copy"));
            if (name == null) return null;
            name = name.trim();
            if (name.isEmpty()) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }

            name = nameResolver.resolve(mcreator, name, progress, false);
            if (name == null) continue;
            if (name.equalsIgnoreCase(currentFolderName)) {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.clone.same.name.error"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                continue;
            }
            return name;
        }
    }

    public record RenameOutcome(int status, File dest) {
        private static final int RENAMED = 0, SKIPPED = 1, CANCELLED = 2;

        static RenameOutcome renamed(File dest) { return new RenameOutcome(RENAMED, dest); }
        static RenameOutcome skipped()          { return new RenameOutcome(SKIPPED, null); }
        static RenameOutcome cancelled()        { return new RenameOutcome(CANCELLED, null); }

        public boolean isRenamed()   { return status == RENAMED; }
        public boolean isCancelled() { return status == CANCELLED; }
    }

    public static RenameOutcome promptAndRenameFile(Component parent, File item, String progress,
                                                    InvalidNameResolver nameResolver, BulkConflictResolver resolver) {
        FileNameParts parts = FileNameParts.of(item);
        String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message.file", canonicalExtension(parts.extension())) + "\n"
                + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + item.getName();
        String newName = onEdt(() -> JOptionPane.showInputDialog(parent, hint, parts.baseName()));
        if (newName == null) return RenameOutcome.cancelled();
        newName = newName.trim();
        if (newName.isEmpty()) {
            onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        String extension = parts.extension();
        if (requiresStrictNaming(extension)) {
            newName = nameResolver.resolve(parent, newName, progress);
            if (newName == null) return RenameOutcome.skipped();
            extension = canonicalExtension(extension);
        } else if (!isValidFileName(newName)) {
            onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.invalid.chars.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        File dest = new File(item.getParentFile(), newName + extension);
        if (isSamePathExact(dest, item)) return RenameOutcome.skipped();

        if (isSamePathIgnoreCase(dest, item)) {
            if (renameFile(item, dest)) {
                return RenameOutcome.renamed(dest);
            }
            onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + item.getName(),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return RenameOutcome.skipped();
        }

        ConflictResolution resolution = resolver.resolve(parent, dest);
        if (resolution.action() == ACTION_CANCEL) return RenameOutcome.cancelled();
        if (resolution.action() == ACTION_SKIP) return RenameOutcome.skipped();
        dest = resolution.targetFile();
        if (dest.exists()) {
            boolean removed = dest.delete();
            if (!removed) {
                logWarn("Could not remove conflicting destination before rename: " + dest.getAbsolutePath());
                return RenameOutcome.skipped();
            }
        }
        if (renameFile(item, dest)) {
            return RenameOutcome.renamed(dest);
        }
        onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + item.getName(),
                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
        return RenameOutcome.skipped();
    }

    private static void renameOneFolder(MCreator mcreator, FXBrowserPanel browser, File item, String progress,
                                        InvalidNameResolver nameResolver, BulkConflictResolver resolver,
                                        int[] counts, boolean[] cancelAllHolder) {
        String hint = progress + "  " + L10N.t("plugin.photon.resourcemenu.rename.input.message") + "\n"
                + L10N.t("plugin.photon.resourcemenu.rename.current.name") + ": " + browser.getFolderName(item);
        String newName = onEdt(() -> JOptionPane.showInputDialog(mcreator, hint, browser.getFolderName(item)));
        if (newName == null) { cancelAllHolder[0] = true; return; }
        newName = newName.trim();
        if (newName.isEmpty()) {
            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            return;
        }

        newName = nameResolver.resolve(mcreator, newName, progress, false);
        if (newName == null) return;

        File dest = new File(item.getParentFile(), newName);
        if (isSamePathExact(dest, item)) return;

        if (isSamePathIgnoreCase(dest, item)) {
            if (renameFile(item, dest)) {
                counts[1]++;
            } else {
                onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                        progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + browser.getFolderName(item),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
            }
            return;
        }

        ConflictResolution resolution = resolver.resolve(mcreator, dest);
        if (resolution.action() == ACTION_CANCEL) { cancelAllHolder[0] = true; return; }
        if (resolution.action() == ACTION_SKIP) return;
        dest = resolution.targetFile();
        if (dest.exists()) {
            boolean removed = deleteRecursively(dest);
            if (!removed) {
                logWarn("Could not remove conflicting destination before rename: " + dest.getAbsolutePath());
                return;
            }
        }
        if (renameFile(item, dest)) {
            counts[1]++;
        } else {
            onEdtVoid(() -> JOptionPane.showMessageDialog(mcreator,
                    progress + " " + L10N.t("plugin.photon.resourcemenu.rename.error") + "\n" + browser.getFolderName(item),
                    L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
        }
    }

    public static JButton buildRenameButtonForBrowser(MCreator mcreator, FXBrowserPanel browser) {
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
                        renameOneFolder(mcreator, browser, item, progress, nameResolver, resolver, counts, cancelAllHolder);
                    } else {
                        RenameOutcome outcome = promptAndRenameFile(mcreator, item, progress, nameResolver, resolver);
                        if (outcome.isCancelled()) { cancelAllHolder[0] = true; break; }
                        if (outcome.isRenamed()) counts[0]++;
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

    public static JButton buildMoveButtonForBrowser(MCreator mcreator, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        JButton btn = createToolbarButton(L10N.t("plugin.photon.resourcemenu.move.files"), "16px_photon.move");
        btn.addActionListener(e -> {
            List<File> selFiles   = browser.getSelectedFiles();
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
            File baseDir = targetDirGetter.get();
            if (!baseDir.exists()) {
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.move.no.base.dir"),
                        L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            Set<String> excludedPaths = new HashSet<>();
            for (File f : itemsToMove) excludedPaths.add(f.getAbsolutePath());
            File destDir = showFolderPickerDialog(mcreator, baseDir, baseDir.getName(), excludedPaths);
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
                        if (resolution.action() == ACTION_SKIP)   continue;
                        dest = resolution.targetFile();
                        if (dest.exists()) {
                            boolean removed = isDir ? deleteRecursively(dest) : dest.delete();
                            if (!removed) {
                                logWarn("Could not remove conflicting destination before move: " + dest.getAbsolutePath());
                                continue;
                            }
                        }
                    }
                    if (renameFile(f, dest)) {
                        if (isDir) counts[1]++;
                        else counts[0]++;
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

    public static JComponent buildFilterBarForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JPanel bar = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color base = themeAltBackgroundColor();
                g2.setPaint(new GradientPaint(0, 0, shade(base, 12), 0, h, shade(base, -12)));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g2.setColor(themeAccentColor());
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 6));

        JTextField searchField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(shade(themeForegroundColor(), -60));
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(L10N.t("plugin.photon.resourcemenu.filter.placeholder"), getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        searchField.setOpaque(false);
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        searchField.setForeground(themeForegroundColor());
        searchField.setCaretColor(themeForegroundColor());
        ComponentUtils.deriveFont(searchField, 12f);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { apply(); }
            @Override public void removeUpdate(DocumentEvent e) { apply(); }
            @Override public void changedUpdate(DocumentEvent e) { apply(); }
            private void apply() { browser.setFilter(searchField.getText()); }
        });

        JLabel divider = new JLabel("|");
        divider.setForeground(shade(themeForegroundColor(), -40));
        ComponentUtils.deriveFont(divider, 12f);

        JLabel filterLabel = new JLabel(L10N.t("plugin.photon.resourcemenu.filter.elements"));
        filterLabel.setForeground(themeForegroundColor());
        ComponentUtils.deriveFont(filterLabel, 12f);
        filterLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        filterLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));

        JPopupMenu popup = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (FilterType type : FilterType.values()) {
            String label = switch (type) {
                case ALL -> L10N.t("plugin.photon.resourcemenu.filter.option.all");
                case FILE -> L10N.t("plugin.photon.resourcemenu.filter.option.file");
                case FOLDER -> L10N.t("plugin.photon.resourcemenu.filter.option.folder");
            };
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, type == FilterType.ALL);
            item.addActionListener(e -> browser.setFilterType(type));
            group.add(item);
            popup.add(item);
        }
        filterLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { popup.show(filterLabel, 0, filterLabel.getHeight()); }
        });

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rightSide.setOpaque(false);
        rightSide.add(divider);
        rightSide.add(filterLabel);

        bar.add(searchField, BorderLayout.CENTER);
        bar.add(rightSide, BorderLayout.EAST);
        bar.setPreferredSize(new Dimension(230, 24));
        return bar;
    }

    public record FolderPickerNode(File dir, String label) {
        @Override public String toString() { return label; }
    }

    private static DefaultMutableTreeNode buildFolderOnlyTree(File dir, String label, Set<String> excludePaths) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FolderPickerNode(dir, label));
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs != null) {
            Arrays.sort(subs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File sub : subs) {
                if (!excludePaths.contains(sub.getAbsolutePath())) {
                    node.add(buildFolderOnlyTree(sub, sub.getName(), excludePaths));
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

        tree.setBackground(themeBackgroundColor());
        tree.setForeground(themeForegroundColor());
        tree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            {
                setTextNonSelectionColor(themeForegroundColor());
                setTextSelectionColor(Color.WHITE);
                setBackgroundNonSelectionColor(themeBackgroundColor());
                setBackgroundSelectionColor(themeAccentColor());
                setBorderSelectionColor(shade(themeAccentColor(), 30));
            }
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode dmtn && dmtn.getUserObject() instanceof FolderPickerNode fpn) {
                    setText(fpn.label());
                    Icon fi = UIManager.getIcon("FileView.directoryIcon");
                    if (fi != null) setIcon(fi);
                }
                setForeground(sel ? Color.WHITE : themeForegroundColor());
                setBackground(sel ? themeAccentColor() : themeBackgroundColor());
                setOpaque(sel);
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(new Dimension(320, 280));
        scroll.setBorder(BorderFactory.createLineBorder(shade(themeBackgroundColor(), 25)));
        scroll.getViewport().setBackground(themeBackgroundColor());
        int result = JOptionPane.showConfirmDialog(
                parent, scroll,
                L10N.t("plugin.photon.resourcemenu.move.select.target.folder"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;
        javax.swing.tree.TreePath selPath = tree.getSelectionPath();
        if (selPath == null) return fallback;
        DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        if (selNode.getUserObject() instanceof FolderPickerNode fpn) return fpn.dir();
        return fallback;
    }
}
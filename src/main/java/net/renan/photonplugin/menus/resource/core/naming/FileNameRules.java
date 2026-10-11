package net.renan.photonplugin.menus.resource.core.naming;

import net.mcreator.ui.init.L10N;

import java.io.File;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class FileNameRules {

    private static final Set<String> STRICT_NAMING_EXTENSIONS = Set.of(".fx", ".fxpack", ".png", ".nbt", ".obj");
    private static final Pattern STRICT_FILE_NAME_PATTERN = Pattern.compile("^[a-z0-9_\\-.]+$");
    private static final Set<Character> OS_FORBIDDEN_CHARS = Set.of('<', '>', ':', '"', '/', '\\', '|', '?', '*');
    private static final Set<String> OS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNameRules() {
    }

    public static boolean isValidFileName(String name) {
        return isValidLooseFileName(name);
    }

    public static boolean requiresStrictNaming(String extension) {
        return extension != null && STRICT_NAMING_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public static boolean hasExtension(File file, String extension) {
        return file != null && hasExtension(file.getName(), extension);
    }

    public static boolean hasExtension(String fileName, String extension) {
        if (fileName == null || extension == null || extension.isEmpty()) return false;
        return fileName.regionMatches(true, fileName.length() - extension.length(), extension, 0, extension.length());
    }

    public static String canonicalExtension(String extension) {
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
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            char lower = Character.toLowerCase(c);
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

    public static String buildInvalidNameMessage(String charsMessageKey, String caseOnlyMessageKey, String name, String suggestion) {
        Set<Character> invalidChars = findInvalidStrictChars(name);
        if (invalidChars.isEmpty()) {
            return L10N.t(caseOnlyMessageKey, name, suggestion);
        }
        return L10N.t(charsMessageKey, name, formatInvalidCharsLabel(invalidChars), suggestion);
    }

    public static String buildInvalidNameMessageLoose(String name, String suggestion) {
        Set<Character> invalidChars = findInvalidLooseChars(name);
        if (!invalidChars.isEmpty()) {
            return L10N.t("plugin.photon.resourcemenu.name.invalid.chars.loose.message",
                    name, formatInvalidCharsLabel(invalidChars), suggestion);
        }
        return L10N.t("plugin.photon.resourcemenu.name.invalid.reserved.message", name, suggestion);
    }
}

package net.renan.photonplugin.menus.resource.neo.fxpacks.usages;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.elements.ModElement;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FxPackUsageSearcher {

    public static final String FXPACK_EXTENSION = ".fxpack";

    private static final Set<String> SKIP_DIRS = Set.of("photon_plugin");

    private static final Pattern FXPACK_FX_ENTRY = Pattern.compile("^assets/([^/]+)/fx/(.+)\\.fx$");

    private static final Pattern FX_ACTION_PATTERN = Pattern.compile(
            "\\b(?:(?:Summon|Remove|Modify)FX(?:Entity|Block)(?:Server|Client)\\s*\\.\\s*(?:create|destroy|modify)"
                    + "|CheckFX(?:Entity|Block)\\s*\\.\\s*check)"
                    + "\\s*\\([^;{}]*\\)\\s*\\.\\s*name\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"
    );

    private FxPackUsageSearcher() {
    }

    public static List<PackUsages> search(MCreator mcreator, List<File> fxPackFiles) {
        List<PackUsages> packs = new ArrayList<>();
        for (File file : fxPackFiles) packs.add(PackUsages.of(file));

        Map<String, List<PackUsages>> packsByFxName = new HashMap<>();
        for (PackUsages pack : packs) {
            for (String fxName : pack.fxNames())
                packsByFxName.computeIfAbsent(fxName, k -> new ArrayList<>()).add(pack);
        }
        if (packsByFxName.isEmpty()) return packs;

        Map<String, ModElement> byName = new HashMap<>();
        for (ModElement me : mcreator.getWorkspace().getModElements())
            byName.put(me.getName(), me);

        List<String> namesByLengthDesc = byName.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());

        String modPackage = mcreator.getWorkspace().getWorkspaceSettings().getModElementsPackage();
        File packageDir = new File(mcreator.getWorkspace().getWorkspaceFolder(),
                "src/main/java/" + modPackage.replace('.', '/'));
        searchSourceDir(packageDir, byName, namesByLengthDesc, packsByFxName);
        return packs;
    }

    private static void searchSourceDir(File dir, Map<String, ModElement> byName, List<String> namesByLengthDesc,
                                        Map<String, List<PackUsages>> packsByFxName) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (!SKIP_DIRS.contains(child.getName()))
                    searchSourceDir(child, byName, namesByLengthDesc, packsByFxName);
            } else {
                String fileName = child.getName();
                if (!fileName.endsWith(".java")) continue;
                String baseName = fileName.substring(0, fileName.length() - 5);
                String elementName = matchOwningElementName(baseName, namesByLengthDesc);
                if (elementName == null) continue;
                ModElement owner = byName.get(elementName);
                if (owner != null) checkFileAndAddUsages(child, owner, packsByFxName);
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

    private static void checkFileAndAddUsages(File file, ModElement me, Map<String, List<PackUsages>> packsByFxName) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Set<String> referencedKeys = extractPhotonFxKeys(content);
            if (referencedKeys.isEmpty()) return;
            for (String key : referencedKeys) {
                List<PackUsages> owners = packsByFxName.get(key);
                if (owners == null) continue;
                for (PackUsages pack : owners) {
                    if (!pack.usedBy().contains(me)) pack.usedBy().add(me);
                }
            }
        } catch (Exception e) {
            MenuLog.warn("Failed to read " + file.getAbsolutePath() + " while searching FX Pack usages: " + e.getMessage());
        }
    }

    private static Set<String> extractPhotonFxKeys(String content) {
        Set<String> keys = new HashSet<>();
        Matcher m = FX_ACTION_PATTERN.matcher(content);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    private static Set<String> readFxNames(File fxPackFile) {
        Set<String> names = new LinkedHashSet<>();
        if (fxPackFile == null || !fxPackFile.isFile()) return names;
        try (ZipFile zip = new ZipFile(fxPackFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                Matcher matcher = FXPACK_FX_ENTRY.matcher(entry.getName().replace('\\', '/'));
                if (matcher.matches()) names.add(matcher.group(1) + ":" + matcher.group(2));
            }
        } catch (IOException e) {
            MenuLog.warn("Failed to read FX Pack '" + fxPackFile.getName() + "' while searching usages: " + e.getMessage());
        }
        return names;
    }

    private static String displayName(File fxPackFile) {
        String fileName = fxPackFile.getName();
        return fileName.endsWith(FXPACK_EXTENSION)
                ? fileName.substring(0, fileName.length() - FXPACK_EXTENSION.length())
                : fileName;
    }

    public record PackUsages(String packName, Set<String> fxNames, List<ModElement> usedBy) {

        static PackUsages of(File fxPackFile) {
            return new PackUsages(displayName(fxPackFile), readFxNames(fxPackFile), new ArrayList<>());
        }
    }
}

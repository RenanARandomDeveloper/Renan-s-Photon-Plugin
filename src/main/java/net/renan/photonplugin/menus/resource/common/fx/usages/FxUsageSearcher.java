package net.renan.photonplugin.menus.resource.common.fx.usages;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.elements.ModElement;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FxUsageSearcher {

    private static final Set<String> SKIP_DIRS = Set.of("photon_plugin");

    private static final Pattern FX_ACTION_PATTERN = Pattern.compile(
            "\\b(?:(?:Summon|Remove|Modify)FX(?:Entity|Block)(?:Server|Client)\\s*\\.\\s*(?:create|destroy|modify)"
                    + "|CheckFX(?:Entity|Block)\\s*\\.\\s*check)"
                    + "\\s*\\([^;{}]*\\)\\s*\\.\\s*name\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"
    );

    private FxUsageSearcher() {
    }

    public static void searchInWorkspace(MCreator mcreator, Map<String, List<ModElement>> usagesMap) {
        Map<String, ModElement> byName = new HashMap<>();
        for (ModElement me : mcreator.getWorkspace().getModElements())
            byName.put(me.getName(), me);

        List<String> namesByLengthDesc = byName.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());

        String modPackage = mcreator.getWorkspace().getWorkspaceSettings().getModElementsPackage();
        File packageDir = new File(mcreator.getWorkspace().getWorkspaceFolder(),
                "src/main/java/" + modPackage.replace('.', '/'));
        searchSourceDir(packageDir, byName, namesByLengthDesc, usagesMap);
    }

    private static void searchSourceDir(File dir, Map<String, ModElement> byName, List<String> namesByLengthDesc,
                                        Map<String, List<ModElement>> usagesMap) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (!SKIP_DIRS.contains(child.getName()))
                    searchSourceDir(child, byName, namesByLengthDesc, usagesMap);
            } else {
                String fileName = child.getName();
                if (!fileName.endsWith(".java")) continue;
                String baseName = fileName.substring(0, fileName.length() - 5);
                String elementName = matchOwningElementName(baseName, namesByLengthDesc);
                if (elementName == null) continue;
                ModElement owner = byName.get(elementName);
                if (owner != null) checkFileAndAddUsages(child, owner, usagesMap);
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

    private static void checkFileAndAddUsages(File file, ModElement me, Map<String, List<ModElement>> usagesMap) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Set<String> referencedKeys = extractPhotonFxKeys(content);
            if (referencedKeys.isEmpty()) return;
            for (Map.Entry<String, List<ModElement>> entry : usagesMap.entrySet()) {
                List<ModElement> found = entry.getValue();
                if (!found.contains(me) && referencedKeys.contains(entry.getKey())) found.add(me);
            }
        } catch (Exception e) {
            MenuLog.warn("Failed to read " + file.getAbsolutePath() + " while searching FX usages: " + e.getMessage());
        }
    }

    private static Set<String> extractPhotonFxKeys(String content) {
        Set<String> keys = new HashSet<>();
        Matcher m = FX_ACTION_PATTERN.matcher(content);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }
}

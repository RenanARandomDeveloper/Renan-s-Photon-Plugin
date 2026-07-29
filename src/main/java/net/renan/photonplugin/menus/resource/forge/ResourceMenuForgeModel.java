package net.renan.photonplugin.menus.resource.forge;

import net.mcreator.io.FileIO;
import net.renan.photonplugin.Log;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;
import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommonModel.*;

public final class ResourceMenuForgeModel {
    private ResourceMenuForgeModel() {}

    public static JPanel createPanel(MCreator mcreator, Supplier<File> objDirGetter, Supplier<File> jsonDirGetter) {
        FXBrowserPanel browser = new FXBrowserPanel(List.of(objDirGetter), OBJ_EXTENSION,
                file -> {try { return scaleIconTo(UIRES.get("64px_photon.obj"), 64); }
                catch (Exception ignored) { return null; }
                }
        );

        browser.setDragAndDropEnabled(true);

        browser.setPostMoveHook((oldFile, newFile) -> {
            File objRoot = objDirGetter.get();
            File jsonRoot = jsonDirGetter.get();
            if (oldFile.isDirectory()) {
                relocateMirroredJsonFolder(objRoot, jsonRoot, oldFile, newFile);
            } else {
                syncCompanionsAfterObjRelocate(objRoot, jsonRoot, oldFile, newFile);
            }
        });

        return assembleModelPanel(browser, objDirGetter,
                buildModelImportButton(mcreator, browser, objDirGetter, jsonDirGetter),
                buildModelCloneButtonForBrowser(mcreator, browser, objDirGetter,
                        (src, destObj) -> cloneModelJson(objDirGetter.get(), jsonDirGetter.get(), src, destObj)),
                buildModelDeleteButtonForBrowser(mcreator, browser, objDirGetter, jsonDirGetter),
                buildExportButtonForBrowser(mcreator, browser),
                buildModelRenameButtonForBrowser(mcreator, browser,
                        (oldFolder, newFolder) -> relocateMirroredJsonFolder(objDirGetter.get(), jsonDirGetter.get(), oldFolder, newFolder),
                        (oldFile, newFile) -> syncCompanionsAfterObjRelocate(objDirGetter.get(), jsonDirGetter.get(), oldFile, newFile)),
                buildModelMoveButtonForBrowser(mcreator, browser, objDirGetter,
                        (oldFolder, newFolder) -> relocateMirroredJsonFolder(objDirGetter.get(), jsonDirGetter.get(), oldFolder, newFolder),
                        (oldFile, newFile) -> syncCompanionsAfterObjRelocate(objDirGetter.get(), jsonDirGetter.get(), oldFile, newFile)),
                buildCreateFolderButtonForBrowser(mcreator, browser),
                buildFilterBarForBrowser(mcreator, browser));
    }

    private static JButton buildModelImportButton(MCreator mcreator, FXBrowserPanel browser,
                                                  Supplier<File> objDirGetter, Supplier<File> jsonDirGetter) {
        JButton button = createToolbarButton(L10N.t("plugin.photon.resourcemenu.import"), "16px_photon.import");
        button.addActionListener(e -> {
            File[] objSources = FileDialogs.getMultiOpenDialog(mcreator, new String[]{OBJ_EXTENSION});
            if (objSources == null || objSources.length == 0) return;

            File navigatedDir = browser.getCurrentDir();
            File objRoot = objDirGetter.get();
            File targetDir = (navigatedDir != null && navigatedDir.isDirectory()) ? navigatedDir : objRoot;
            if (!targetDir.exists()) targetDir.mkdirs();
            File jsonRoot = jsonDirGetter.get();

            boolean strict = requiresStrictNaming(OBJ_EXTENSION);
            int conflictCount = (int) Arrays.stream(objSources)
                    .filter(src -> new File(targetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver overwriteResolver = new BulkConflictResolver(conflictCount, objSources.length >= 2);
            int invalidNameCount = strict
                    ? (int) Arrays.stream(objSources).filter(src -> !isValidStrictFileName(src.getName())).count()
                    : 0;
            InvalidNameResolver nameResolver = new InvalidNameResolver(invalidNameCount);

            List<File> importedObjFiles = new ArrayList<>();
            int[] skippedNoMtl = new int[1];
            int[] skippedNameMismatch = new int[1];
            Exception[] failure = new Exception[1];

            browser.beginInternalMutation();
            runFileTaskInBackground(mcreator, () -> {
                try {
                    for (File objSource : objSources) {
                        String expectedMtlName = FileNameParts.of(objSource).baseName() + MTL_EXTENSION;
                        boolean[] mismatched = new boolean[1];
                        File mtlSource = onEdt(() -> {
                            JOptionPane.showMessageDialog(mcreator,
                                    L10N.t("plugin.photon.resourcemenu.model.import.select.mtl.for", objSource.getName()));
                            while (true) {
                                File[] mtlArr = FileDialogs.getMultiOpenDialog(mcreator, new String[]{MTL_EXTENSION});
                                if (mtlArr == null || mtlArr.length == 0) return null;
                                File candidate = mtlArr[0];
                                if (candidate.getName().equalsIgnoreCase(expectedMtlName)) {
                                    return candidate;
                                }
                                int choice = JOptionPane.showConfirmDialog(mcreator,
                                        L10N.t("plugin.photon.resourcemenu.model.import.mtl.name.mismatch.message",
                                                candidate.getName(), expectedMtlName),
                                        L10N.t("plugin.photon.resourcemenu.model.import.mtl.name.mismatch.title"),
                                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                                if (choice != JOptionPane.YES_OPTION) {
                                    mismatched[0] = true;
                                    return null;
                                }
                            }
                        });
                        if (mtlSource == null) {
                            if (mismatched[0]) skippedNameMismatch[0]++; else skippedNoMtl[0]++;
                            continue;
                        }

                        String fileName = objSource.getName();
                        if (strict) {
                            fileName = nameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }

                        File destObj = new File(targetDir, fileName);
                        ConflictResolution resolution = overwriteResolver.resolve(mcreator, destObj);
                        if (resolution.action() == ACTION_CANCEL) break;
                        if (resolution.action() == ACTION_SKIP) continue;
                        destObj = resolution.targetFile();

                        String baseName = FileNameParts.of(destObj).baseName();
                        File destMtl = new File(targetDir, baseName + MTL_EXTENSION);
                        File destJson = mirrorJsonFileFor(objRoot, jsonRoot, destObj);

                        if (destObj.exists()) destObj.delete();
                        FileIO.copyFile(objSource, destObj);
                        if (destMtl.exists()) destMtl.delete();
                        FileIO.copyFile(mtlSource, destMtl);
                        writeModelJson(destJson, objRoot, destObj);

                        importedObjFiles.add(destObj);
                    }
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            }, () -> {
                try {
                    if (failure[0] != null) {
                        Log.error("Failed to import model files into '" + targetDir.getAbsolutePath() + "'", failure[0]);
                        JOptionPane.showMessageDialog(mcreator,
                                L10N.t("plugin.photon.resourcemenu.import.failed") + failure[0].getMessage(),
                                L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE);
                    } else if (!importedObjFiles.isEmpty()) {
                        browser.refresh();
                        StringBuilder suffix = new StringBuilder();
                        if (skippedNoMtl[0] > 0) {
                            suffix.append(" (").append(skippedNoMtl[0]).append(" ")
                                    .append(L10N.t("plugin.photon.resourcemenu.model.import.skipped.no.mtl")).append(")");
                        }
                        if (skippedNameMismatch[0] > 0) {
                            suffix.append(" (").append(skippedNameMismatch[0]).append(" ")
                                    .append(L10N.t("plugin.photon.resourcemenu.model.import.skipped.mtl.mismatch")).append(")");
                        }
                        JOptionPane.showMessageDialog(mcreator, buildItemLabel(importedObjFiles.size(), 0) + " "
                                + L10N.t("plugin.photon.resourcemenu.action.imported") + " \u2192 \"" + targetDir.getName() + "\""
                                + autoRenameNotice(overwriteResolver) + suffix);
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

    private static void cloneModelJson(File objRoot, File jsonRoot, File src, File destObj) throws IOException {
        File srcJson = mirrorJsonFileFor(objRoot, jsonRoot, src);
        File destJson = mirrorJsonFileFor(objRoot, jsonRoot, destObj);
        File destJsonParent = destJson.getParentFile();
        if (destJsonParent != null && !destJsonParent.exists()) destJsonParent.mkdirs();
        if (srcJson.exists()) {
            if (destJson.exists()) destJson.delete();
            FileIO.copyFile(srcJson, destJson);
            updateModelReference(destJson, objRoot, destObj);
        } else {
            writeModelJson(destJson, objRoot, destObj);
        }
    }

    private static void syncCompanionsAfterObjRelocate(File objRoot, File jsonRoot, File oldObjFile, File newObjFile) {
        File oldMtl = mtlFileFor(oldObjFile);
        File newMtl = mtlFileFor(newObjFile);
        if (oldMtl.exists() && !renameFile(oldMtl, newMtl)) {
            Log.warn("Could not relocate companion .mtl file: " + oldMtl.getAbsolutePath());
        }

        File oldJson = mirrorJsonFileFor(objRoot, jsonRoot, oldObjFile);
        File newJson = mirrorJsonFileFor(objRoot, jsonRoot, newObjFile);
        try {
            File newJsonParent = newJson.getParentFile();
            if (newJsonParent != null && !newJsonParent.exists() && !newJsonParent.mkdirs()) {
                Log.warn("Could not create mirrored json directory: " + newJsonParent.getAbsolutePath());
            }
            if (oldJson.exists()) {
                if (renameFile(oldJson, newJson)) {
                    updateModelReference(newJson, objRoot, newObjFile);
                } else {
                    Log.warn("Could not relocate companion .json file: " + oldJson.getAbsolutePath());
                }
            } else {
                writeModelJson(newJson, objRoot, newObjFile);
            }
        } catch (IOException ex) {
            Log.error("Failed to sync model json for '" + newObjFile.getAbsolutePath() + "'", ex);
        }
    }

    private static void relocateMirroredJsonFolder(File objRoot, File jsonRoot, File oldObjFolder, File newObjFolder) {
        File oldJsonFolder = mirrorJsonFolderFor(objRoot, jsonRoot, oldObjFolder);
        File newJsonFolder = mirrorJsonFolderFor(objRoot, jsonRoot, newObjFolder);
        if (!oldJsonFolder.exists()) return;
        File parent = newJsonFolder.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!renameFile(oldJsonFolder, newJsonFolder)) {
            Log.warn("Could not relocate mirrored json folder: " + oldJsonFolder.getAbsolutePath());
            return;
        }

        fixModelReferencesRecursively(objRoot, jsonRoot, newObjFolder);
    }

    private static void fixModelReferencesRecursively(File objRoot, File jsonRoot, File objFolder) {
        File[] children = objFolder.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                fixModelReferencesRecursively(objRoot, jsonRoot, child);
            } else if (child.getName().toLowerCase().endsWith(OBJ_EXTENSION)) {
                File json = mirrorJsonFileFor(objRoot, jsonRoot, child);
                if (json.exists()) {
                    try {
                        updateModelReference(json, objRoot, child);
                    } catch (IOException ex) {
                        Log.error("Failed to update model reference for '" + child.getAbsolutePath() + "'", ex);
                    }
                }
            }
        }
    }

    private static String modelResourcePath(File objRoot, File objFile) {
        String relDir = relativeSubPath(objRoot, objFile.getParentFile());
        String baseName = FileNameParts.of(objFile).baseName();
        if (relDir.isEmpty()) {
            return "ldlib:models/obj/" + baseName + ".obj";
        }
        String relDirSlashes = relDir.replace(File.separatorChar, '/');
        return "ldlib:models/obj/" + relDirSlashes + "/" + baseName + ".obj";
    }

    private static void writeModelJson(File jsonFile, File objRoot, File objFile) throws IOException {
        File parent = jsonFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent.getAbsolutePath());
        }
        String content = "{\n"
                + "    \"loader\": \"forge:obj\",\n"
                + "    \"model\": \"" + modelResourcePath(objRoot, objFile) + "\",\n"
                + "    \"textures\": {\n"
                + "    },\n"
                + "    \"flip_v\": true\n"
                + "}\n";
        Files.write(jsonFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateModelReference(File jsonFile, File objRoot, File objFile) throws IOException {
        String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
        String newPath = modelResourcePath(objRoot, objFile);
        String updated = content.replaceFirst(
                "(\"model\"\\s*:\\s*\")[^\"]*(\")",
                "$1" + java.util.regex.Matcher.quoteReplacement(newPath) + "$2");
        if (updated.equals(content) && !content.contains("\"model\"")) {
            writeModelJson(jsonFile, objRoot, objFile);
            return;
        }
        Files.write(jsonFile.toPath(), updated.getBytes(StandardCharsets.UTF_8));
    }
}
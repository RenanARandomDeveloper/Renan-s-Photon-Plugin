package net.renan.photonplugin.menus.resource.neo;

import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.file.ExtensionFilter;
import net.mcreator.ui.dialogs.file.FileChooserType;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.Log;
import net.renan.photonplugin.menus.resource.common.ResourceMenuCommonModel;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;
import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommonModel.*;

public final class ResourceMenuNeoModel {
    private ResourceMenuNeoModel() {}

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> modelsDirGetter) {
        return createFXPanel(mcreator, modelsDirGetter, modelsDirGetter, true);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> objDirGetter, Supplier<File> jsonDirGetter) {
        return createFXPanel(mcreator, objDirGetter, jsonDirGetter, true);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> objDirGetter, Supplier<File> jsonDirGetter,
                                       boolean dragAndDropMoveEnabled) {
        Supplier<File> modelsDirGetter = normalizeToModelsRoot(objDirGetter);

        FXBrowserPanel browser = new FXBrowserPanel(List.of(modelsDirGetter), OBJ_EXTENSION,
                Set.of(OBJ_EXTENSION, JSON_EXTENSION),
                file -> {
                    try {
                        if (file.getName().toLowerCase().endsWith(JSON_EXTENSION)) {
                            try { return scaleIconTo(UIRES.get("64px_photon.json"), 64); }
                            catch (Exception ignored) { /* fall through to the default model icon */ }
                        }
                        return scaleIconTo(UIRES.get("64px_photon.obj"), 64);
                    } catch (Exception ignored) { return null; }
                }
        );

        browser.setDragAndDropEnabled(dragAndDropMoveEnabled);

        browser.setPostMoveHook((oldFile, newFile) -> {
            File modelsRoot = modelsDirGetter.get();
            if (oldFile.isDirectory()) {
                relocateMirroredJsonFolder(modelsRoot, oldFile, newFile);
            } else {
                syncCompanionsAfterObjRelocate(modelsRoot, oldFile, newFile);
            }
        });

        return assembleModelPanel(browser, modelsDirGetter,
                buildModelImportButton(mcreator, browser, modelsDirGetter),
                buildModelCloneButtonForBrowser(mcreator, browser, modelsDirGetter,
                        (src, destObj) -> cloneModelJson(modelsDirGetter.get(), src, destObj)),
                buildModelDeleteButtonForBrowser(mcreator, browser, modelsDirGetter, modelsDirGetter),
                buildExportButtonForBrowser(mcreator, browser),
                buildModelRenameButtonForBrowser(mcreator, browser,
                        (oldFolder, newFolder) -> relocateMirroredJsonFolder(modelsDirGetter.get(), oldFolder, newFolder),
                        (oldFile, newFile) -> syncCompanionsAfterObjRelocate(modelsDirGetter.get(), oldFile, newFile)),
                buildModelMoveButtonForBrowser(mcreator, browser, modelsDirGetter,
                        (oldFolder, newFolder) -> relocateMirroredJsonFolder(modelsDirGetter.get(), oldFolder, newFolder),
                        (oldFile, newFile) -> syncCompanionsAfterObjRelocate(modelsDirGetter.get(), oldFile, newFile)),
                buildCreateFolderButtonForBrowser(mcreator, browser),
                buildFilterBarForBrowser(mcreator, browser));
    }

    private static Supplier<File> normalizeToModelsRoot(Supplier<File> dirGetter) {
        return () -> {
            File dir = dirGetter.get();
            if (dir == null) return null;
            String name = dir.getName();
            if (("obj".equalsIgnoreCase(name) || "json".equalsIgnoreCase(name)) && dir.getParentFile() != null) {
                return dir.getParentFile();
            }
            return dir;
        };
    }

    private static File[] showModelImportDialog(MCreator mcreator) {
        File[] selected = FileDialogs.getFileChooserDialog(mcreator, FileChooserType.OPEN, true, null,
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.model.import.filter.obj"), "*" + OBJ_EXTENSION),
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.model.import.filter.json"), "*" + JSON_EXTENSION),
                new ExtensionFilter(L10N.t("plugin.photon.resourcemenu.model.import.filter.both"),
                        "*" + OBJ_EXTENSION, "*" + JSON_EXTENSION));
        return selected != null ? selected : new File[0];
    }

    private static JButton buildModelImportButton(MCreator mcreator, FXBrowserPanel browser,
                                                  Supplier<File> modelsDirGetter) {
        JButton button = createToolbarButton(L10N.t("plugin.photon.resourcemenu.import"), "16px_photon.import");
        button.addActionListener(e -> {
            File[] selectedSources = showModelImportDialog(mcreator);
            if (selectedSources == null || selectedSources.length == 0) return;

            List<File> objSources = new ArrayList<>();
            List<File> jsonSources = new ArrayList<>();
            for (File src : selectedSources) {
                if (src.getName().toLowerCase().endsWith(JSON_EXTENSION)) {
                    jsonSources.add(src);
                } else {
                    objSources.add(src);
                }
            }
            if (objSources.isEmpty() && jsonSources.isEmpty()) return;

            File navigatedDir = browser.getCurrentDir();
            File modelsRoot = modelsDirGetter.get();
            File targetDir = (navigatedDir != null && navigatedDir.isDirectory()) ? navigatedDir : modelsRoot;
            if (!targetDir.exists()) targetDir.mkdirs();
            File jsonTargetDir = mirrorJsonFolderFor(modelsRoot, targetDir);

            boolean strictObj = requiresStrictNaming(OBJ_EXTENSION);
            int objConflictCount = (int) objSources.stream()
                    .filter(src -> new File(targetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver objOverwriteResolver = new BulkConflictResolver(objConflictCount, objSources.size() >= 2);
            int objInvalidNameCount = strictObj
                    ? (int) objSources.stream().filter(src -> !isValidStrictFileName(src.getName())).count()
                    : 0;
            InvalidNameResolver objNameResolver = new InvalidNameResolver(objInvalidNameCount);

            boolean strictJson = requiresStrictNaming(JSON_EXTENSION);
            int jsonConflictCount = (int) jsonSources.stream()
                    .filter(src -> new File(jsonTargetDir, src.getName()).exists())
                    .count();
            BulkConflictResolver jsonOverwriteResolver = new BulkConflictResolver(jsonConflictCount, jsonSources.size() >= 2);
            int jsonInvalidNameCount = strictJson
                    ? (int) jsonSources.stream().filter(src -> !isValidStrictFileName(src.getName())).count()
                    : 0;
            InvalidNameResolver jsonNameResolver = new InvalidNameResolver(jsonInvalidNameCount);

            List<File> importedObjFiles = new ArrayList<>();
            List<File> importedJsonFiles = new ArrayList<>();
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
                        if (strictObj) {
                            fileName = objNameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }

                        File destObj = new File(targetDir, fileName);
                        ConflictResolution resolution = objOverwriteResolver.resolve(mcreator, destObj);
                        if (resolution.action() == ACTION_CANCEL) break;
                        if (resolution.action() == ACTION_SKIP) continue;
                        destObj = resolution.targetFile();

                        String baseName = FileNameParts.of(destObj).baseName();
                        File destMtl = new File(targetDir, baseName + MTL_EXTENSION);

                        if (destObj.exists()) destObj.delete();
                        FileIO.copyFile(objSource, destObj);
                        if (destMtl.exists()) destMtl.delete();
                        FileIO.copyFile(mtlSource, destMtl);

                        importedObjFiles.add(destObj);
                    }

                    if (!jsonSources.isEmpty() && !jsonTargetDir.exists() && !jsonTargetDir.mkdirs()) {
                        throw new IOException("Cannot create directory: " + jsonTargetDir.getAbsolutePath());
                    }
                    for (File jsonSource : jsonSources) {
                        String fileName = jsonSource.getName();
                        if (strictJson) {
                            fileName = jsonNameResolver.resolve(mcreator, fileName);
                            if (fileName == null) continue;
                        }

                        File destJson = new File(jsonTargetDir, fileName);
                        ConflictResolution resolution = jsonOverwriteResolver.resolve(mcreator, destJson);
                        if (resolution.action() == ACTION_CANCEL) break;
                        if (resolution.action() == ACTION_SKIP) continue;
                        destJson = resolution.targetFile();

                        if (destJson.exists()) destJson.delete();
                        FileIO.copyFile(jsonSource, destJson);

                        importedJsonFiles.add(destJson);
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
                    } else if (!importedObjFiles.isEmpty() || !importedJsonFiles.isEmpty()) {
                        browser.refresh();
                        StringBuilder message = new StringBuilder();
                        if (!importedObjFiles.isEmpty()) {
                            message.append(buildItemLabel(importedObjFiles.size(), 0)).append(" ")
                                    .append(L10N.t("plugin.photon.resourcemenu.action.imported"))
                                    .append(" \u2192 \"").append(targetDir.getName()).append("\"")
                                    .append(autoRenameNotice(objOverwriteResolver));
                        }
                        if (!importedJsonFiles.isEmpty()) {
                            if (message.length() > 0) message.append("\n");
                            message.append(L10N.t("plugin.photon.resourcemenu.model.import.json.imported",
                                            importedJsonFiles.size(), jsonTargetDir.getName()))
                                    .append(autoRenameNotice(jsonOverwriteResolver));
                        }
                        if (skippedNoMtl[0] > 0) {
                            message.append(" (").append(skippedNoMtl[0]).append(" ")
                                    .append(L10N.t("plugin.photon.resourcemenu.model.import.skipped.no.mtl")).append(")");
                        }
                        if (skippedNameMismatch[0] > 0) {
                            message.append(" (").append(skippedNameMismatch[0]).append(" ")
                                    .append(L10N.t("plugin.photon.resourcemenu.model.import.skipped.mtl.mismatch"))
                                    .append(")");
                        }
                        JOptionPane.showMessageDialog(mcreator, message.toString());
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

    private static void cloneModelJson(File modelsRoot, File src, File destObj) throws IOException {
        File srcJson = mirrorJsonFileFor(modelsRoot, src);
        if (srcJson.exists()) {
            File destJson = mirrorJsonFileFor(modelsRoot, destObj);
            File destJsonParent = destJson.getParentFile();
            if (destJsonParent != null && !destJsonParent.exists()) destJsonParent.mkdirs();
            if (destJson.exists()) destJson.delete();
            FileIO.copyFile(srcJson, destJson);
        }
    }

    private static void syncCompanionsAfterObjRelocate(File modelsRoot, File oldObjFile, File newObjFile) {
        File oldMtl = mtlFileFor(oldObjFile);
        File newMtl = mtlFileFor(newObjFile);
        if (oldMtl.exists() && !renameFile(oldMtl, newMtl)) {
            Log.warn("Could not relocate companion .mtl file: " + oldMtl.getAbsolutePath());
        }

        File oldJson = mirrorJsonFileFor(modelsRoot, oldObjFile);
        if (!oldJson.exists()) return;

        File newJson = mirrorJsonFileFor(modelsRoot, newObjFile);
        File newJsonParent = newJson.getParentFile();
        if (newJsonParent != null && !newJsonParent.exists() && !newJsonParent.mkdirs()) {
            Log.warn("Could not create mirrored json directory: " + newJsonParent.getAbsolutePath());
        }
        if (!renameFile(oldJson, newJson)) {
            Log.warn("Could not relocate companion .json file: " + oldJson.getAbsolutePath());
        }
    }

    private static void relocateMirroredJsonFolder(File modelsRoot, File oldObjFolder, File newObjFolder) {
        File oldJsonFolder = mirrorJsonFolderFor(modelsRoot, oldObjFolder);
        File newJsonFolder = mirrorJsonFolderFor(modelsRoot, newObjFolder);
        if (!oldJsonFolder.exists()) return;
        File parent = newJsonFolder.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!renameFile(oldJsonFolder, newJsonFolder)) {
            Log.warn("Could not relocate mirrored json folder: " + oldJsonFolder.getAbsolutePath());
        }
    }

    private static File mirrorJsonFileFor(File modelsRoot, File objFile) {
        return ResourceMenuCommonModel.mirrorJsonFileFor(modelsRoot, modelsRoot, objFile);
    }

    private static File mirrorJsonFolderFor(File modelsRoot, File objFolder) {
        return ResourceMenuCommonModel.mirrorJsonFolderFor(modelsRoot, modelsRoot, objFolder);
    }
}
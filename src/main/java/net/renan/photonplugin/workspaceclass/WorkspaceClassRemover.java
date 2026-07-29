package net.renan.photonplugin.workspaceclass;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.renan.photonplugin.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceClassRemover {

    private final MCreator mcreator;

    public WorkspaceClassRemover(MCreator mcreator) {
        this.mcreator = mcreator;
    }

    public static RemovalResult remove(MCreator mcreator) {
        return new WorkspaceClassRemover(mcreator).remove();
    }

    public RemovalResult remove() {
        RemovalResult result = new RemovalResult();

        if (this.mcreator == null) {
            Log.error("No MCreator instance provided, aborting.");
            result.addError("No MCreator workspace window provided.");
            return result;
        }

        Workspace workspace = this.mcreator.getWorkspace();
        if (workspace == null) {
            Log.error("No workspace is currently open, aborting.");
            result.addError("No workspace is currently open.");
            return result;
        }

        WorkspaceSettings settings = workspace.getWorkspaceSettings();
        if (settings == null) {
            Log.error("Workspace settings are unavailable, aborting.");
            result.addError("Workspace settings are unavailable.");
            return result;
        }

        String basePackage = settings.getModElementsPackage();

        if (basePackage == null || basePackage.isBlank()) {
            Log.error(String.format(
                    "Workspace settings are incomplete (basePackage='%s').", basePackage));
            result.addError("Workspace settings are incomplete (missing base package).");
            return result;
        }

        Path workspaceFolder = workspace.getWorkspaceFolder().toPath().normalize();
        Path targetPackageDir = workspaceFolder
                .resolve("src/main/java")
                .resolve(basePackage.replace('.', '/'))
                .resolve(WorkspaceConstants.TARGET_SUB_PACKAGE)
                .normalize();

        if (!targetPackageDir.startsWith(workspaceFolder)) {
            Log.error("Resolved target package directory '" + targetPackageDir + "' escapes the workspace folder.");
            result.addError("Invalid base package: resolved path escapes the workspace folder.");
            return result;
        }

        Log.info("Removing photon_plugin generated classes from: %s", targetPackageDir);

        if (Files.notExists(targetPackageDir)) {
            Log.info("Package '%s' does not exist, nothing to remove.", targetPackageDir);
            for (String baseClassName : WorkspaceConstants.getBaseClassNames()) {
                result.addNotFound(WorkspaceConstants.getJavaFileName(baseClassName));
            }
            return result;
        }

        for (String baseClassName : WorkspaceConstants.getBaseClassNames()) {
            String fileName = WorkspaceConstants.getJavaFileName(baseClassName);
            deleteGeneratedFile(targetPackageDir, fileName, result);
        }

        deleteDirectoryIfEmpty(targetPackageDir, result);

        Log.info("Done. Deleted: %d, not found: %d, errors: %d", result.getDeletedCount(),
                result.getNotFoundCount(), result.getErrors().size());

        return result;
    }

    private void deleteGeneratedFile(Path targetPackageDir, String fileName, RemovalResult result) {
        Path file = targetPackageDir.resolve(fileName);

        if (Files.notExists(file)) {
            Log.info("'%s' was not found, nothing to delete.", file);
            result.addNotFound(fileName);
            return;
        }

        try {
            Files.delete(file);
            Log.info("Deleted '%s'.", file);
            result.addDeleted(fileName);
        } catch (IOException e) {
            Log.error(String.format("Failed to delete '%s': %s", file, e.getMessage()), e);
            result.addError("Failed to delete '" + fileName + "': " + e.getMessage());
        }
    }

    private void deleteDirectoryIfEmpty(Path targetPackageDir, RemovalResult result) {
        boolean isEmpty;

        try (var entries = Files.list(targetPackageDir)) {
            isEmpty = !entries.findFirst().isPresent();
        } catch (IOException e) {
            Log.warn("Could not list contents of '%s'.", targetPackageDir);
            return;
        }

        if (!isEmpty) {
            Log.info("Keeping '%s' directory because it still contains other item(s).", targetPackageDir);
            return;
        }

        try {
            Files.delete(targetPackageDir);
            Log.info("Removed now-empty directory '%s'.", targetPackageDir);
        } catch (IOException e) {
            Log.warn("Could not remove empty directory '%s': %s", targetPackageDir, e.getMessage());
            result.addError("Could not remove empty directory '" + targetPackageDir + "'.");
        }
    }

    public static final class RemovalResult {
        private final List<String> deletedFiles = new ArrayList<>();
        private final List<String> notFoundFiles = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        void addDeleted(String fileName) {
            this.deletedFiles.add(fileName);
        }

        void addNotFound(String fileName) {
            this.notFoundFiles.add(fileName);
        }

        void addError(String message) {
            this.errors.add(message);
        }

        public int getDeletedCount() {
            return this.deletedFiles.size();
        }

        public int getNotFoundCount() {
            return this.notFoundFiles.size();
        }

        public List<String> getDeletedFiles() {
            return this.deletedFiles;
        }

        public List<String> getNotFoundFiles() {
            return this.notFoundFiles;
        }

        public List<String> getErrors() {
            return this.errors;
        }

        public boolean hasErrors() {
            return !this.errors.isEmpty();
        }
    }
}
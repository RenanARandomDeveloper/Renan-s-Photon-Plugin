package net.renan.photonplugin.workspaceclass;

import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class WorkspacePaths {

    private WorkspacePaths() {
    }

    public static Path resolveTargetPackageDir(Workspace workspace, WorkspaceSettings settings)
            throws InvalidWorkspacePathException {
        if (workspace == null || settings == null) {
            return null;
        }

        String basePackage = settings.getModElementsPackage();
        if (basePackage == null || basePackage.isBlank()) {
            return null;
        }

        File workspaceFolderFile = workspace.getWorkspaceFolder();
        if (workspaceFolderFile == null) {
            return null;
        }

        Path workspaceFolder = realOrNormalizedPath(workspaceFolderFile);
        Path targetPackageDir = workspaceFolder.resolve("src/main/java")
                .resolve(basePackage.replace('.', '/'))
                .resolve(WorkspaceConstants.TARGET_SUB_PACKAGE)
                .normalize();

        if (!targetPackageDir.startsWith(workspaceFolder)) {
            throw new InvalidWorkspacePathException(
                    "Invalid base package: resolved output path '" + targetPackageDir
                            + "' escapes the workspace folder '" + workspaceFolder + "'.");
        }

        return targetPackageDir;
    }

    private static Path realOrNormalizedPath(File folder) {
        Path path = folder.toPath();
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.normalize();
        }
    }

    public static String sessionKey(MCreator mcreator) {
        if (mcreator == null) {
            return null;
        }

        File workspaceFolder = mcreator.getWorkspaceFolder();
        if (workspaceFolder == null) {
            return null;
        }

        try {
            return workspaceFolder.getCanonicalPath();
        } catch (IOException e) {
            return workspaceFolder.getAbsolutePath();
        }
    }

    public static final class InvalidWorkspacePathException extends Exception {
        private static final long serialVersionUID = 1L;

        public InvalidWorkspacePathException(String message) {
            super(message);
        }
    }
}
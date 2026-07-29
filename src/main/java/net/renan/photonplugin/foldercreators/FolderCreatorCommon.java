package net.renan.photonplugin.foldercreators;

import net.renan.photonplugin.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class FolderCreatorCommon {
    protected abstract String getBaseRelativePath();
    protected abstract String[] getFolderStructure();

    protected void buildStructure(File workspaceRoot) {
        if (workspaceRoot == null) {
            Log.error("Workspace root cannot be null.");
            return;
        }

        Path basePath = workspaceRoot.toPath().resolve(getBaseRelativePath());

        for (String relativePath : getFolderStructure()) {
            Path target = basePath.resolve(relativePath);

            if (Files.exists(target)) {
                if (!Files.isDirectory(target)) {
                    Log.warn("Path already exists but is not a directory, skipping: %s", target);
                } else {
                    Log.info("Directory already exists, skipping: %s", target);
                }
                continue;
            }

            try {
                Files.createDirectories(target);
                Log.info("Created: %s", target);
            } catch (IOException e) {
                Log.error(String.format("Failed to create directory '%s'", target), e);
            }
        }
    }
}
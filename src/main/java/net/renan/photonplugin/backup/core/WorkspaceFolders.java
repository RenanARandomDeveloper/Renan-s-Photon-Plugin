package net.renan.photonplugin.backup.core;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.log.Log;

import java.io.File;

public final class WorkspaceFolders {
    private WorkspaceFolders() {
    }

    public static File resolve(MCreator mcreator) {
        try {
            return mcreator.getFolderManager().getWorkspaceFolder();
        } catch (Exception e) {
            Log.error("Failed to resolve workspace folder.", e);
            return null;
        }
    }
}

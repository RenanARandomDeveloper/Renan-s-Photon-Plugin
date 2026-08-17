package net.renan.photonplugin.backup;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.backup.core.BackupModuleSupport;

public final class BackupForge {
    private BackupForge() {
    }

    public static void setup(MCreator mcreator) {
        BackupModuleSupport.setup(mcreator, "ldlib");
    }

    public static void shutdown(MCreator mcreator) {
        BackupModuleSupport.shutdown(mcreator);
    }
}

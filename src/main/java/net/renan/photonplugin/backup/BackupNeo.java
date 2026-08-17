package net.renan.photonplugin.backup;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.backup.core.BackupModuleSupport;

public final class BackupNeo {
    private BackupNeo() {
    }

    public static void setup(MCreator mcreator) {
        BackupModuleSupport.setup(mcreator, "ldlib2");
    }

    public static void shutdown(MCreator mcreator) {
        BackupModuleSupport.shutdown(mcreator);
    }
}

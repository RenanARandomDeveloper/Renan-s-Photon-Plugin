package net.renan.photonplugin.menus.resource.core.conflict;

import java.io.File;

public record ConflictResolution(int action, File targetFile) {

    public static final int ACTION_PROCEED = 0;
    public static final int ACTION_SKIP = 1;
    public static final int ACTION_CANCEL = 2;
    static final int ACTION_RENAME_ALL = 3;

    static ConflictResolution proceed(File file) {
        return new ConflictResolution(ACTION_PROCEED, file);
    }

    static ConflictResolution skip() {
        return new ConflictResolution(ACTION_SKIP, null);
    }

    static ConflictResolution cancel() {
        return new ConflictResolution(ACTION_CANCEL, null);
    }
}

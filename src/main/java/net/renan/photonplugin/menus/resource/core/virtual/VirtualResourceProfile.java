package net.renan.photonplugin.menus.resource.core.virtual;

import java.io.File;
import java.util.List;

public interface VirtualResourceProfile {

    String storeFileName();

    String defaultRootName();

    String resourceLabel();

    void entriesAdded(File workspaceFolder, List<File> files);

    void entriesRemoved(File workspaceFolder, List<File> files);

    void entryRenamed(File workspaceFolder, File oldFile, File newFile);
}

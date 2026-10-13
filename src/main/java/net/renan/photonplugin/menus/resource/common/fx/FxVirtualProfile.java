package net.renan.photonplugin.menus.resource.common.fx;

import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualResourceProfile;

import java.io.File;
import java.util.List;

public final class FxVirtualProfile implements VirtualResourceProfile {

    public static final FxVirtualProfile INSTANCE = new FxVirtualProfile();

    private FxVirtualProfile() {
    }

    @Override
    public String storeFileName() {
        return "fx_virtual_folders.json";
    }

    @Override
    public String defaultRootName() {
        return "fx";
    }

    @Override
    public String resourceLabel() {
        return "FX";
    }

    @Override
    public void entriesAdded(File workspaceFolder, List<File> files) {
        FXListManager.addEntries(workspaceFolder, files);
    }

    @Override
    public void entriesRemoved(File workspaceFolder, List<File> files) {
        FXListManager.removeEntries(workspaceFolder, files);
    }

    @Override
    public void entryRenamed(File workspaceFolder, File oldFile, File newFile) {
        FXListManager.renameEntry(workspaceFolder, oldFile, newFile);
    }
}

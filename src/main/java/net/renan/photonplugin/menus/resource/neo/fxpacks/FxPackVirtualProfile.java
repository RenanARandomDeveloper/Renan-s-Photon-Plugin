package net.renan.photonplugin.menus.resource.neo.fxpacks;

import net.renan.photonplugin.blockly.FXListManager;
import net.renan.photonplugin.menus.resource.core.virtual.VirtualResourceProfile;

import java.io.File;
import java.util.List;

public final class FxPackVirtualProfile implements VirtualResourceProfile {

    public static final FxPackVirtualProfile INSTANCE = new FxPackVirtualProfile();

    private FxPackVirtualProfile() {
    }

    @Override
    public String storeFileName() {
        return "fxpack_virtual_folders.json";
    }

    @Override
    public String defaultRootName() {
        return "fxpack";
    }

    @Override
    public String resourceLabel() {
        return "FX Pack";
    }

    @Override
    public void entriesAdded(File workspaceFolder, List<File> files) {
        FXListManager.addFxPackEntries(workspaceFolder, files);
    }

    @Override
    public void entriesRemoved(File workspaceFolder, List<File> files) {
        FXListManager.removeFxPackEntries(workspaceFolder, files);
    }

    @Override
    public void entryRenamed(File workspaceFolder, File oldFile, File newFile) {
        FXListManager.renameFxPackEntry(workspaceFolder, oldFile, newFile);
    }
}

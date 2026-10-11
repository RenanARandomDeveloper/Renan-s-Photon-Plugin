package net.renan.photonplugin.menus.resource.common.fx;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;

import javax.swing.*;
import java.io.File;
import java.util.function.Supplier;

public final class ResourceMenuFX {

    private ResourceMenuFX() {
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {
        return FxResourcePanelFactory.build(mcreator, targetDirGetter);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter, boolean enablePreview) {
        return FxResourcePanelFactory.build(mcreator, targetDirGetter, enablePreview);
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter, boolean enablePreview,
                                       PreviewMode mode) {
        return FxResourcePanelFactory.build(mcreator, targetDirGetter, enablePreview, mode);
    }
}
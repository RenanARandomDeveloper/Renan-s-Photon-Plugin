package net.renan.photonplugin.menus.resource.common.fx;

import net.mcreator.ui.MCreator;

import javax.swing.*;
import java.io.File;
import java.util.function.Supplier;

public final class ResourceMenuFX {

    private ResourceMenuFX() {
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {
        return FxResourcePanelFactory.build(mcreator, targetDirGetter);
    }
}

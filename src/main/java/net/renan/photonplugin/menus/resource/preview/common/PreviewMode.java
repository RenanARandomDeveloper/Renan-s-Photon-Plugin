package net.renan.photonplugin.menus.resource.preview.common;

import net.mcreator.ui.init.L10N;

public enum PreviewMode {
    NEOFORGE("plugin.photon.resourcemenu.fx.preview.mode.neoforge"),

    FORGE("plugin.photon.resourcemenu.fx.preview.mode.forge");

    private final String nameKey;

    PreviewMode(String nameKey) {
        this.nameKey = nameKey;
    }

    public String displayName() {
        return L10N.t(nameKey);
    }
}

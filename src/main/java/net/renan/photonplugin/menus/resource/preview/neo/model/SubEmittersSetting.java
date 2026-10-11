package net.renan.photonplugin.menus.resource.preview.neo.model;

import java.util.List;

public record SubEmittersSetting(boolean enable, List<SubEmitterDef> emitters) {
    public static final SubEmittersSetting DISABLED = new SubEmittersSetting(false, List.of());
}

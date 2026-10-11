package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;

public record MaterialLayer(IMaterial material, BlendMode blendMode, boolean depthTest, boolean cull) {
}

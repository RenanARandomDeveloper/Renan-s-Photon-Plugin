package net.renan.photonplugin.menus.resource.preview.common.model;

public record UnsupportedMaterial(String kind, String fallbackTexture, String graphPath,
                                  RenderStateHint renderState) implements IMaterial {
    public UnsupportedMaterial(String kind, String fallbackTexture, String graphPath) {
        this(kind, fallbackTexture, graphPath, null);
    }

    public UnsupportedMaterial(String kind, String fallbackTexture) {
        this(kind, fallbackTexture, null, null);
    }
}

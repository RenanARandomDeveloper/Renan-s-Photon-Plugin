package net.renan.photonplugin.menus.resource.preview.common.model;

public record TextureMaterial(String texture, float[] hdrColor, boolean hdrAdditive, float discardThreshold,
                              int pixelArtBits, float litParticles) implements IMaterial {
    public TextureMaterial(String texture, float[] hdrColor, boolean hdrAdditive, float discardThreshold,
                           int pixelArtBits) {
        this(texture, hdrColor, hdrAdditive, discardThreshold, pixelArtBits, 0f);
    }

    public TextureMaterial(String texture, float[] hdrColor, boolean hdrAdditive, float discardThreshold,
                           boolean pixelArt) {
        this(texture, hdrColor, hdrAdditive, discardThreshold, pixelArt ? 8 : 0, 0f);
    }

    public boolean pixelArt() {
        return pixelArtBits > 0;
    }

    public boolean lit() {
        return litParticles > 0f;
    }
}

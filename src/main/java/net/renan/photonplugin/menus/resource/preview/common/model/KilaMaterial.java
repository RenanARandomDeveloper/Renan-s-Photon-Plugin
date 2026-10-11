package net.renan.photonplugin.menus.resource.preview.common.model;

import java.util.List;

public record KilaMaterial(String texture, int colorMode, float[] tint, float[] emission, float intensity,
                           float alpha, float alphaSharpen, float alphaClip, String blend, float additive,
                           boolean slotRenderState, boolean doubleSided, boolean depthTest, boolean projection,
                           float litIntensity, int flipColumns, int flipRows, int flipStart,
                           List<String> unsupportedModules) implements IMaterial {
    public RenderStateHint renderState() {
        if (slotRenderState) {
            return null;
        }
        return new RenderStateHint(RenderStateHint.blendOf(blend, additive), !doubleSided, depthTest && !projection);
    }
}

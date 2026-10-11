package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.ConstantFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.List;

public final class TrailConfig implements FxObjectData {
    public int duration = 100;
    public boolean looping = true;
    public int startDelay;
    public int time = 20;
    public float minVertexDistance = 0.05f;

    public boolean smoothInterpolation;

    public UvMode uvMode = UvMode.STRETCH;
    public NumberFunction widthOverTrail = new ConstantFunction(0.2);
    public NumberFunction colorOverTrail = new ConstantFunction(-1);

    public UvAnimation uvAnimation;
    public List<MaterialLayer> materials = List.of();
    public BlendMode blendMode;
    public RenderSettings renderSettings = RenderSettings.DEFAULT;

    public TrailConfig copy() {
        TrailConfig c = new TrailConfig();
        c.duration = duration;
        c.looping = looping;
        c.startDelay = startDelay;
        c.time = time;
        c.minVertexDistance = minVertexDistance;
        c.smoothInterpolation = smoothInterpolation;
        c.uvMode = uvMode;
        c.widthOverTrail = widthOverTrail;
        c.colorOverTrail = colorOverTrail;
        c.uvAnimation = uvAnimation;
        c.materials = materials;
        c.blendMode = blendMode;
        c.renderSettings = renderSettings;
        return c;
    }

    public enum UvMode {STRETCH, TILE}
}

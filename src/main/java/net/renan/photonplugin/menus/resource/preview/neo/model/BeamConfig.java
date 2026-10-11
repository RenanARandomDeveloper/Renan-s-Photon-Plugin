package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.List;

public final class BeamConfig implements FxObjectData {
    public int duration;
    public boolean looping;
    public Vec3 end;
    public NumberFunction width;
    public NumberFunction color;

    public NumberFunction emitRate;
    public UvAnimation uvAnimation;
    public int startDelay;
    public List<MaterialLayer> materials;
    public BlendMode blendMode;
    public RenderSettings renderSettings = RenderSettings.DEFAULT;

    public BeamConfig copy() {
        BeamConfig c = new BeamConfig();
        c.duration = duration;
        c.looping = looping;
        c.end = end;
        c.width = width;
        c.color = color;
        c.emitRate = emitRate;
        c.uvAnimation = uvAnimation;
        c.startDelay = startDelay;
        c.materials = materials;
        c.blendMode = blendMode;
        c.renderSettings = renderSettings;
        return c;
    }
}

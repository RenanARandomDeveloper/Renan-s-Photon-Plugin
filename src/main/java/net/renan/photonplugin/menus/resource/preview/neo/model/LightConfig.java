package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.ConstantFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

public final class LightConfig implements FxObjectData {
    public Type type = Type.POINT;
    public int lifetime = 20;
    public boolean looping = true;
    public NumberFunction color = new ConstantFunction(-1);
    public NumberFunction intensity = new ConstantFunction(10);
    public NumberFunction range = new ConstantFunction(10);
    public float innerAngle = 20;
    public float outerAngle = 35;
    public float sourceRadius = 0.15f;
    public boolean castShadows = true;
    public float flicker = 0;
    public float flickerSpeed = 8;
    public boolean volumetricEnable = false;
    public NumberFunction volumetricStrength = new ConstantFunction(1);

    public LightConfig copy() {
        LightConfig c = new LightConfig();
        c.type = type;
        c.lifetime = lifetime;
        c.looping = looping;
        c.color = color;
        c.intensity = intensity;
        c.range = range;
        c.innerAngle = innerAngle;
        c.outerAngle = outerAngle;
        c.sourceRadius = sourceRadius;
        c.castShadows = castShadows;
        c.flicker = flicker;
        c.flickerSpeed = flickerSpeed;
        c.volumetricEnable = volumetricEnable;
        c.volumetricStrength = volumetricStrength;
        return c;
    }

    public enum Type {POINT, SPOT}
}

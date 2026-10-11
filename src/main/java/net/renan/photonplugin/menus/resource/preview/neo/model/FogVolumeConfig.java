package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.ConstantFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

public final class FogVolumeConfig implements FxObjectData {
    public Shape shape = Shape.BOX;
    public int lifetime = 100;
    public boolean looping = true;
    public NumberFunction density = new ConstantFunction(1);
    public float edgeFalloff = 0.3f;
    public NumberFunction color = new ConstantFunction(-1);
    public float absorption = 0.5f;
    public NumberFunction emission = new ConstantFunction(0xFF000000);
    public boolean noiseEnable = false;
    public float noiseStrength = 0.6f;
    public float noiseScale = 2f;
    public Vec3 noiseWind = new Vec3(0.5f, 0f, 0.2f);

    public FogVolumeConfig copy() {
        FogVolumeConfig c = new FogVolumeConfig();
        c.shape = shape;
        c.lifetime = lifetime;
        c.looping = looping;
        c.density = density;
        c.edgeFalloff = edgeFalloff;
        c.color = color;
        c.absorption = absorption;
        c.emission = emission;
        c.noiseEnable = noiseEnable;
        c.noiseStrength = noiseStrength;
        c.noiseScale = noiseScale;
        c.noiseWind = noiseWind;
        return c;
    }

    public enum Shape {BOX, SPHERE}
}

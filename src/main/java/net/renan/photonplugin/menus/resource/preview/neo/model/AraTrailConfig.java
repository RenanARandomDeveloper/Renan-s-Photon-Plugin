package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.ConstantFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.List;

public final class AraTrailConfig implements FxObjectData {
    public int duration = 100;
    public boolean looping = true;
    public Space space = Space.WORLD;
    public String customSpaceId = "";
    public Alignment alignment = Alignment.VIEW;
    public Sorting sorting = Sorting.OLDER_ON_TOP;
    public int smoothness = 1;
    public float smoothingDistance = 0.05f;
    public float thickness = 0.2f;
    public NumberFunction thicknessOverLength = new ConstantFunction(1f);
    public NumberFunction colorOverLength = new ConstantFunction(-1);
    public NumberFunction thicknessOverTime = new ConstantFunction(1f);
    public NumberFunction thicknessOverSegmentTime = new ConstantFunction(1f);
    public NumberFunction colorOverTime = new ConstantFunction(-1);
    public NumberFunction colorOverSegmentTime = new ConstantFunction(-1);
    public boolean emit = true;
    public float initialThickness = 1;
    public int initialColor = -1;
    public Vec3 initialVelocity = new Vec3(0, 0, 0);
    public float timeInterval = 0.05f;
    public float minDistance = 0.025f;
    public float time = 1f;
    public AraTrailPhysics physics = AraTrailPhysics.DEFAULT;
    public TextureMode textureMode = TextureMode.STRETCH;
    public float uvFactor = 1;
    public float uvWidthFactor = 1;
    public float tileAnchor = 1;
    public List<MaterialLayer> materials = List.of();
    public RenderSettings renderSettings = RenderSettings.DEFAULT;

    public AraTrailConfig copy() {
        AraTrailConfig c = new AraTrailConfig();
        c.duration = duration;
        c.looping = looping;
        c.space = space;
        c.customSpaceId = customSpaceId;
        c.alignment = alignment;
        c.sorting = sorting;
        c.smoothness = smoothness;
        c.smoothingDistance = smoothingDistance;
        c.thickness = thickness;
        c.thicknessOverLength = thicknessOverLength;
        c.colorOverLength = colorOverLength;
        c.thicknessOverTime = thicknessOverTime;
        c.thicknessOverSegmentTime = thicknessOverSegmentTime;
        c.colorOverTime = colorOverTime;
        c.colorOverSegmentTime = colorOverSegmentTime;
        c.emit = emit;
        c.initialThickness = initialThickness;
        c.initialColor = initialColor;
        c.initialVelocity = initialVelocity;
        c.timeInterval = timeInterval;
        c.minDistance = minDistance;
        c.time = time;
        c.physics = physics;
        c.textureMode = textureMode;
        c.uvFactor = uvFactor;
        c.uvWidthFactor = uvWidthFactor;
        c.tileAnchor = tileAnchor;
        c.materials = materials;
        c.renderSettings = renderSettings;
        return c;
    }

    public enum Space {WORLD, LOCAL, CUSTOM}

    public enum Alignment {VIEW, VELOCITY, LOCAL}

    public enum Sorting {OLDER_ON_TOP, NEWER_ON_TOP}

    public enum TextureMode {STRETCH, TILE, WORLD_TILE}
}

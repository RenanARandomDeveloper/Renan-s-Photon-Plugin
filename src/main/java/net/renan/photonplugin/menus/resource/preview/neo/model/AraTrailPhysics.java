package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public record AraTrailPhysics(boolean enable, float warmup, Vec3 gravity, ValueSpace gravitySpace,
                              String gravityCustomSpaceId, float inertia, float velocitySmoothing, float damping) {
    public static final AraTrailPhysics DEFAULT = new AraTrailPhysics(false, 0, new Vec3(0, 0, 0), ValueSpace.WORLD,
            "", 0, 0.75f, 0.75f);
}

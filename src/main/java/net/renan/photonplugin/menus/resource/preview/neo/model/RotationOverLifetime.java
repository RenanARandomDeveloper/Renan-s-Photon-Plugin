package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record RotationOverLifetime(boolean enable, NumberFunction yaw, NumberFunction pitch, NumberFunction roll,
                                   ValueSpace space, String customSpaceId) {
    public Vec3 getRotationDegrees(double t, MemRandom mem) {
        return new Vec3(
                (float) yaw.get(t, mem.of("rol-yaw")),
                (float) pitch.get(t, mem.of("rol-pitch")),
                (float) roll.get(t, mem.of("rol-roll")));
    }

    public boolean isLocal() {
        return space == null || space == ValueSpace.LOCAL;
    }
}

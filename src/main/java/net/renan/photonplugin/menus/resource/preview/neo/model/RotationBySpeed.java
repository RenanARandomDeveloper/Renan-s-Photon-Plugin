package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record RotationBySpeed(boolean enable, NumberFunction yaw, NumberFunction pitch, NumberFunction roll,
                              Range speedRange) {
    public Vec3 getRotationDegrees(float speedBlocksPerSecond, MemRandom mem) {
        float t = speedRange.fractionOf(speedBlocksPerSecond);
        return new Vec3(
                (float) yaw.get(t, mem.of("rbs-yaw")),
                (float) pitch.get(t, mem.of("rbs-pitch")),
                (float) roll.get(t, mem.of("rbs-roll")));
    }
}

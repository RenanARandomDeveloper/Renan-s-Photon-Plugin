package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record InheritVelocity(boolean enable, Mode mode, NumberFunction multiply) {
    public Vec3 getVelocity(Vec3 emitterVelocity, double t, MemRandom mem) {
        float m = (float) multiply.get(t, mem.of("inherit-velocity"));
        return new Vec3(emitterVelocity).mul(m);
    }

    public enum Mode {CURRENT, INITIAL}
}

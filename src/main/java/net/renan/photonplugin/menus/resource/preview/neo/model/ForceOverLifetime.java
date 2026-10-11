package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record ForceOverLifetime(boolean enable, NumberFunction3 force, ValueSpace space, String customSpaceId) {
    public Vec3 getForce(double t, MemRandom mem) {
        return force.get(t, mem.of("force-over-lifetime")).mul(0.05f);
    }
}

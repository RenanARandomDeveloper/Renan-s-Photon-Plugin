package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record Physics(boolean enable, NumberFunction friction, NumberFunction gravity) {
    public float getFriction(double t, MemRandom mem) {
        return (float) friction.get(t, mem.of("friction"));
    }

    public float getGravity(double t, MemRandom mem) {
        return (float) gravity.get(t, mem.of("gravity"));
    }
}

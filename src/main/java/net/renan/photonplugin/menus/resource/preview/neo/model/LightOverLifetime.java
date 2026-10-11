package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record LightOverLifetime(boolean enable, NumberFunction skyLight, NumberFunction blockLight) {
    public static LightOverLifetime fullBright() {
        return new LightOverLifetime(true, NumberFunction.constant(15), NumberFunction.constant(15));
    }

    public int getSkyLight(double t, MemRandom mem) {
        return (int) skyLight.get(t, mem.of("sky-light"));
    }

    public int getBlockLight(double t, MemRandom mem) {
        return (int) blockLight.get(t, mem.of("block-light"));
    }
}

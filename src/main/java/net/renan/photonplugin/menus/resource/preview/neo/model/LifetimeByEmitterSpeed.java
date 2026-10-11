package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record LifetimeByEmitterSpeed(boolean enable, NumberFunction multiplier, Range speedRange) {
    public int getLifetime(float emitterSpeedBlocksPerSecond, int initialLifetimeTicks, MemRandom mem) {
        float t = speedRange.fractionOfSorted(emitterSpeedBlocksPerSecond);
        return (int) (multiplier.get(t, mem.of("lifetime-by-speed")) * initialLifetimeTicks);
    }
}

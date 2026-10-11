package net.renan.photonplugin.menus.resource.preview.neo.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public final class MemRandom {
    private final Random random;
    private final Map<Object, Double> cache = new HashMap<>();
    private final Map<Object, Double> gaussianCache = new HashMap<>();

    public MemRandom(Random random) {
        this.random = random;
    }

    public double get(Object key) {
        return cache.computeIfAbsent(key, k -> random.nextDouble());
    }

    public double getGaussian(Object key) {
        return gaussianCache.computeIfAbsent(key, k -> random.nextGaussian());
    }

    public Supplier<Double> of(Object key) {
        return () -> get(key);
    }

    public Random random() {
        return random;
    }
}

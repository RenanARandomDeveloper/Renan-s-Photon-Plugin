package net.renan.photonplugin.menus.resource.preview.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

abstract class FParticle {
    final Random random;
    final Map<Object, Float> memRandom = new HashMap<>();

    boolean removed;
    int age;
    int lifetime;
    int delay;
    float t;

    FParticle(Random random) {
        this.random = random;
    }

    boolean isAlive() {
        return !removed;
    }

    abstract void tick();

    abstract Object renderKey();

    abstract void gather(FCtx ctx);

    float getMemRandom(Object key) {
        return getMemRandom(key, Random::nextFloat);
    }

    float getMemRandom(Object key, Function<Random, Float> supplier) {
        Float value = memRandom.get(key);
        if (value == null) {
            value = supplier.apply(random);
            memRandom.put(key, value);
        }
        return value;
    }
}

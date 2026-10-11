package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

abstract class FObject {
    final ForgeSimulation sim;
    final FRuntime runtime;
    final FTransform transform = new FTransform();
    final Random random;
    final Map<Object, Float> memRandom = new HashMap<>();
    String name = "";
    boolean removed;
    int age;

    int lifetime;
    float t;
    int delay;
    boolean visible = true;
    Vec3 previousPosition;
    Vec3 velocity = new Vec3(0, 0, 0);
    float rCol = 1, gCol = 1, bCol = 1, alpha = 1;

    FObject(ForgeSimulation sim, FRuntime runtime) {
        this.sim = sim;
        this.runtime = runtime;
        this.random = new Random(sim.nextSeed());
        transform.owner = this;
    }

    boolean isAlive() {
        for (FTransform child : transform.children()) {
            if (child.owner != null && child.owner.isAlive()) {
                return true;
            }
        }
        return false;
    }

    int getParticleAmount() {
        return 0;
    }

    void tick() {
    }

    void remove() {
        removed = true;
    }

    void remove(boolean force) {
        remove();
    }

    void reset() {
    }

    float getT(float partialTicks) {
        if (lifetime > 0) {
            return t + partialTicks / lifetime;
        }
        return 0;
    }

    float getMemRandom(Object key) {
        Float value = memRandom.get(key);
        if (value == null) {
            value = random.nextFloat();
            memRandom.put(key, value);
        }
        return value;
    }

    boolean isVisible() {
        if (!visible) {
            return false;
        }
        FTransform parent = transform.parent();
        return parent == null || parent.owner == null || parent.owner.isVisible();
    }

    Vec3 worldPosition() {
        return transform.position();
    }

    void gather(FCtx ctx) {
    }
}

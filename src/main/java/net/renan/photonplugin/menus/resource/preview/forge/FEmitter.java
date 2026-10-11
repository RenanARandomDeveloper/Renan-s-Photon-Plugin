package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.Random;

abstract class FEmitter extends FObject {
    final Random particleRandom;

    FEmitter(ForgeSimulation sim, FRuntime runtime) {
        super(sim, runtime);
        this.particleRandom = new Random(sim.nextSeed());
    }

    abstract int getLifetime();

    abstract boolean isLooping();

    abstract void update();

    @Override
    boolean isAlive() {
        if (!removed || getParticleAmount() != 0) {
            return true;
        }
        return super.isAlive();
    }

    @Override
    final void tick() {
        if (!isAlive()) {
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        if (previousPosition != null) {
            velocity = transform.position().sub(previousPosition);
        }
        previousPosition = transform.position();
        updateOrigin();
        update();
    }

    protected void updateOrigin() {
        lifetime = getLifetime();
    }

    protected final void baseUpdate() {
        if (age >= getLifetime() && !isLooping()) {
            remove(false);
        }
        age++;
        if (getLifetime() > 0) {
            t = (age % getLifetime()) * 1f / getLifetime();
        }
    }

    @Override
    void reset() {
        age = 0;
        memRandom.clear();
        removed = false;
        previousPosition = null;
        velocity = new Vec3(0, 0, 0);
        t = 0;
    }

    float[] rgba() {
        return new float[]{rCol, gCol, bCol, alpha};
    }
}

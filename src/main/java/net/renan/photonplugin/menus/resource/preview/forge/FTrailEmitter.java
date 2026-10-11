package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.TrailCfg;

final class FTrailEmitter extends FEmitter {
    final TrailCfg config;
    private FTrail trail;

    FTrailEmitter(ForgeSimulation sim, FRuntime runtime, TrailCfg config) {
        super(sim, runtime);
        this.config = config;
        init();
    }

    private void init() {
        trail = new FTrail(this, config, particleRandom);
    }

    @Override
    int getLifetime() {
        return config.duration;
    }

    @Override
    boolean isLooping() {
        return config.looping;
    }

    @Override
    int getParticleAmount() {
        return trail.isAlive() ? 1 : 0;
    }

    @Override
    void update() {
        if (trail.isAlive()) {
            trail.tick();
        } else {
            remove();
        }
        baseUpdate();
    }

    @Override
    void reset() {
        super.reset();
        init();
    }

    @Override
    void remove(boolean force) {
        trail.removed = true;
        super.remove(force);
        if (force) {
            trail.tails.clear();
        }
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !isVisible()) {
            return;
        }
        trail.gather(ctx);
    }
}

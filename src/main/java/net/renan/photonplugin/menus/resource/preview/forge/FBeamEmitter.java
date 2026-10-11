package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.BeamCfg;

final class FBeamEmitter extends FEmitter {
    final BeamCfg config;
    private FBeam beam;

    FBeamEmitter(ForgeSimulation sim, FRuntime runtime, BeamCfg config) {
        super(sim, runtime);
        this.config = config;
        init();
    }

    private void init() {
        beam = new FBeam(this, config, particleRandom);
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
        return beam.isAlive() ? 1 : 0;
    }

    @Override
    void update() {
        if (beam.isAlive()) {
            beam.tick();
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
        super.remove(force);
        beam.removed = true;
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !isVisible()) {
            return;
        }
        beam.gather(ctx);
    }
}

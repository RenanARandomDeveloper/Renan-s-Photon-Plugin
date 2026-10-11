package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.render.GroundForecast;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.sim.GroundProbe;
import net.renan.photonplugin.menus.resource.preview.common.sim.PreviewSimulation;
import net.renan.photonplugin.menus.resource.preview.neo.render.NeoItemGatherer;

import java.awt.*;

public final class NeoPreviewSimulation implements PreviewSimulation {
    private static final long PROBE_SLICE_NANOS = 6_000_000L;
    private final FxSceneSimulation simulation;
    private final NeoItemGatherer gatherer;
    private GroundForecast forecast;
    private GroundProbe probe;

    public NeoPreviewSimulation(FxSceneSimulation simulation, NeoItemGatherer gatherer) {
        this.simulation = simulation;
        this.gatherer = gatherer;
    }

    public FxSceneSimulation delegate() {
        return simulation;
    }

    @Override
    public void tick(float ticks) {
        simulation.tick(ticks);
    }

    @Override
    public void restart() {
        simulation.restart();
    }

    @Override
    public void simulateTo(float targetTicks) {
        simulation.simulateTo(targetTicks);
    }

    @Override
    public long seed() {
        return simulation.seed();
    }

    @Override
    public void setSeed(long seed) {
        simulation.setSeed(seed);
        forecast = null;
        probe = null;
    }

    @Override
    public float playbackTime() {
        return simulation.playbackTime();
    }

    @Override
    public int particleCount() {
        int count = 0;
        for (var pe : simulation.particleEmitters()) {
            count += pe.particles().size();
        }
        return count;
    }

    private GroundProbe newProbe() {
        try {
            FxSceneSimulation copy = simulation.probeCopy();
            PreviewCamera probeCamera = new PreviewCamera();
            return new GroundProbe(() -> copy.tick(1f), copy::isFinished, () -> gatherer.gather(copy, probeCamera));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public GroundForecast predictGround() {
        if (forecast == null) {
            if (probe == null) {
                probe = newProbe();
            }
            try {
                forecast = probe == null ? GroundForecast.NONE : probe.finish();
            } catch (RuntimeException e) {
                forecast = GroundForecast.NONE;
            }
        }
        return forecast;
    }

    @Override
    public boolean groundForecastReady() {
        return forecast != null;
    }

    private GroundForecast advanceProbe() {
        if (forecast != null) {
            return forecast;
        }
        if (probe == null) {
            probe = newProbe();
            if (probe == null) {
                forecast = GroundForecast.NONE;
                return forecast;
            }
        }
        try {
            if (probe.step(PROBE_SLICE_NANOS)) {
                forecast = probe.current();
                return forecast;
            }
            return probe.current();
        } catch (RuntimeException e) {
            forecast = GroundForecast.NONE;
            return forecast;
        }
    }

    @Override
    public void render(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        var items = gatherer.gather(simulation, camera);
        renderer.setLighting(simulation.collectLighting());
        renderer.setGroundForecast(advanceProbe());
        renderer.renderItems(g2, width, height, items, camera);
    }
}

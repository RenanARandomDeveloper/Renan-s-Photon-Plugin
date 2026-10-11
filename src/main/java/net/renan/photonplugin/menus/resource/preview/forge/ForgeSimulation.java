package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundForecast;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.common.sim.GroundProbe;
import net.renan.photonplugin.menus.resource.preview.common.sim.PreviewSimulation;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.ForgeScene;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.FxData;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class ForgeSimulation implements PreviewSimulation {
    private static final int MAX_SUB_DEPTH = 4;
    private static final int MAX_SUB_RUNTIMES = 400;
    private static final int MAX_STEPS_PER_CALL = 200_000;

    private static final FCollision.Box FLOOR = new FCollision.Box(-5.5, -1, -5.5, 5.5, 0, 5.5);
    private static final long PROBE_SLICE_NANOS = 6_000_000L;
    private final ForgeScene scene;
    private final FCollision collision = new FCollision(FLOOR);
    private final Vec3 rootPosition = new Vec3(0, 1, 0);
    private final List<FObject> active = new ArrayList<>();
    private final List<FObject> pending = new ArrayList<>();
    private long baseSeed;
    private Random seedSource;
    private FRuntime main;
    private int subRuntimes;
    private int tickCount;
    private float partial;
    private boolean collisionEnabled = true;
    private boolean autoRestart = true;
    private GroundForecast forecast;
    private GroundProbe probe;

    public ForgeSimulation(ForgeScene scene, long baseSeed) {
        this.scene = scene;
        this.baseSeed = baseSeed;
        restart();
    }

    long nextSeed() {
        return seedSource.nextLong();
    }

    void add(FObject object) {
        pending.add(object);
    }

    FCollision collision() {
        return collisionEnabled ? collision : null;
    }

    FRuntime createSubRuntime(String name, int depth) {
        FxData data = scene.subs.get(name);
        if (data == null || depth > MAX_SUB_DEPTH || subRuntimes >= MAX_SUB_RUNTIMES) {
            return null;
        }
        subRuntimes++;
        return new FRuntime(this, data, depth);
    }

    public void setCollisionEnabled(boolean enabled) {
        this.collisionEnabled = enabled;
    }

    public void setAutoRestart(boolean autoRestart) {
        this.autoRestart = autoRestart;
    }

    @Override
    public synchronized void restart() {
        seedSource = new Random(baseSeed);
        active.clear();
        pending.clear();
        subRuntimes = 0;
        tickCount = 0;
        partial = 0;
        main = new FRuntime(this, scene.main, 0);
        main.root.transform.setLocal(rootPosition, Quat.identity(), new Vec3(1, 1, 1));
        main.emmit();
    }

    @Override
    public synchronized void tick(float ticks) {
        float acc = partial + Math.max(0f, ticks);
        int steps = 0;
        while (acc >= 1f && steps < MAX_STEPS_PER_CALL) {
            step();
            tickCount++;
            acc -= 1f;
            steps++;
            if (autoRestart && finished()) {
                restart();
            }
        }
        partial = Math.min(acc, 0.9999f);
    }

    @Override
    public synchronized void simulateTo(float targetTicks) {
        restart();
        tick(Math.max(0f, targetTicks));
    }

    @Override
    public long seed() {
        return baseSeed;
    }

    @Override
    public void setSeed(long seed) {
        this.baseSeed = seed;
        this.forecast = null;
        this.probe = null;
    }

    @Override
    public synchronized float playbackTime() {
        return tickCount + partial;
    }

    @Override
    public synchronized int particleCount() {
        int count = 0;
        for (FObject object : active) {
            if (object instanceof FParticleEmitter emitter) {
                count += emitter.tileCount();
            }
        }
        return count;
    }

    private GroundProbe newProbe() {
        try {
            ForgeSimulation copy = new ForgeSimulation(scene, baseSeed);
            copy.setAutoRestart(false);
            copy.setCollisionEnabled(collisionEnabled);
            PreviewCamera probeCamera = new PreviewCamera();
            return new GroundProbe(() -> copy.tick(1f), copy::isFinished, () -> copy.gatherItems(probeCamera));
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

    private synchronized boolean isFinished() {
        return finished();
    }

    private synchronized List<RenderItem> gatherItems(PreviewCamera camera) {
        FCtx ctx = new FCtx(camera, partial);
        for (FObject object : active) {
            object.gather(ctx);
        }
        return ctx.items;
    }

    @Override
    public synchronized void render(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        FCtx ctx = new FCtx(camera, partial);
        for (FObject object : active) {
            object.gather(ctx);
        }
        renderer.setGroundForecast(advanceProbe());
        renderer.renderItems(g2, width, height, ctx.items, camera);
    }

    private void step() {
        Iterator<FObject> iterator = active.iterator();
        while (iterator.hasNext()) {
            FObject object = iterator.next();
            object.tick();
            if (!object.isAlive()) {
                iterator.remove();
            }
        }
        if (!pending.isEmpty()) {
            active.addAll(pending);
            pending.clear();
        }
    }

    private boolean finished() {
        return active.isEmpty() && pending.isEmpty();
    }
}

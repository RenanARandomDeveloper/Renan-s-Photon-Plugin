package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.neo.model.BeamConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.Transform;

import java.util.Random;

public final class BeamSim {
    private final BeamConfig authored;
    private final java.util.Map<String, Object> overrides = new java.util.HashMap<>();
    private final Transform sceneTransform;
    private final MemRandom mem;
    private BeamConfig config;
    private float age;

    private int delay;
    private boolean removed;
    private SceneNode node;

    public BeamSim(BeamConfig config, Transform sceneTransform, Random random) {
        this.config = config;
        this.sceneTransform = sceneTransform;
        this.mem = new MemRandom(random);
        this.authored = config;
        this.delay = config.startDelay;
    }

    public SceneNode node() {
        return node;
    }

    public void setNode(SceneNode node) {
        this.node = node;
    }

    public void tick(float dt) {
        if (removed) {
            return;
        }
        if (delay > 0) {
            delay--;
        }
        if (delay > 0) {
            age = 0;
        }
        age += dt;
        if (!config.looping && age >= config.duration) {
            removed = true;
        }
    }

    public boolean isFinishedForGood() {
        return removed;
    }

    public boolean isVisible() {
        return delay <= 0 && !removed && (node == null || node.isVisible());
    }

    public UvRect uv() {
        double t = getT();
        UvRect base = config.uvAnimation != null && config.uvAnimation.enable()
                ? config.uvAnimation.getUvs(t, mem) : UvRect.FULL;
        float offset = -(float) config.emitRate.get(t, mem.of("beam-emit"));
        return new UvRect(base.u0() + offset, base.v0(), base.u1() + offset, base.v1());
    }

    public void setOverride(String path, Object value) {
        if (!java.util.Objects.equals(overrides.put(path, value), value)) {
            config = ConfigOverrides.apply(authored, overrides);
        }
    }

    public BeamConfig config() {
        return config;
    }

    public Vec3 from() {
        return sceneTransform.position().copy();
    }

    public Vec3 to() {
        return sceneTransform.transformPoint(config.end);
    }

    public float width() {
        double t = getT();
        return (float) config.width.get(t, mem.of("beam-width"));
    }

    public int color() {
        double t = getT();
        return (int) Math.round(config.color.get(t, mem.of("beam-color")));
    }

    private double getT() {
        if (delay > 0 || config.duration <= 0) {
            return 0;
        }
        return config.looping ? (age % config.duration) / config.duration : Math.min(age / config.duration, 1.0);
    }
}

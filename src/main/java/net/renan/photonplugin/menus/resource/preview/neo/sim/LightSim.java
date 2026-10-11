package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.SceneLighting;
import net.renan.photonplugin.menus.resource.preview.neo.model.LightConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.HdrSampler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class LightSim {
    private final LightConfig authored;
    private final SceneNode node;
    private final Random random;
    private final Map<String, Object> overrides = new HashMap<>();
    private LightConfig config;
    private float age = -1;
    private float seed;
    private boolean removed;

    public LightSim(LightConfig config, SceneNode node, Random random) {
        this.authored = config;
        this.config = config;
        this.node = node;
        this.random = random;
    }

    public SceneNode node() {
        return node;
    }

    public LightConfig config() {
        return config;
    }

    public void setOverride(String path, Object value) {
        if (!Objects.equals(overrides.put(path, value), value)) {
            config = ConfigOverrides.apply(authored, overrides);
        }
    }

    public void tick(float dt) {
        if (removed) {
            return;
        }
        if (age < 0) {
            age = 0;
            seed = random.nextFloat();
        } else {
            age += dt;
        }
        if (!config.looping && age >= config.lifetime) {
            removed = true;
        }
    }

    public boolean isFinishedForGood() {
        return removed;
    }

    public boolean isShowing() {
        return age >= 0 && !removed && node.isActive() && node.isVisible();
    }

    private float lifetimeT() {
        int lifetime = Math.max(1, config.lifetime);
        float time = Math.max(age, 0);
        return config.looping ? (time % lifetime) / lifetime : Math.min(time / lifetime, 1f);
    }

    public SceneLighting.Light sample() {
        if (!isShowing()) {
            return null;
        }
        float t = lifetimeT();
        java.util.function.Supplier<Double> rnd = () -> (double) seed;
        float intensity = (float) config.intensity.get(t, rnd);
        float range = (float) config.range.get(t, rnd);
        if (intensity <= 0 || range <= 0) {
            return null;
        }
        float flicker = Math.max(0f, Math.min(1f, config.flicker));
        if (flicker > 0) {
            float phase = Math.max(age, 0) / 20f * config.flickerSpeed + seed * 100f;
            double tau = 6.2832;
            float noise = (float) (0.5 + 0.25 * Math.sin(phase * tau) + 0.15 * Math.sin(phase * 13.7 + 1.3)
                    + 0.1 * Math.sin(phase * 29.3 + 4.1));
            intensity *= 1f - flicker * Math.max(0f, Math.min(1f, noise));
        }
        float[] rgba = new float[4];
        HdrSampler.sample(config.color, t, rnd, rgba);
        Vec3 position = node.localToWorld().transformPosition(new Vec3());
        boolean spot = config.type == LightConfig.Type.SPOT;
        Vec3 axis = spot ? node.localToWorld().transformDirection(new Vec3(0, -1, 0)).normalize() : new Vec3(0, -1, 0);
        float volumetric = 0f;
        if (config.volumetricEnable) {
            volumetric = Math.max(0f, (float) config.volumetricStrength.get(t, rnd));
        }
        return SceneLighting.Light.of(position, rgba[0] * intensity, rgba[1] * intensity, rgba[2] * intensity, range,
                spot, axis, config.innerAngle, config.outerAngle, volumetric);
    }
}

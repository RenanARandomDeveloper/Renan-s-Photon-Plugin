package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.SceneLighting;
import net.renan.photonplugin.menus.resource.preview.neo.model.FogVolumeConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.HdrSampler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class FogVolumeSim {
    private final FogVolumeConfig authored;
    private final SceneNode node;
    private final Random random;
    private final Map<String, Object> overrides = new HashMap<>();
    private FogVolumeConfig config;
    private float age = -1;
    private float seed;
    private boolean removed;

    public FogVolumeSim(FogVolumeConfig config, SceneNode node, Random random) {
        this.authored = config;
        this.config = config;
        this.node = node;
        this.random = random;
    }

    public SceneNode node() {
        return node;
    }

    public FogVolumeConfig config() {
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

    public SceneLighting.Fog sample() {
        if (!isShowing()) {
            return null;
        }
        int lifetime = Math.max(1, config.lifetime);
        float time = Math.max(age, 0);
        float t = config.looping ? (time % lifetime) / lifetime : Math.min(time / lifetime, 1f);
        java.util.function.Supplier<Double> rnd = () -> (double) seed;
        float density = (float) config.density.get(t, rnd);
        if (!(density > 0)) {
            return null;
        }
        float[] color = new float[4];
        float[] emission = new float[4];
        HdrSampler.sample(config.color, t, rnd, color);
        HdrSampler.sample(config.emission, t, rnd, emission);
        Vec3 center = node.localToWorld().transformPosition(new Vec3());
        Vec3 scale = node.world().scale();
        Vec3 wind = new Vec3(0, 0, 0);
        float noise = 0f;
        if (config.noiseEnable) {
            noise = config.noiseStrength;
            wind = new Vec3(config.noiseWind).mul(-time / 20f);
        }
        return SceneLighting.Fog.of(config.shape == FogVolumeConfig.Shape.SPHERE, center, node.worldToLocal(),
                node.world().rotation(), scale, density, config.edgeFalloff, config.absorption, color, emission, noise,
                config.noiseScale, wind);
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.PerlinNoise;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public final class Noise {
    private final boolean enable;
    private final float frequency;
    private final Quality quality;
    private final boolean remapEnable;
    private final NumberFunction remapCurve;
    private final NumberFunction3 position;
    private final NumberFunction rotation;
    private final NumberFunction size;
    private final PerlinNoise generator = new PerlinNoise();

    public Noise(boolean enable, float frequency, Quality quality, boolean remapEnable, NumberFunction remapCurve,
                 NumberFunction3 position, NumberFunction rotation, NumberFunction size) {
        this.enable = enable;
        this.frequency = frequency;
        this.quality = quality;
        this.remapEnable = remapEnable;
        this.remapCurve = remapCurve;
        this.position = position;
        this.rotation = rotation;
        this.size = size;
    }

    public Noise with(Boolean enable, Float frequency, NumberFunction3 position, NumberFunction rotation, NumberFunction size) {
        return new Noise(enable != null ? enable : this.enable, frequency != null ? frequency : this.frequency, quality,
                remapEnable, remapCurve, position != null ? position : this.position,
                rotation != null ? rotation : this.rotation, size != null ? size : this.size);
    }

    public boolean enable() {
        return enable;
    }

    private void setupSeed(MemRandom mem) {
        double seed = mem.getGaussian("noise-seed") * 255;
        if (generator.getSeed() != seed) {
            generator.setSeed(seed);
        }
    }

    private float sample(double t) {
        double input = t * frequency;
        double value = switch (quality) {
            case NOISE_1D -> generator.noise(input);
            case NOISE_2D -> generator.noise(input, input);
            case NOISE_3D -> generator.noise(input, input, input);
        };
        if (remapEnable) {
            value = remapCurve.get((value + 1) / 2, () -> 0.0);
        }
        return (float) value;
    }

    public Vec3 getRotationDegrees(double t, MemRandom mem) {
        setupSeed(mem);
        float degree = (float) rotation.get(t, mem.of("noise-rotation"));
        if (degree == 0) {
            return new Vec3(0, 0, 0);
        }
        float n = sample((t + 10 * mem.get("noise-rotation-degree")) * 100);
        return new Vec3(degree, 0, 0).mul(n * (float) (Math.PI * 2 / 360));
    }

    public Vec3 getSizeDelta(double t, MemRandom mem) {
        setupSeed(mem);
        float scale = (float) size.get(t, mem.of("noise-size"));
        if (scale == 0) {
            return new Vec3(0, 0, 0);
        }
        float n = sample((t + 10 * mem.get("noise-size-scale")) * 100);
        return new Vec3(scale, scale, scale).mul(n);
    }

    public Vec3 getPositionDelta(double t, MemRandom mem) {
        setupSeed(mem);
        Vec3 offset = position.get(t, mem.of("noise-position"));
        if (offset.isZero()) {
            return new Vec3(0, 0, 0);
        }
        float nx = sample((t + 10 * mem.get("noise-position-x")) * 100);
        float ny = sample((t + 10 * mem.get("noise-position-y")) * 100);
        float nz = sample((t + 10 * mem.get("noise-position-z")) * 100);
        return offset.mul(nx, ny, nz);
    }

    public enum Quality {NOISE_1D, NOISE_2D, NOISE_3D}
}

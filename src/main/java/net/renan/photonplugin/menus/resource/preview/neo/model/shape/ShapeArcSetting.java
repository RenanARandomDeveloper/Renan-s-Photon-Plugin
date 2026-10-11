package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.Random;

public final class ShapeArcSetting {
    private final Mode mode;
    private final float arcSpread;
    private final NumberFunction arcSpeed;

    public ShapeArcSetting(String arcModeName, float arcSpread, NumberFunction arcSpeed) {
        this.mode = parseMode(arcModeName);
        this.arcSpread = Math.max(0f, Math.min(1f, arcSpread));
        this.arcSpeed = arcSpeed;
    }

    private static Mode parseMode(String name) {
        if (name == null) {
            return Mode.Random;
        }
        try {
            return Mode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Mode.Random;
        }
    }

    public double sampleArcFraction(Random random, EmissionContext context) {
        float speed = mode.usesArcSpeed() ? (float) arcSpeed.get(context.emitterT(), random::nextDouble) : 1.0f;
        return mode.sample(arcSpread, speed, context.emitterAgeTicks(), context.batchIndex(), context.batchCount(),
                random.nextDouble());
    }

    private enum Mode {
        Random, Loop, PingPong, BurstSpread;

        private static float positiveFraction(float value) {
            return value - (float) Math.floor(value);
        }

        private static float pingPong(float value) {
            float phase = positiveFraction(value / 2.0f) * 2.0f;
            return phase <= 1.0f ? phase : 2.0f - phase;
        }

        private static float applySpread(float value, float spread) {
            if (spread <= 0f) {
                return value;
            }
            float clampedSpread = clamp(spread, 0f, 1f);
            if (clampedSpread >= 1f) {
                return 0f;
            }
            return (float) Math.floor(value / clampedSpread) * clampedSpread;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        boolean usesArcSpeed() {
            return this == Loop || this == PingPong;
        }

        float sample(float spread, float speed, int emitterAgeTicks, int batchIndex, int batchCount, double randomValue) {
            float raw = switch (this) {
                case Random -> (float) randomValue;
                case Loop -> positiveFraction(emitterAgeTicks / 20.0f * speed);
                case PingPong -> pingPong(emitterAgeTicks / 20.0f * speed);
                case BurstSpread -> batchCount <= 1 ? 0.0f : (float) batchIndex / batchCount;
            };
            return applySpread(clamp(raw, 0f, 1f), spread);
        }
    }
}

package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

final class FUtil {
    static final float TWO_PI = (float) (Math.PI * 2);
    static final int FULL_BRIGHT = 15728880;

    private FUtil() {
    }

    static float lerp(float delta, float a, float b) {
        return a + delta * (b - a);
    }

    static Vec3 rotateX(Vec3 v, float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float y = v.y * cos - v.z * sin;
        float z = v.y * sin + v.z * cos;
        return v.set(v.x, y, z);
    }

    static Vec3 rotateY(Vec3 v, float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float x = v.x * cos + v.z * sin;
        float z = -v.x * sin + v.z * cos;
        return v.set(x, v.y, z);
    }

    static Vec3 rotateZ(Vec3 v, float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float x = v.x * cos - v.y * sin;
        float y = v.x * sin + v.y * cos;
        return v.set(x, y, v.z);
    }

    static Vec3 rotateYXY(Vec3 v, Vec3 r) {
        rotateY(v, r.y);
        rotateX(v, r.x);
        rotateY(v, r.z);
        return v;
    }

    static Vec3 project(Vec3 a, Vec3 b) {
        float d = b.dot(b);
        if (d == 0) {
            return new Vec3(0, 0, 0);
        }
        return new Vec3(b).mul(a.dot(b) / d);
    }

    static float max(Vec3 v) {
        return Math.max(v.x, Math.max(v.y, v.z));
    }

    static float red(int argb) {
        return ((argb >> 16) & 0xff) / 255f;
    }

    static float green(int argb) {
        return ((argb >> 8) & 0xff) / 255f;
    }

    static float blue(int argb) {
        return (argb & 0xff) / 255f;
    }

    static float alpha(int argb) {
        return ((argb >>> 24) & 0xff) / 255f;
    }

    static int argb(float r, float g, float b, float a) {
        return (to255(a) << 24) | (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    private static int to255(float v) {
        if (Float.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    static int skyOf(int packed) {
        return Math.max(0, Math.min(15, (packed >> 20) & 0xf));
    }

    static int blockOf(int packed) {
        return Math.max(0, Math.min(15, (packed >> 4) & 0xf));
    }

    static java.util.function.Supplier<Float> once(java.util.Random random) {
        return new java.util.function.Supplier<>() {
            private Float value;

            @Override
            public Float get() {
                if (value == null) {
                    value = random.nextFloat();
                }
                return value;
            }
        };
    }
}

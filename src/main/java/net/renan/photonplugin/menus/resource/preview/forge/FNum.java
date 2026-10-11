package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag.CompoundTag;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag.ListTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public interface FNum {
    static FNum constant(double v) {
        return new Const(v);
    }

    static FNum parse(NbtTag tag, double fallback) {
        if (!(tag instanceof CompoundTag c) || c.entries().isEmpty()) {
            return constant(fallback);
        }
        String type = ForgeNbt.string(c, "_type", "");
        return switch (type) {
            case "Constant", "Color" -> new Const(ForgeNbt.number(c.get("number"), 0));
            case "RandomConstant" -> new RandConst(ForgeNbt.real(c, "a", 0), ForgeNbt.real(c, "b", 0),
                    ForgeNbt.bool(c, "isDecimals", false));
            case "RandomColor" -> new RandColor((int) ForgeNbt.number(c.get("a"), 0xff000000),
                    (int) ForgeNbt.number(c.get("b"), 0xffffffff));
            case "Curve" -> {
                float lower = ForgeNbt.real(c, "lower", 0);
                float upper = ForgeNbt.real(c, "upper", 0);
                yield new Curve(lower, upper, curves(c.get("curves"), lower, upper, ForgeNbt.real(c, "defaultValue", 0)));
            }
            case "RandomCurve" -> {
                float lower = ForgeNbt.real(c, "lower", 0);
                float upper = ForgeNbt.real(c, "upper", 0);
                float def = ForgeNbt.real(c, "defaultValue", 0);
                yield new RandCurve(lower, upper, curves(c.get("curves0"), lower, upper, def),
                        curves(c.get("curves1"), lower, upper, def));
            }
            case "Gradient" -> new GradientFn(FGradient.parse(c));
            case "RandomGradient" ->
                    new RandGradient(FGradient.parse(c.getCompound("a")), FGradient.parse(c.getCompound("b")));
            default -> constant(0);
        };
    }

    private static List<CurveSegment> curves(NbtTag tag, float lower, float upper, float def) {
        List<CurveSegment> out = new ArrayList<>();
        if (tag instanceof ListTag(List<NbtTag> items)) {
            for (NbtTag item : items) {
                List<Float> v = ForgeNbt.floats(item);
                if (v.size() >= 8) {
                    out.add(new CurveSegment(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), v.get(5), v.get(6), v.get(7)));
                }
            }
        }
        if (out.isEmpty()) {
            float y = upper == lower ? 0.5f : (def - lower) / (upper - lower);
            out.add(new CurveSegment(0, y, 0.1f, y, 0.9f, y, 1, y));
        }
        return out;
    }

    static FNum3 parse3(NbtTag tag, double dx, double dy, double dz) {
        if (!(tag instanceof CompoundTag c) || c.entries().isEmpty()) {
            return new FNum3(constant(dx), constant(dy), constant(dz));
        }
        if (ForgeNbt.isNumber(c.get("x"))) {
            return new FNum3(constant(ForgeNbt.number(c.get("x"), dx)), constant(ForgeNbt.number(c.get("y"), dy)),
                    constant(ForgeNbt.number(c.get("z"), dz)));
        }
        return new FNum3(parse(c.get("x"), dx), parse(c.get("y"), dy), parse(c.get("z"), dz));
    }

    double get(float t, Supplier<Float> lerp);

    record Const(double value) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            return value;
        }
    }

    record RandConst(float a, float b, boolean decimals) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            float min = Math.min(a, b);
            float max = Math.max(a, b);
            if (min == max) {
                return max;
            }
            if (decimals) {
                return min + lerp.get() * (max - min);
            }
            return (int) (min + lerp.get() * (max + 1 - min));
        }
    }

    record RandColor(int a, int b) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            return FGradient.blend(a, b, lerp.get());
        }
    }

    record Curve(float lower, float upper, List<CurveSegment> curves) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            return lower + (upper - lower) * CurveSegment.evaluateCurveY(curves, t);
        }
    }

    record RandCurve(float lower, float upper, List<CurveSegment> curves0, List<CurveSegment> curves1) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            float a = CurveSegment.evaluateCurveY(curves0, t);
            float b = CurveSegment.evaluateCurveY(curves1, t);
            float randomY = a == b ? a : (Math.min(a, b) + lerp.get() * Math.abs(a - b));
            return lower + (upper - lower) * randomY;
        }
    }

    record GradientFn(FGradient gradient) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            return gradient.color(t);
        }
    }

    record RandGradient(FGradient g0, FGradient g1) implements FNum {
        @Override
        public double get(float t, Supplier<Float> lerp) {
            return FGradient.blend(g0.color(t), g1.color(t), lerp.get());
        }
    }

    final class FGradient {
        private final float[][] times = new float[4][];
        private final float[][] values = new float[4][];

        private FGradient(List<List<float[]>> tracks) {
            for (int i = 0; i < 4; i++) {
                List<float[]> keys = new ArrayList<>(tracks.get(i));
                keys.sort(Comparator.comparingDouble(k -> k[0]));
                if (keys.isEmpty()) {
                    keys.add(new float[]{0, 1});
                }
                times[i] = new float[keys.size()];
                values[i] = new float[keys.size()];
                for (int k = 0; k < keys.size(); k++) {
                    times[i][k] = keys.get(k)[0];
                    values[i][k] = keys.get(k)[1];
                }
            }
        }

        static FGradient white() {
            List<List<float[]>> tracks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<float[]> keys = new ArrayList<>();
                keys.add(new float[]{0, 1});
                keys.add(new float[]{1, 1});
                tracks.add(keys);
            }
            return new FGradient(tracks);
        }

        static FGradient parse(CompoundTag c) {
            List<List<float[]>> tracks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tracks.add(new ArrayList<>());
            }
            readPairs(c.get("a"), tracks.get(0));
            readPairs(c.get("r"), tracks.get(1));
            readPairs(c.get("g"), tracks.get(2));
            readPairs(c.get("b"), tracks.get(3));

            List<Float> rgb = ForgeNbt.floats(c.get("rgb"));
            if (tracks.get(1).isEmpty() && tracks.get(2).isEmpty() && tracks.get(3).isEmpty() && rgb.size() >= 4) {
                for (int i = 0; i + 3 < rgb.size(); i += 4) {
                    tracks.get(1).add(new float[]{rgb.get(i), rgb.get(i + 1)});
                    tracks.get(2).add(new float[]{rgb.get(i), rgb.get(i + 2)});
                    tracks.get(3).add(new float[]{rgb.get(i), rgb.get(i + 3)});
                }
            }

            float max = 0;
            for (int ch = 1; ch < 4; ch++) {
                for (float[] k : tracks.get(ch)) {
                    max = Math.max(max, k[1]);
                }
            }
            if (max > 1.0001f) {
                for (int ch = 0; ch < 4; ch++) {
                    for (float[] k : tracks.get(ch)) {
                        k[1] /= 255f;
                    }
                }
            }
            return new FGradient(tracks);
        }

        private static void readPairs(NbtTag tag, List<float[]> out) {
            List<Float> flat = ForgeNbt.floats(tag);
            for (int i = 0; i + 1 < flat.size(); i += 2) {
                out.add(new float[]{flat.get(i), flat.get(i + 1)});
            }
        }

        static int pack(float a, float r, float g, float b) {
            return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }

        private static int clamp(float v) {
            return Math.max(0, Math.min(255, (int) (v * 255f)));
        }

        static int blend(int colorA, int colorB, float f) {
            float a = ((colorA >>> 24) & 0xff) / 255f, a2 = ((colorB >>> 24) & 0xff) / 255f;
            float r = ((colorA >> 16) & 0xff) / 255f, r2 = ((colorB >> 16) & 0xff) / 255f;
            float g = ((colorA >> 8) & 0xff) / 255f, g2 = ((colorB >> 8) & 0xff) / 255f;
            float b = (colorA & 0xff) / 255f, b2 = (colorB & 0xff) / 255f;
            return pack(a + (a2 - a) * f, r + (r2 - r) * f, g + (g2 - g) * f, b + (b2 - b) * f);
        }

        private float sample(int track, float t) {
            float[] ts = times[track];
            float[] vs = values[track];
            if (t <= ts[0] || ts.length == 1) {
                return vs[0];
            }
            for (int i = 0; i < ts.length - 1; i++) {
                if (t <= ts[i + 1]) {
                    float span = ts[i + 1] - ts[i];
                    float f = span <= 1e-8f ? 1f : (t - ts[i]) / span;
                    return vs[i] + (vs[i + 1] - vs[i]) * f;
                }
            }
            return vs[vs.length - 1];
        }

        int color(float t) {
            return pack(sample(0, t), sample(1, t), sample(2, t), sample(3, t));
        }
    }

    record FNum3(FNum x, FNum y, FNum z) {
        public Vec3 get(float t, Supplier<Float> lerp) {
            return new Vec3((float) x.get(t, lerp), (float) y.get(t, lerp), (float) z.get(t, lerp));
        }
    }
}

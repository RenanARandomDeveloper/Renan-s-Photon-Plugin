package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.*;

import java.util.ArrayList;
import java.util.List;

public final class NumberFunctionParser {
    private NumberFunctionParser() {
    }

    public static NumberFunction parse(NbtTag tag, double fallback) {
        if (!(tag instanceof NbtTag.CompoundTag wrapper)) {
            return NumberFunction.constant(fallback);
        }
        String type = wrapper.getString("type", "constant");
        NbtTag.CompoundTag data = wrapper.getCompound("data");
        return switch (type) {
            case "constant" -> new ConstantFunction(data.getFloat("number", (float) fallback));
            case "random_constant" -> new RandomConstantFunction(data.getFloat("a", 0), data.getFloat("b", 1));
            case "curve" -> parseCurve(data, "curves");
            case "random_curve" -> parseRandomCurve(data);
            case "color" -> new ColorFunction(data.getInt("number", (int) fallback));
            case "random_color" -> new RandomColorFunction(data.getInt("a", 0xFFFFFFFF), data.getInt("b", 0xFFFFFFFF));
            case "gradient" -> new GradientFunction(parseGradient(data.getCompound("gradientColor")));
            case "random_gradient", "hdr_random_gradient" -> new RandomGradientFunction(
                    parseGradient(data.getCompound("gradientColor0")), parseGradient(data.getCompound("gradientColor1")));

            case "hdr_gradient" -> new GradientFunction(parseGradient(data.getCompound("gradientColor")));
            case "hdr_color" -> {
                float[] c = parseHdrColor(data.get("color"));
                yield new HdrColorFunction(c, c);
            }
            case "hdr_random_color" ->
                    new HdrColorFunction(parseHdrColor(data.get("colorA")), parseHdrColor(data.get("colorB")));
            default -> NumberFunction.constant(fallback);
        };
    }

    public static float[] parseHdrColor(NbtTag tag) {
        float r = 1, g = 1, b = 1, a = 1, intensity = 1;
        if (tag instanceof NbtTag.CompoundTag c) {
            NbtTag.ListTag list = c.getList("color");
            r = floatAt(list, 0, 1f);
            g = floatAt(list, 1, 1f);
            b = floatAt(list, 2, 1f);
            a = floatAt(list, 3, 1f);
            intensity = c.getFloat("intensity", 1f);
        } else if (tag instanceof NbtTag.ListTag list && list.size() >= 3) {
            r = floatAt(list, 0, 1f);
            g = floatAt(list, 1, 1f);
            b = floatAt(list, 2, 1f);
            intensity = floatAt(list, 3, 1f);
        }
        intensity = Math.max(0f, intensity);
        return new float[]{r * intensity, g * intensity, b * intensity, a};
    }

    private static float floatAt(NbtTag.ListTag list, int i, float fallback) {
        if (i >= list.size()) {
            return fallback;
        }
        Float f = asFloat(list.get(i));
        return f == null ? fallback : f;
    }

    public static NumberFunction3 parse3(NbtTag tag, double fallbackX, double fallbackY, double fallbackZ) {
        if (!(tag instanceof NbtTag.ListTag list) || list.size() < 3) {
            return NumberFunction3.constant(fallbackX, fallbackY, fallbackZ);
        }
        return new NumberFunction3(
                parse(list.get(0), fallbackX),
                parse(list.get(1), fallbackY),
                parse(list.get(2), fallbackZ));
    }

    private static CurveFunction parseCurve(NbtTag.CompoundTag data, String key) {
        float lower = data.getFloat("lower", 0);
        float upper = data.getFloat("upper", 1);
        return new CurveFunction(lower, upper, parseSegments(data.getList(key)));
    }

    private static RandomCurveFunction parseRandomCurve(NbtTag.CompoundTag data) {
        float lower = data.getFloat("lower", 0);
        float upper = data.getFloat("upper", 1);
        return new RandomCurveFunction(lower, upper, parseSegments(data.getList("curves0")), parseSegments(data.getList("curves1")));
    }

    private static List<CurveSegment> parseSegments(NbtTag.ListTag segmentsTag) {
        List<CurveSegment> segments = new ArrayList<>();
        for (int i = 0; i < segmentsTag.size(); i++) {
            if (segmentsTag.get(i) instanceof NbtTag.ListTag seg && seg.size() >= 8) {
                float[] v = new float[8];
                boolean ok = true;
                for (int j = 0; j < 8; j++) {
                    Float f = asFloat(seg.get(j));
                    if (f == null) {
                        ok = false;
                        break;
                    }
                    v[j] = f;
                }
                if (ok) {
                    segments.add(new CurveSegment(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]));
                }
            }
        }
        if (segments.isEmpty()) {
            segments.add(new CurveSegment(0, 1, 0.1f, 1, 0.9f, 1, 1, 1));
        }
        return segments;
    }

    private static Float asFloat(NbtTag tag) {
        return switch (tag) {
            case NbtTag.FloatTag f -> f.value();
            case NbtTag.DoubleTag d -> (float) d.value();
            case NbtTag.IntTag i -> (float) i.value();
            case null, default -> null;
        };
    }

    public static Gradient parseGradient(NbtTag.CompoundTag gradientColor) {
        List<Float> a = readFlatFloats(gradientColor, "a");
        List<Float> rgb = readFlatFloats(gradientColor, "rgb");
        List<Gradient.AlphaKey> alphaKeys = new ArrayList<>();
        for (int i = 0; i + 1 < a.size(); i += 2) {
            alphaKeys.add(new Gradient.AlphaKey(a.get(i), a.get(i + 1)));
        }
        List<Gradient.ColorKey> colorKeys = new ArrayList<>();
        for (int i = 0; i + 3 < rgb.size(); i += 4) {
            colorKeys.add(new Gradient.ColorKey(rgb.get(i), rgb.get(i + 1), rgb.get(i + 2), rgb.get(i + 3)));
        }
        if (alphaKeys.isEmpty() && colorKeys.isEmpty()) {
            return Gradient.whiteOpaque();
        }
        return new Gradient(alphaKeys, colorKeys);
    }

    private static List<Float> readFlatFloats(NbtTag.CompoundTag compound, String key) {
        List<Float> out = new ArrayList<>();
        NbtTag.ListTag list = compound.getList(key);
        for (int i = 0; i < list.size(); i++) {
            Float f = asFloat(list.get(i));
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Gradient {
    private final List<AlphaKey> alphaKeys;
    private final List<ColorKey> colorKeys;

    public Gradient(List<AlphaKey> alphaKeys, List<ColorKey> colorKeys) {
        this.alphaKeys = new ArrayList<>(alphaKeys);
        this.colorKeys = new ArrayList<>(colorKeys);
        this.alphaKeys.sort(Comparator.comparingDouble(AlphaKey::t));
        this.colorKeys.sort(Comparator.comparingDouble(ColorKey::t));
        if (this.alphaKeys.isEmpty()) {
            this.alphaKeys.add(new AlphaKey(0, 1));
        }
        if (this.colorKeys.isEmpty()) {
            this.colorKeys.add(new ColorKey(0, 1, 1, 1));
        }
    }

    public static Gradient whiteOpaque() {
        return new Gradient(List.of(new AlphaKey(0, 1), new AlphaKey(1, 1)),
                List.of(new ColorKey(0, 1, 1, 1), new ColorKey(1, 1, 1, 1)));
    }

    private static int clampByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    public int getColor(float t) {
        float a = sampleAlpha(t);
        float[] rgb = sampleColor(t);
        int ia = clampByte(a);
        int ir = clampByte(rgb[0]);
        int ig = clampByte(rgb[1]);
        int ib = clampByte(rgb[2]);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    public float[] getRgba(float t) {
        float[] rgb = sampleColor(t);
        return new float[]{rgb[0], rgb[1], rgb[2], sampleAlpha(t)};
    }

    private float sampleAlpha(float t) {
        if (alphaKeys.size() == 1) {
            return alphaKeys.get(0).alpha();
        }
        if (t <= alphaKeys.get(0).t()) {
            return alphaKeys.get(0).alpha();
        }
        for (int i = 0; i < alphaKeys.size() - 1; i++) {
            AlphaKey k0 = alphaKeys.get(i);
            AlphaKey k1 = alphaKeys.get(i + 1);
            if (t <= k1.t()) {
                float span = k1.t() - k0.t();
                float f = span <= 1e-8f ? 1f : (t - k0.t()) / span;
                return k0.alpha() + (k1.alpha() - k0.alpha()) * f;
            }
        }
        return alphaKeys.get(alphaKeys.size() - 1).alpha();
    }

    private float[] sampleColor(float t) {
        if (colorKeys.size() == 1) {
            ColorKey k = colorKeys.get(0);
            return new float[]{k.r(), k.g(), k.b()};
        }
        if (t <= colorKeys.get(0).t()) {
            ColorKey k = colorKeys.get(0);
            return new float[]{k.r(), k.g(), k.b()};
        }
        for (int i = 0; i < colorKeys.size() - 1; i++) {
            ColorKey k0 = colorKeys.get(i);
            ColorKey k1 = colorKeys.get(i + 1);
            if (t <= k1.t()) {
                float span = k1.t() - k0.t();
                float f = span <= 1e-8f ? 1f : (t - k0.t()) / span;
                return new float[]{
                        k0.r() + (k1.r() - k0.r()) * f,
                        k0.g() + (k1.g() - k0.g()) * f,
                        k0.b() + (k1.b() - k0.b()) * f};
            }
        }
        ColorKey k = colorKeys.get(colorKeys.size() - 1);
        return new float[]{k.r(), k.g(), k.b()};
    }

    public record AlphaKey(float t, float alpha) {
    }

    public record ColorKey(float t, float r, float g, float b) {
    }
}

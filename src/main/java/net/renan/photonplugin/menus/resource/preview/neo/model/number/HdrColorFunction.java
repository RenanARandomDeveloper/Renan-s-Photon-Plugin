package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.function.Supplier;

public record HdrColorFunction(float[] a, float[] b) implements NumberFunction, HdrSampler {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        float blend = a == b ? 0f : lerp.get().floatValue();
        int argb = 0;
        for (int i = 0; i < 4; i++) {
            float v = a[i] + (b[i] - a[i]) * blend;
            int ch = (int) (Math.max(0f, Math.min(1f, v)) * 255f);
            int shift = i == 3 ? 24 : 16 - 8 * i;
            argb |= ch << shift;
        }
        return argb;
    }

    @Override
    public void sampleHdr(double t, Supplier<Double> lerp, float[] out) {
        float blend = a == b ? 0f : lerp.get().floatValue();
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] + (b[i] - a[i]) * blend;
        }
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;

import java.util.function.Supplier;

public interface HdrSampler {
    static void sample(NumberFunction function, double t, Supplier<Double> lerp, float[] out) {
        if (function instanceof HdrSampler hdr) {
            hdr.sampleHdr(t, lerp, out);
            return;
        }
        int argb = (int) Math.round(function.get(t, lerp));
        out[0] = ColorMath.red(argb) / 255f;
        out[1] = ColorMath.green(argb) / 255f;
        out[2] = ColorMath.blue(argb) / 255f;
        out[3] = ColorMath.alpha(argb) / 255f;
    }

    void sampleHdr(double t, Supplier<Double> lerp, float[] out);
}

package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;

import java.util.function.Supplier;

public record RandomGradientFunction(Gradient gradientA, Gradient gradientB) implements NumberFunction, HdrSampler {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        int a = gradientA.getColor((float) t);
        int b = gradientB.getColor((float) t);
        return ColorMath.blendArgb(a, b, lerp.get().floatValue());
    }

    @Override
    public void sampleHdr(double t, Supplier<Double> lerp, float[] out) {
        float[] a = gradientA.getRgba((float) t);
        float[] b = gradientB.getRgba((float) t);
        float f = lerp.get().floatValue();
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] + (b[i] - a[i]) * f;
        }
    }
}

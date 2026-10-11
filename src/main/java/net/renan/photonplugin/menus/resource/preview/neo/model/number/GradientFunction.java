package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.function.Supplier;

public record GradientFunction(Gradient gradient) implements NumberFunction, HdrSampler {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        return gradient.getColor((float) t);
    }

    @Override
    public void sampleHdr(double t, Supplier<Double> lerp, float[] out) {
        float[] c = gradient.getRgba((float) t);
        System.arraycopy(c, 0, out, 0, 4);
    }
}

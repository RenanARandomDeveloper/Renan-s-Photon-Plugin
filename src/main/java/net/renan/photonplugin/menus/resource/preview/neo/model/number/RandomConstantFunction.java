package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.function.Supplier;

public record RandomConstantFunction(double a, double b) implements NumberFunction {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        return min + lerp.get() * (max - min);
    }
}

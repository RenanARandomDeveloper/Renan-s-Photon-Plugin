package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.function.Supplier;

public record ColorFunction(int argb) implements NumberFunction {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        return argb;
    }
}

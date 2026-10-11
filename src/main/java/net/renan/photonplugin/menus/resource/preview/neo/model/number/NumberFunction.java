package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import java.util.function.Supplier;

public interface NumberFunction {
    static NumberFunction constant(double value) {
        return new ConstantFunction(value);
    }

    double get(double t, Supplier<Double> lerp);

    default double get(double t) {
        return get(t, () -> 0.5);
    }
}

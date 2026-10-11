package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.function.Supplier;

public record NumberFunction3(NumberFunction x, NumberFunction y, NumberFunction z) {
    public static NumberFunction3 constant(double x, double y, double z) {
        return new NumberFunction3(NumberFunction.constant(x), NumberFunction.constant(y), NumberFunction.constant(z));
    }

    public Vec3 get(double t, Supplier<Double> lerp) {
        return new Vec3((float) x.get(t, lerp), (float) y.get(t, lerp), (float) z.get(t, lerp));
    }
}

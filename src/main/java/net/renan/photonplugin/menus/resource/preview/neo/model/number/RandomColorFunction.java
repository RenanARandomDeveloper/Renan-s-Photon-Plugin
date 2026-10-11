package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;

import java.util.function.Supplier;

public record RandomColorFunction(int argbA, int argbB) implements NumberFunction {
    @Override
    public double get(double t, Supplier<Double> lerp) {
        return ColorMath.blendArgb(argbA, argbB, lerp.get().floatValue());
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.function.Supplier;

public record ColorBySpeed(boolean enable, NumberFunction color, Range speedRange) {
    public int getColor(float speedBlocksPerSecond, Supplier<Double> lerp) {
        float t = speedRange.fractionOf(speedBlocksPerSecond);
        return (int) Math.round(color.get(t, lerp));
    }
}

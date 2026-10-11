package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.function.Supplier;

public record ColorOverLifetime(boolean enable, NumberFunction color) {
    public int getColor(double t, Supplier<Double> lerp) {
        return (int) Math.round(color.get(t, lerp));
    }
}

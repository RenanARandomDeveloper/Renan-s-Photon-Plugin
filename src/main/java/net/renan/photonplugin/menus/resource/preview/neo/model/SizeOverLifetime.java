package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;

import java.util.function.Supplier;

public record SizeOverLifetime(boolean enable, NumberFunction3 size) {
    public Vec3 getSize(double t, Supplier<Double> lerp) {
        return size.get(t, lerp);
    }
}

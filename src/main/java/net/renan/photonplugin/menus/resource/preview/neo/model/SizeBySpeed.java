package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;

import java.util.function.Supplier;

public record SizeBySpeed(boolean enable, NumberFunction3 size, Range speedRange) {
    public Vec3 getSize(float speedBlocksPerSecond, Supplier<Double> lerp) {
        float t = speedRange.fractionOf(speedBlocksPerSecond);
        return size.get(t, lerp);
    }
}

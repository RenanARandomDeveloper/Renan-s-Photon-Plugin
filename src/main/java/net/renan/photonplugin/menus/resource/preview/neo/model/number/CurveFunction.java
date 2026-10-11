package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;

import java.util.List;
import java.util.function.Supplier;

public final class CurveFunction implements NumberFunction {
    private final float lower;
    private final float upper;
    private final List<CurveSegment> segments;

    public CurveFunction(float lower, float upper, List<CurveSegment> segments) {
        this.lower = lower;
        this.upper = upper;
        this.segments = segments;
    }

    @Override
    public double get(double t, Supplier<Double> lerp) {
        float curveY = CurveSegment.evaluateCurveY(segments, (float) t);
        return lower + (upper - lower) * curveY;
    }
}

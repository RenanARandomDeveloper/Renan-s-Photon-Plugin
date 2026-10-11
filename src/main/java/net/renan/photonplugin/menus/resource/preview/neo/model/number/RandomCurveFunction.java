package net.renan.photonplugin.menus.resource.preview.neo.model.number;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;

import java.util.List;
import java.util.function.Supplier;

public final class RandomCurveFunction implements NumberFunction {
    private final float lower;
    private final float upper;
    private final List<CurveSegment> segmentsA;
    private final List<CurveSegment> segmentsB;

    public RandomCurveFunction(float lower, float upper, List<CurveSegment> segmentsA, List<CurveSegment> segmentsB) {
        this.lower = lower;
        this.upper = upper;
        this.segmentsA = segmentsA;
        this.segmentsB = segmentsB;
    }

    @Override
    public double get(double t, Supplier<Double> lerp) {
        float a = CurveSegment.evaluateCurveY(segmentsA, (float) t);
        float b = CurveSegment.evaluateCurveY(segmentsB, (float) t);
        float randomY = a == b ? a : (float) (Math.min(a, b) + lerp.get() * Math.abs(a - b));
        return lower + (upper - lower) * randomY;
    }
}

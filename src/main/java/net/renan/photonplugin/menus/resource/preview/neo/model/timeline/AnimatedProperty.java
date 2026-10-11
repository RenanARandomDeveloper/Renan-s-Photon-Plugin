package net.renan.photonplugin.menus.resource.preview.neo.model.timeline;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.shape.TimelineExpression;

import java.util.ArrayList;
import java.util.List;

public final class AnimatedProperty {
    public final Kind kind;
    public final String path;
    public final ValueType valueType;
    private final boolean shortestRotation;
    private final List<List<CurveSegment>> channels;
    private final List<List<ExprClip>> exprClips;
    private final List<List<CurveClip>> curveClips;
    private final List<ColorStop> stops;
    private final List<GradientClip> gradientClips;

    private final boolean hdr;

    public AnimatedProperty(Kind kind, String path, ValueType valueType, boolean shortestRotation,
                            List<List<CurveSegment>> channels, List<List<ExprClip>> exprClips,
                            List<List<CurveClip>> curveClips, List<ColorStop> stops, List<GradientClip> gradientClips,
                            boolean hdr) {
        this.kind = kind;
        this.path = path;
        this.valueType = valueType;
        this.shortestRotation = shortestRotation;
        this.channels = channels;
        this.exprClips = exprClips;
        this.curveClips = curveClips;
        this.stops = new ArrayList<>(stops);
        this.stops.sort((a, b) -> Float.compare(a.tick(), b.tick()));
        this.gradientClips = gradientClips;
        this.hdr = hdr;
    }

    private static float sampleCurve(List<CurveSegment> segments, float x) {
        if (segments.isEmpty()) {
            return 0f;
        }
        CurveSegment first = segments.get(0);
        CurveSegment last = segments.get(segments.size() - 1);
        if (x <= first.p0x()) {
            return first.p0y();
        }
        if (x >= last.p1x()) {
            return last.p1y();
        }
        for (CurveSegment seg : segments) {
            if (seg.p1x() > seg.p0x() && x >= seg.p0x() && x <= seg.p1x()) {
                return seg.evaluateY((x - seg.p0x()) / (seg.p1x() - seg.p0x()));
            }
        }
        return last.p1y();
    }

    private static List<CurveSegment> unwrap(List<CurveSegment> segments) {
        if (segments.size() <= 1) {
            return segments;
        }
        List<CurveSegment> out = new ArrayList<>(segments.size());
        float offset = 0f;
        for (CurveSegment seg : segments) {
            float p0y = seg.p0y() + offset;
            float c1y = seg.c1y() + offset;
            float rawDelta = seg.p1y() - seg.p0y();
            float wrapped = rawDelta - 360f * Math.round(rawDelta / 360f);
            float endOffset = (p0y + wrapped) - seg.p1y();
            out.add(new CurveSegment(seg.p0x(), p0y, seg.c1x(), c1y, seg.c2x(), seg.c2y() + endOffset,
                    seg.p1x(), seg.p1y() + endOffset));
            offset = endOffset;
        }
        return out;
    }

    private static int lerpArgb(int c0, int c1, float f) {
        int a = Math.round(((c0 >> 24) & 0xFF) + (((c1 >> 24) & 0xFF) - ((c0 >> 24) & 0xFF)) * f);
        int r = Math.round(((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * f);
        int g = Math.round(((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * f);
        int b = Math.round((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public int channelCount() {
        return channels.size();
    }

    public double lastKeyTime() {
        double last = 0;
        for (List<CurveSegment> segments : channels) {
            for (CurveSegment segment : segments) {
                last = Math.max(last, Math.max(segment.p0x(), segment.p1x()));
            }
        }
        return last;
    }

    public float[] sample(float time) {
        float[] values = new float[channels.size()];
        boolean stepped = kind == Kind.CONFIG && valueType.discrete();
        for (int i = 0; i < values.length; i++) {
            values[i] = stepped ? stepped(i, time) : value(i, time);
        }
        return values;
    }

    private Float evalExpression(int axis, float time) {
        for (ExprClip clip : exprClips.get(axis)) {
            if (clip.contains(time)) {
                double v = clip.expression().eval(time - clip.start());
                return Double.isFinite(v) ? (float) v : null;
            }
        }
        return null;
    }

    private float value(int axis, float time) {
        Float expr = evalExpression(axis, time);
        if (expr != null) {
            return expr;
        }
        List<CurveSegment> segments = channels.get(axis);
        if (kind == Kind.ROTATION && shortestRotation) {
            segments = unwrap(segments);
        }
        return sampleCurve(segments, time);
    }

    private float stepped(int axis, float time) {
        Float expr = evalExpression(axis, time);
        if (expr != null) {
            return clampDiscrete(expr);
        }
        List<CurveSegment> segments = channels.get(axis);
        if (segments.isEmpty()) {
            return 0f;
        }
        CurveSegment first = segments.get(0);
        boolean single = segments.size() == 1 && first.p0x() == first.p1x() && first.p0y() == first.p1y();
        int count = single ? 1 : segments.size() + 1;
        float value = first.p0y();
        for (int k = 0; k < count; k++) {
            float kx;
            float ky;
            if (k <= 0) {
                kx = first.p0x();
                ky = first.p0y();
            } else if (k >= segments.size()) {
                CurveSegment last = segments.get(segments.size() - 1);
                kx = last.p1x();
                ky = last.p1y();
            } else {
                kx = segments.get(k).p0x();
                ky = segments.get(k).p0y();
            }
            if (kx <= time) {
                value = ky;
            } else {
                break;
            }
        }
        return clampDiscrete(value);
    }

    private float clampDiscrete(float v) {
        return valueType == ValueType.BOOL ? (v >= 0.5f ? 1f : 0f) : Math.round(v);
    }

    public Object configValue(float time) {
        return switch (valueType) {
            case INT -> Math.round(stepped(0, time));
            case BOOL -> stepped(0, time) >= 0.5f;
            case FLOAT -> value(0, time);
            case NUMBER_FUNCTION -> function(0, time);
            case NUMBER_FUNCTION3 -> new NumberFunction3(function(0, time), function(1, time), function(2, time));
            case COLOR -> colorFunction(time);
        };
    }

    private NumberFunction function(int axis, float time) {
        for (CurveClip clip : curveClips.get(axis)) {
            if (clip.contains(time) && clip.curve() != null) {
                return clip.curve();
            }
        }
        return new ConstantFunction(value(axis, time));
    }

    private NumberFunction colorFunction(float time) {
        for (GradientClip clip : gradientClips) {
            if (clip.contains(time) && clip.gradient() != null) {
                return new GradientFunction(clip.gradient());
            }
        }
        if (hdr) {
            int argb = sampleColor(time);
            float intensity = sampleIntensity(time);
            float[] c = {((argb >> 16) & 0xFF) / 255f * intensity, ((argb >> 8) & 0xFF) / 255f * intensity,
                    (argb & 0xFF) / 255f * intensity, ((argb >> 24) & 0xFF) / 255f};
            return new HdrColorFunction(c, c);
        }
        return new ConstantFunction(sampleColor(time));
    }

    private float sampleIntensity(float time) {
        if (stops.isEmpty()) {
            return 1f;
        }
        ColorStop first = stops.get(0);
        ColorStop last = stops.get(stops.size() - 1);
        if (stops.size() == 1 || time <= first.tick()) {
            return first.intensity();
        }
        if (time >= last.tick()) {
            return last.intensity();
        }
        for (int i = 0; i < stops.size() - 1; i++) {
            ColorStop a = stops.get(i);
            ColorStop b = stops.get(i + 1);
            if (time >= a.tick() && time <= b.tick()) {
                float span = b.tick() - a.tick();
                float f = span <= 0 ? 0f : (time - a.tick()) / span;
                return a.intensity() + (b.intensity() - a.intensity()) * f;
            }
        }
        return last.intensity();
    }

    private int sampleColor(float time) {
        if (stops.isEmpty()) {
            return -1;
        }
        ColorStop first = stops.get(0);
        ColorStop last = stops.get(stops.size() - 1);
        if (stops.size() == 1 || time <= first.tick()) {
            return first.argb();
        }
        if (time >= last.tick()) {
            return last.argb();
        }
        for (int i = 0; i < stops.size() - 1; i++) {
            ColorStop a = stops.get(i);
            ColorStop b = stops.get(i + 1);
            if (time >= a.tick() && time <= b.tick()) {
                float span = b.tick() - a.tick();
                return lerpArgb(a.argb(), b.argb(), span <= 0 ? 0f : (time - a.tick()) / span);
            }
        }
        return last.argb();
    }

    public enum Kind {POSITION, ROTATION, SCALE, SPEED, CONFIG}

    public enum ValueType {
        INT, BOOL, FLOAT, NUMBER_FUNCTION, NUMBER_FUNCTION3, COLOR;

        public int channelCount() {
            return switch (this) {
                case NUMBER_FUNCTION3 -> 3;
                case COLOR -> 0;
                default -> 1;
            };
        }

        public boolean discrete() {
            return this == INT || this == BOOL;
        }
    }

    public record ExprClip(double start, double duration, TimelineExpression expression) {
        public boolean contains(double time) {
            return time >= start && time < start + duration;
        }
    }

    public record CurveClip(double start, double duration, NumberFunction curve) {
        public boolean contains(double time) {
            return time >= start && time < start + duration;
        }
    }

    public record GradientClip(double start, double duration, Gradient gradient) {
        public boolean contains(double time) {
            return time >= start && time < start + duration;
        }
    }

    public record ColorStop(float tick, int argb, float intensity) {
        public ColorStop(float tick, int argb) {
            this(tick, argb, 1f);
        }
    }
}

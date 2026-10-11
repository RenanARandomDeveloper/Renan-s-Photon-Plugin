package net.renan.photonplugin.menus.resource.preview.common.math;

import java.util.List;

public record CurveSegment(float p0x, float p0y, float c1x, float c1y, float c2x, float c2y, float p1x,
                           float p1y) {
    public static float evaluateCurveY(List<CurveSegment> segments, float x) {
        if (segments.isEmpty()) {
            return 0f;
        }
        CurveSegment first = segments.get(0);
        if (x < first.p0x) {
            return first.p0y;
        }
        for (CurveSegment seg : segments) {
            if (x >= seg.p0x && x <= seg.p1x) {
                float dx = seg.p1x - seg.p0x;
                return dx <= 0 ? seg.p1y : seg.evaluateY((x - seg.p0x) / dx);
            }
        }
        CurveSegment last = segments.get(segments.size() - 1);
        return last.p1y;
    }

    public float evaluateY(float t) {
        if (c1x == p0x) {
            return c1y > p0y ? p0y : p1y;
        }
        if (c2x == p1x) {
            return c2y > p1y ? p1y : p0y;
        }
        float mt = 1 - t;
        float mt2 = mt * mt;
        float t2 = t * t;
        return mt2 * mt * p0y + 3 * mt2 * t * c1y + 3 * mt * t2 * c2y + t2 * t * p1y;
    }
}

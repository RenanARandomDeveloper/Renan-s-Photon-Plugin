package net.renan.photonplugin.menus.resource.preview.common.render;

import java.util.Arrays;
import java.util.List;

public final class GroundSampler {
    public static final float OUTLIER_FRACTION = 0f;

    private final GroundExtent scratch = new GroundExtent();
    private final float[] bounds = new float[6];
    private float[] reaches = new float[256];
    private float[] sorted = new float[256];
    private float[] boxes = new float[256 * 6];
    private boolean[] hasPoints = new boolean[256];
    private int count;
    private boolean anyBounds;

    public float measure(List<RenderItem> items) {
        count = 0;
        for (RenderItem item : items) {
            scratch.reset();
            item.measure(scratch);
            if (count == reaches.length) {
                int size = count * 2;
                reaches = Arrays.copyOf(reaches, size);
                sorted = new float[size];
                boxes = Arrays.copyOf(boxes, size * 6);
                hasPoints = Arrays.copyOf(hasPoints, size);
            }
            reaches[count] = scratch.reach();
            hasPoints[count] = scratch.hasPoints();
            if (scratch.hasPoints()) {
                System.arraycopy(scratch.box(), 0, boxes, count * 6, 6);
            }
            count++;
        }
        float limit = trimmedReach();
        collectBounds(limit);
        return limit;
    }

    public float[] bounds() {
        return anyBounds ? bounds.clone() : null;
    }

    private float trimmedReach() {
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (reaches[i] > 0f) {
                sorted[n++] = reaches[i];
            }
        }
        if (n == 0) {
            return 0f;
        }
        int skip = (int) (n * OUTLIER_FRACTION);
        if (skip == 0) {
            float max = 0f;
            for (int i = 0; i < n; i++) {
                max = Math.max(max, sorted[i]);
            }
            return max;
        }
        Arrays.sort(sorted, 0, n);
        return sorted[n - 1 - skip];
    }

    private void collectBounds(float limit) {
        anyBounds = false;
        for (int i = 0; i < count; i++) {
            if (!hasPoints[i] || reaches[i] > limit) {
                continue;
            }
            int o = i * 6;
            if (!anyBounds) {
                System.arraycopy(boxes, o, bounds, 0, 6);
                anyBounds = true;
                continue;
            }
            for (int k = 0; k < 3; k++) {
                bounds[k] = Math.min(bounds[k], boxes[o + k]);
                bounds[k + 3] = Math.max(bounds[k + 3], boxes[o + k + 3]);
            }
        }
    }
}

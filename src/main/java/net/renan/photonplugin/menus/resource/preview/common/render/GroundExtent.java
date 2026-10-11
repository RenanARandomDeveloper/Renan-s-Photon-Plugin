package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public final class GroundExtent {
    private final float[] box = new float[6];
    private float reach;
    private boolean any;

    public void include(Vec3 p) {
        if (p != null) {
            include(p.x, p.y, p.z);
        }
    }

    public void include(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return;
        }
        reach = Math.max(reach, Math.max(Math.abs(x), Math.abs(z)));
        if (!any) {
            box[0] = x;
            box[1] = y;
            box[2] = z;
            box[3] = x;
            box[4] = y;
            box[5] = z;
            any = true;
            return;
        }
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.min(box[2], z);
        box[3] = Math.max(box[3], x);
        box[4] = Math.max(box[4], y);
        box[5] = Math.max(box[5], z);
    }

    public void includeAround(Vec3 center, float radius) {
        if (center != null && Float.isFinite(radius)) {
            include(center.x + radius, center.y + radius, center.z + radius);
            include(center.x - radius, center.y - radius, center.z - radius);
        }
    }

    public void reset() {
        reach = 0f;
        any = false;
    }

    public float reach() {
        return reach;
    }

    public boolean hasPoints() {
        return any;
    }

    public float[] box() {
        return box;
    }
}

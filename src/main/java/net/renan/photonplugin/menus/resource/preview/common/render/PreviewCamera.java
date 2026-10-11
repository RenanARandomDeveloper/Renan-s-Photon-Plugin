package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public final class PreviewCamera {
    public static final float NEAR_PLANE = 0.05f;
    private static final float MAX_DISTANCE = 2000f;
    private static final float EASE = 0.18f;
    private final Vec3 target = new Vec3(0, 0.5f, 0);
    private final float focalLength = 900f;
    private final Vec3 goalTarget = new Vec3();
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(20);
    private float distance = 4f;
    private float[] frameBounds;
    private boolean framePending;
    private float goalDistance = 4f;
    private int viewWidth = 960;
    private int viewHeight = 640;

    public float focalLength() {
        return focalLength;
    }

    public synchronized float yawRadians() {
        return yaw;
    }

    public synchronized Vec3 position() {
        return new Vec3(
                target.x + distance * (float) (Math.cos(pitch) * Math.sin(yaw)),
                target.y + distance * (float) Math.sin(pitch),
                target.z + distance * (float) (Math.cos(pitch) * Math.cos(yaw)));
    }

    public synchronized Vec3 forward() {
        return new Vec3(target).sub(position()).normalize();
    }

    public synchronized Vec3 right() {
        return Vec3.cross(forward(), new Vec3(0, 1, 0)).normalize();
    }

    public synchronized Vec3 up() {
        return Vec3.cross(right(), forward()).normalize();
    }

    public synchronized void orbit(float dYawPixels, float dPitchPixels) {
        framePending = false;
        yaw += dYawPixels * 0.008f;
        pitch += dPitchPixels * 0.008f;
        float limit = (float) Math.toRadians(89);
        pitch = Math.max(-limit, Math.min(limit, pitch));
    }

    public synchronized void zoom(float notches) {
        framePending = false;
        distance *= Math.pow(1.12f, notches);
        distance = Math.max(0.3f, Math.min(MAX_DISTANCE, distance));
    }

    public synchronized void pan(float dxPixels, float dyPixels) {
        framePending = false;
        float scale = distance * 0.0015f;
        Vec3 r = right();
        Vec3 u = up();
        target.add(r.mul(-dxPixels * scale));
        target.add(u.mul(dyPixels * scale));
    }

    public synchronized void frame(float[] bounds) {
        frameBounds = bounds == null ? null : bounds.clone();
        framePending = frameBounds != null;
        if (frameBounds == null) {
            resetView();
        }
    }

    public synchronized void applyPendingFrame(int width, int height) {
        if (width > 0 && height > 0) {
            viewWidth = width;
            viewHeight = height;
        }
        if (framePending && width > 0 && height > 0) {
            computeGoal();
            target.add(new Vec3(goalTarget).sub(target).mul(EASE));
            distance *= (float) Math.pow(goalDistance / distance, EASE);
            if (new Vec3(goalTarget).sub(target).length() < 0.002f * goalDistance
                    && Math.abs(goalDistance / distance - 1f) < 0.005f) {
                framePending = false;
                target.set(goalTarget);
                distance = goalDistance;
            }
        }
    }

    private void computeGoal() {
        float[] b = frameBounds;
        float dx = b[3] - b[0];
        float dy = b[4] - b[1];
        float dz = b[5] - b[2];
        float radius = Math.max(0.5f, 0.5f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        float t = Math.min(viewWidth, viewHeight) * 0.5f / focalLength;
        float fit = radius * (float) Math.sqrt(1 + t * t) / t * 1.1f;
        goalTarget.set((b[0] + b[3]) * 0.5f, (b[1] + b[4]) * 0.5f, (b[2] + b[5]) * 0.5f);
        goalDistance = Math.max(2f, Math.min(MAX_DISTANCE, fit));
    }

    public synchronized void resetView() {
        if (frameBounds != null) {
            yaw = (float) Math.toRadians(35);
            pitch = (float) Math.toRadians(20);
            framePending = false;
            computeGoal();
            target.set(goalTarget);
            distance = goalDistance;
            return;
        }
        yaw = (float) Math.toRadians(35);
        pitch = (float) Math.toRadians(20);
        distance = 4f;
        target.set(0, 0.5f, 0);
    }

    public synchronized void focusOn(Vec3 center, float radius) {
        target.set(center);
        distance = Math.max(1.5f, radius * 2.5f);
    }

    public synchronized Projected project(Vec3 worldPos, int viewWidth, int viewHeight) {
        Vec3 camPos = position();
        Vec3 fwd = forward();
        Vec3 rgt = right();
        Vec3 u = up();
        Vec3 rel = new Vec3(worldPos).sub(camPos);
        float depth = rel.dot(fwd);
        if (depth < NEAR_PLANE) {
            return null;
        }
        float camX = rel.dot(rgt);
        float camY = rel.dot(u);
        float screenScale = focalLength / depth;
        float sx = viewWidth / 2f + camX * screenScale;
        float sy = viewHeight / 2f - camY * screenScale;
        return new Projected(sx, sy, depth, screenScale);
    }

    public synchronized float depthOf(Vec3 worldPos) {
        return new Vec3(worldPos).sub(position()).dot(forward());
    }

    public record Projected(float x, float y, float depth, float scale) {
    }
}

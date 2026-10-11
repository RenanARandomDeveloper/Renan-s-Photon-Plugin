package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public record Transform(Vec3 position, Quat rotation, Vec3 scale) {
    public static Transform identity() {
        return new Transform(new Vec3(0, 0, 0), Quat.identity(), new Vec3(1, 1, 1));
    }

    private static float safeDiv(float s) {
        return Math.abs(s) < 1e-8f ? 0f : 1f / s;
    }

    public Vec3 transformPoint(Vec3 local) {
        Vec3 scaled = new Vec3(local).mul(scale);
        return rotation.rotate(scaled).add(position);
    }

    public Vec3 transformDirection(Vec3 local) {
        return rotation.rotate(new Vec3(local));
    }

    public Vec3 inverseTransformPoint(Vec3 parentSpace) {
        Vec3 v = new Vec3(parentSpace).sub(position);
        v = rotation.rotateInverse(v);
        return v.mul(safeDiv(scale.x), safeDiv(scale.y), safeDiv(scale.z));
    }

    public Transform childToParent(Transform child) {
        return new Transform(
                transformPoint(child.position),
                rotation.multiply(child.rotation).normalize(),
                new Vec3(scale).mul(child.scale));
    }
}

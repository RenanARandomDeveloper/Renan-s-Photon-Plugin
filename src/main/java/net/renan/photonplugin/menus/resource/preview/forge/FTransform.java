package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.ArrayList;
import java.util.List;

final class FTransform {
    private final List<FTransform> children = new ArrayList<>();
    private final Vec3 localPosition = new Vec3(0, 0, 0);
    private final Vec3 localScale = new Vec3(1, 1, 1);

    FObject owner;
    private FTransform parent;
    private Quat localRotation = Quat.identity();
    private Mat4 world;
    private Mat4 inverse;

    FTransform parent() {
        return parent;
    }

    List<FTransform> children() {
        return children;
    }

    void setParent(FTransform newParent) {
        if (parent != null) {
            parent.children.remove(this);
        }
        parent = newParent;
        if (newParent != null) {
            newParent.children.add(this);
        }
        dirty();
    }

    private void dirty() {
        world = null;
        inverse = null;
        for (FTransform child : children) {
            child.dirty();
        }
    }

    void setLocal(Vec3 position, Quat rotation, Vec3 scale) {
        localPosition.set(position);
        localRotation = rotation.copy();
        localScale.set(scale);
        dirty();
    }

    Vec3 localPosition() {
        return localPosition.copy();
    }

    Mat4 localToWorld() {
        if (world == null) {
            Mat4 local = Mat4.fromTRS(localPosition, localRotation, localScale);
            world = parent == null ? local : parent.localToWorld().multiply(local);
        }
        return world;
    }

    Mat4 worldToLocal() {
        if (inverse == null) {
            inverse = localToWorld().invert();
        }
        return inverse;
    }

    Vec3 position() {
        Mat4 m = localToWorld();
        return new Vec3(m.m[12], m.m[13], m.m[14]);
    }

    Vec3 scale() {
        if (parent == null) {
            return localScale.copy();
        }
        Vec3 p = parent.scale();
        return new Vec3(localScale.x * p.x, localScale.y * p.y, localScale.z * p.z);
    }

    Quat rotation() {
        return parent == null ? localRotation.copy() : parent.rotation().multiply(localRotation).normalize();
    }

    void setPosition(Vec3 worldPosition) {
        if (parent == null) {
            localPosition.set(worldPosition);
        } else {
            localPosition.set(parent.worldToLocal().transformPosition(worldPosition));
        }
        dirty();
    }

    void setScale(Vec3 worldScale) {
        if (parent == null) {
            localScale.set(worldScale);
        } else {
            Vec3 p = parent.scale();
            localScale.set(p.x == 0 ? 0 : worldScale.x / p.x, p.y == 0 ? 0 : worldScale.y / p.y,
                    p.z == 0 ? 0 : worldScale.z / p.z);
        }
        dirty();
    }

    void setRotation(Quat worldRotation) {
        localRotation = parent == null ? worldRotation.copy()
                : parent.rotation().conjugate().multiply(worldRotation).normalize();
        dirty();
    }
}

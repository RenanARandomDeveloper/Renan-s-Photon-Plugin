package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record VelocityOverLifetime(boolean enable, NumberFunction3 linear, OrbitalMode orbitalMode,
                                   NumberFunction3 orbital, NumberFunction3 offset, NumberFunction radial,
                                   NumberFunction speedModifier, ValueSpace space, String customSpaceId) {
    public Vec3 getVelocityAddition(double t, MemRandom mem, Vec3 emitterRelativePos) {
        Vec3 addition = linear.get(t, mem.of("vol-linear")).mul(0.05f);
        Vec3 orbitalVec = orbital.get(t, mem.of("vol-orbital"));
        Vec3 center = offset.get(t, mem.of("vol-offset"));
        float radialVal = (float) radial.get(t, mem.of("vol-radial"));
        boolean hasOrbital = !orbitalVec.isZero();

        if (hasOrbital || radialVal != 0) {
            Vec3 pos = new Vec3(emitterRelativePos);
            if (hasOrbital) {
                Vec3 toPoint = new Vec3(pos).sub(center);
                applyOrbitalAxis(addition, toPoint, orbitalVec.x, new Vec3(1, 0, 0));
                applyOrbitalAxis(addition, toPoint, orbitalVec.y, new Vec3(0, 1, 0));
                applyOrbitalAxis(addition, toPoint, orbitalVec.z, new Vec3(0, 0, 1));
            }
            if (radialVal != 0) {
                Vec3 radialDir = new Vec3(pos);
                if (radialDir.lengthSquared() > 1e-8f) {
                    addition.add(radialDir.normalize().mul(radialVal * 0.01f));
                }
            }
        }
        return addition;
    }

    private void applyOrbitalAxis(Vec3 addition, Vec3 toPoint, float axisSpeed, Vec3 axis) {
        if (axisSpeed == 0) {
            return;
        }
        switch (orbitalMode) {
            case ANGULAR_VELOCITY -> {
                Vec3 radiusVec = new Vec3(toPoint).sub(new Vec3(toPoint).project(axis));
                Vec3 rotated = new Vec3(radiusVec).rotateAroundAxis(axis, axisSpeed * 0.05f).sub(radiusVec);
                addition.add(rotated);
            }
            case LINEAR_VELOCITY -> {
                Vec3 radiusVec = new Vec3(toPoint).sub(new Vec3(toPoint).project(axis));
                float r = radiusVec.length();
                if (r > 1e-5f) {
                    Vec3 rotated = new Vec3(radiusVec).rotateAroundAxis(axis, axisSpeed * 0.05f / r).sub(radiusVec);
                    addition.add(rotated);
                }
            }
            case FIXED_VELOCITY -> {
                Vec3 toCenter = new Vec3(toPoint).mul(-1);
                Vec3 tangential = Vec3.cross(toCenter, axis);
                if (tangential.lengthSquared() > 1e-8f) {
                    addition.add(tangential.normalize().mul(axisSpeed * 0.05f));
                }
            }
        }
    }

    public float getVelocityMultiplier(double t, MemRandom mem) {
        return (float) speedModifier.get(t, mem.of("vol-speedModifier"));
    }

    public enum OrbitalMode {ANGULAR_VELOCITY, LINEAR_VELOCITY, FIXED_VELOCITY}
}

package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record ForceFieldConfig(Shape shape, float startRange, float endRange, NumberFunction directionX,
                               NumberFunction directionY, NumberFunction directionZ, NumberFunction gravity,
                               float gravityFocus, NumberFunction rotationSpeed, float rotationAttraction,
                               float rotationRandomnessX, float rotationRandomnessZ, NumberFunction drag,
                               boolean multiplyDragByParticleSize,
                               boolean multiplyDragByParticleVelocity) implements FxObjectData {
    private static final float FORCE_SCALE = 0.05f;

    public void apply(Transform fieldTransform, Vec3 worldPos, Vec3 worldVelocity, float particleSize, float dt,
                      double t, MemRandom mem, float multiplier) {
        Vec3 local = fieldTransform.inverseTransformPoint(worldPos);
        float endR = endRange;
        float startR = Math.min(startRange, endR);
        if (endR <= 0) {
            return;
        }
        float r = switch (shape) {
            case SPHERE -> local.length();
            case HEMISPHERE -> local.y < 0 ? Float.MAX_VALUE : local.length();
            case CYLINDER -> Math.max((float) Math.sqrt(local.x * local.x + local.z * local.z), Math.abs(local.y));
            case BOX -> Math.max(Math.abs(local.x), Math.max(Math.abs(local.y), Math.abs(local.z)));
        };
        if (r > endR) {
            return;
        }
        float influence = r <= startR ? 1f : 1f - (r - startR) / (endR - startR);
        float strength = influence * multiplier;
        if (strength == 0) {
            return;
        }

        Vec3 direction = new Vec3((float) directionX.get(t, mem.of("ff-dir-x")),
                (float) directionY.get(t, mem.of("ff-dir-y")), (float) directionZ.get(t, mem.of("ff-dir-z")));
        if (direction.lengthSquared() > 0) {
            float magnitude = direction.length();
            Vec3 worldDir = fieldTransform.transformDirection(direction).normalize(magnitude);
            worldVelocity.add(worldDir.mul(FORCE_SCALE * strength * dt));
        }

        float gravityVal = (float) gravity.get(t, mem.of("ff-gravity"));
        if (gravityVal != 0 && local.lengthSquared() > 1e-8f) {
            Vec3 pull = new Vec3(local).normalize(gravityFocus * endR).sub(local);
            if (pull.lengthSquared() > 1e-8f) {
                Vec3 worldPull = fieldTransform.transformDirection(pull).normalize();
                worldVelocity.add(worldPull.mul(gravityVal * FORCE_SCALE * strength * dt));
            }
        }

        float rotSpeed = (float) rotationSpeed.get(t, mem.of("ff-rotation-speed"));
        if (rotSpeed != 0) {
            float anchorX = rotationRandomnessX * (float) (mem.get("ff-anchor-x") * 2 - 1);
            float anchorZ = rotationRandomnessZ * (float) (mem.get("ff-anchor-z") * 2 - 1);
            float radialX = local.x - anchorX;
            float radialZ = local.z - anchorZ;
            if (radialX * radialX + radialZ * radialZ > 1e-8f) {
                Vec3 tangential = new Vec3(-radialZ, 0, radialX);
                Vec3 worldTangential = fieldTransform.transformDirection(tangential).normalize(rotSpeed * FORCE_SCALE);
                worldVelocity.add(new Vec3(worldTangential).mul(strength * dt));
                float attraction = Math.max(0f, Math.min(1f, rotationAttraction * strength * dt));
                if (attraction > 0) {
                    worldVelocity.lerp(worldTangential, attraction);
                }
            }
        }

        float dragVal = (float) drag.get(t, mem.of("ff-drag"));
        if (dragVal > 0) {
            float k = dragVal * strength;
            if (multiplyDragByParticleSize) {
                k *= particleSize;
            }
            if (multiplyDragByParticleVelocity) {
                k *= worldVelocity.length();
            }
            worldVelocity.mul(Math.max(0f, 1f - k * FORCE_SCALE * dt));
        }
    }

    public enum Shape {SPHERE, HEMISPHERE, CYLINDER, BOX}
}

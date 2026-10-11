package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.neo.model.FacingDirection;
import net.renan.photonplugin.menus.resource.preview.neo.model.FacingMode;

final class NeoFacing {
    private NeoFacing() {
    }

    static Vec3 quadCorner(Quat quaternion, float x, float y, Vec3 spaceScale, Vec3 center) {
        Vec3 v = quaternion.rotate(new Vec3(x, y, 0f));
        return v.mul(spaceScale).add(center);
    }

    static Quat cameraRotation(PreviewCamera camera) {
        Vec3 right = new Vec3(camera.right()).normalize();
        Vec3 up = new Vec3(camera.up()).normalize();
        Vec3 back = Vec3.cross(right, up).normalize();
        return Quat.fromBasis(right, up, back);
    }

    static Quat facingQuat(PreviewCamera camera, Vec3 particlePos, Vec3 velocity,
                           FacingMode mode,
                           FacingDirection facingDirection, Quat spaceRotation) {
        final float minThreshold = 0.0001f;
        Vec3 cameraPos = camera.position();
        switch (mode) {
            case ROTATE_Y:
                return Quat.rotationY(camera.yawRadians());
            case LOOKAT_XYZ:
                return lookAtXYZ(particlePos, cameraPos);
            case LOOKAT_Y: {
                float dx = cameraPos.x - particlePos.x;
                float dz = cameraPos.z - particlePos.z;
                if (dx * dx + dz * dz < minThreshold) {
                    return Quat.identity();
                }
                return Quat.rotationY((float) Math.atan2(dx, dz));
            }
            case LOOKAT_DIRECTION: {
                Vec3 toCamera = new Vec3(cameraPos).sub(particlePos);
                if (toCamera.lengthSquared() < minThreshold) {
                    return Quat.identity();
                }
                Vec3 dir = normalizeDirection(resolveDirection(facingDirection, velocity, spaceRotation), false);
                if (dir.lengthSquared() < minThreshold) {
                    return lookAtXYZ(particlePos, cameraPos);
                }
                Quat axisRotation = Quat.rotationTo(new Vec3(1, 0, 0), dir);
                Vec3 inAxisSpace = axisRotation.conjugate().rotate(new Vec3(cameraPos).sub(particlePos));
                float xRotation = (float) Math.atan2(-inAxisSpace.y, inAxisSpace.z);
                return axisRotation.multiply(Quat.rotationX(xRotation));
            }
            case DIRECTION_X: {
                Vec3 dir = normalizeDirection(resolveDirection(facingDirection, velocity, spaceRotation), true);
                if (dir.lengthSquared() < minThreshold) {
                    return Quat.identity();
                }
                float y = (float) Math.atan2(dir.x, dir.z);
                float z = (float) Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                return Quat.rotationYXZ(y - (float) Math.PI / 2, 0, z);
            }
            case DIRECTION_Y: {
                Vec3 dir = normalizeDirection(resolveDirection(facingDirection, velocity, spaceRotation), true);
                if (dir.lengthSquared() < minThreshold) {
                    return Quat.identity();
                }
                float y = (float) Math.atan2(dir.x, dir.z);
                float x = (float) Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                return Quat.rotationYXZ(y - (float) Math.PI, x - (float) Math.PI / 2, 0);
            }
            case DIRECTION_Z: {
                Vec3 dir = normalizeDirection(resolveDirection(facingDirection, velocity, spaceRotation), true);
                if (dir.lengthSquared() < minThreshold) {
                    return Quat.identity();
                }
                float y = (float) Math.atan2(dir.x, dir.z);
                float x = (float) Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
                return Quat.rotationYXZ(y, -x, 0);
            }
            case EMITTER_TRANSFORM_XZ:
                return spaceRotation.copy().multiply(Quat.rotationX(-(float) Math.PI / 2));
            case EMITTER_TRANSFORM_YZ:
                return spaceRotation.copy().multiply(Quat.rotationY((float) Math.PI / 2));
            case EMITTER_TRANSFORM_XY:
                return spaceRotation.copy();
            case DEFAULT:
            default:
                return cameraRotation(camera);
        }
    }

    static Quat lookAtXYZ(Vec3 particlePos, Vec3 cameraPos) {
        Vec3 toCamera = new Vec3(cameraPos).sub(particlePos);
        if (toCamera.lengthSquared() < 0.0001f) {
            return Quat.identity();
        }

        Vec3 z = toCamera.normalize();
        Vec3 x = Vec3.cross(new Vec3(0, 1, 0), z);
        if (x.lengthSquared() < 1e-12f) {
            x.set(1, 0, 0);
        }
        x.normalize();
        Vec3 y = Vec3.cross(z, x).normalize();
        return Quat.fromBasis(x, y, z);
    }

    static Vec3 resolveDirection(FacingDirection fd, Vec3 velocity, Quat spaceRotation) {
        if (fd == null || fd.mode() == FacingDirection.Mode.DERIVE_FROM_VELOCITY) {
            float threshold = fd != null ? fd.minSpeedThreshold() : 0.01f;
            if (velocity.lengthSquared() > threshold * threshold) {
                return new Vec3(velocity);
            }
            return new Vec3(0, 0, 0);
        }
        return spaceRotation.rotate(new Vec3(fd.customDirection()));
    }

    static Vec3 normalizeDirection(Vec3 direction, boolean applyDirectionModeVerticalHack) {
        Vec3 dir = new Vec3(direction);
        if (dir.lengthSquared() < 0.0001f) {
            return new Vec3(0, 0, 0);
        }
        dir.normalize();
        if (applyDirectionModeVerticalHack) {
            if (dir.x == 0 && dir.z == 0 && dir.y > 0) {
                dir.y = -1;
            } else if (dir.x == 0 && dir.z == 0 && dir.y < 0) {
                dir.y = 1;
                dir.z = -0.00001f;
            }
        }
        return dir;
    }

    static Quat baseOrientation(PreviewCamera camera, NeoParticleItem p) {
        return switch (p.renderMode()) {
            case HORIZONTAL -> Quat.rotationX((float) Math.toRadians(-90));
            case VERTICAL -> Quat.identity();

            case VERTICAL_BILLBOARD -> Quat.rotationY(camera.yawRadians());
            case BILLBOARD ->
                    facingQuat(camera, p.position(), p.velocity(), p.facingMode(), p.facingDirection(), p.spaceRotation());
            default -> Quat.identity();
        };
    }
}

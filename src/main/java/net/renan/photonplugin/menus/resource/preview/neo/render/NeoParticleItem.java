package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundExtent;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.neo.model.FacingDirection;
import net.renan.photonplugin.menus.resource.preview.neo.model.FacingMode;
import net.renan.photonplugin.menus.resource.preview.neo.model.RenderMode;

import java.awt.*;
import java.awt.image.BufferedImage;

public record NeoParticleItem(Vec3 position, Vec3 velocity, Vec3 size, Vec3 rotationRad, int color, int blockLight,
                              int skyLight, UvRect uv, IMaterial material, BlendMode blendMode,
                              RenderMode renderMode, FacingMode facingMode,
                              float velocityScale, float lengthScale, FacingDirection facingDirection,
                              Vec3 spaceScale, Quat spaceRotation, boolean cull, RenderSettings renderSettings,
                              float depthKey, Quat outerSpin) implements RenderItem {
    @Override
    public String passKind() {
        return "particle";
    }

    @Override
    public void measure(GroundExtent extent) {
        if ((color >>> 24) == 0) {
            return;
        }
        float radius = 0f;
        if (size != null) {
            float sx = Math.abs(size.x);
            float sy = Math.abs(size.y);
            float scale = 1f;
            if (spaceScale != null) {
                scale = Math.max(Math.abs(spaceScale.x), Math.max(Math.abs(spaceScale.y), Math.abs(spaceScale.z)));
            }
            float reachX = sx;
            if (renderMode == RenderMode.STRETCHED_BILLBOARD && velocity != null) {
                float stretch = Math.max(1f, lengthScale + velocity.length() * velocityScale);
                reachX = sx * stretch * 2f;
            }
            radius = (float) Math.sqrt(reachX * reachX + sy * sy) * scale;
        }
        extent.includeAround(position, radius);
    }

    @Override
    public void draw(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        NeoParticleItem p = this;
        Vec3 center = new Vec3(p.position());
        float sizeX = p.size().x;
        float sizeY = p.size().y;
        Vec3 spaceScale = p.spaceScale();
        Quat quaternion;
        if (p.renderMode() == RenderMode.STRETCHED_BILLBOARD) {
            Vec3 vel = p.velocity();
            float speed = vel.length();
            Vec3 right = speed > 1e-5f ? new Vec3(vel).mul(1f / speed) : new Vec3(1, 0, 0);
            Vec3 dirToCam = new Vec3(camera.position()).sub(center);
            dirToCam = dirToCam.lengthSquared() > 1e-5f ? dirToCam.normalize() : new Vec3(0, 0, 1);
            Vec3 up = Vec3.cross(dirToCam, right);
            if (up.lengthSquared() < 1e-5f) {
                up = Math.abs(right.y) > 0.99f
                        ? Vec3.cross(new Vec3(0, 0, 1), right).normalize()
                        : Vec3.cross(new Vec3(0, 1, 0), right).normalize();
            } else {
                up.normalize();
            }
            Vec3 forward = Vec3.cross(right, up).normalize();
            quaternion = Quat.fromBasis(right, up, forward);
            float stretch = p.lengthScale() + speed * p.velocityScale();
            float stretchedSizeX = sizeX * stretch;
            float offsetAmount = (stretchedSizeX - sizeX) * spaceScale.x;
            center.sub(new Vec3(right).mul(offsetAmount));
            sizeX = stretchedSizeX;
        } else {
            quaternion = NeoFacing.baseOrientation(camera, p);
            if (!p.rotationRad().isZero()) {
                quaternion = quaternion.multiply(Quat.euler(p.rotationRad()));
            }
            if (p.outerSpin() != null && (p.renderMode() == RenderMode.HORIZONTAL || p.renderMode() == RenderMode.VERTICAL)) {
                quaternion = p.outerSpin().multiply(quaternion);
            }
        }
        if (sizeX == 0f || sizeY == 0f) {
            return;
        }
        if (p.cull()) {
            Vec3 normal = quaternion.rotate(new Vec3(0, 0, 1));
            if (sizeX * sizeY < 0f) {
                normal.mul(-1f);
            }
            if (normal.dot(new Vec3(camera.position()).sub(center)) <= 0f) {
                return;
            }
        }

        Vec3 bottomRight = NeoFacing.quadCorner(quaternion, sizeX, -sizeY, spaceScale, center);
        Vec3 topRight = NeoFacing.quadCorner(quaternion, sizeX, sizeY, spaceScale, center);
        Vec3 topLeft = NeoFacing.quadCorner(quaternion, -sizeX, sizeY, spaceScale, center);
        Vec3 bottomLeft = NeoFacing.quadCorner(quaternion, -sizeX, -sizeY, spaceScale, center);

        BufferedImage tinted = renderer.tintedTexture(p.material(), p.color(), p.blockLight(), p.skyLight(), p.position());
        UvRect uv = p.uv();
        renderer.drawWorldTriangle(g2, width, height, camera, tinted,
                topLeft, uv.u0(), uv.v0(), topRight, uv.u1(), uv.v0(), bottomLeft, uv.u0(), uv.v1(), p.blendMode());
        renderer.drawWorldTriangle(g2, width, height, camera, tinted,
                topRight, uv.u1(), uv.v0(), bottomRight, uv.u1(), uv.v1(), bottomLeft, uv.u0(), uv.v1(), p.blendMode());
    }
}

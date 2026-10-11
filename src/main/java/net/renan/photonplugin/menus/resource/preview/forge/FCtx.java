package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;

import java.util.ArrayList;
import java.util.List;

final class FCtx {
    final List<RenderItem> items = new ArrayList<>();
    final PreviewCamera camera;
    final Vec3 camPos;
    final Vec3 right;
    final Vec3 up;
    final Vec3 forward;
    final float yaw;
    final float partial;

    final Quat cameraRotation;

    FCtx(PreviewCamera camera, float partial) {
        this.camera = camera;
        this.partial = partial;
        this.camPos = camera.position();
        this.right = camera.right();
        this.up = camera.up();
        this.forward = camera.forward();
        this.yaw = camera.yawRadians();
        this.cameraRotation = Quat.fromBasis(right.copy().negate(), up.copy(), forward.copy());
    }

    static RenderSettings settings(ForgeConfig.RendererCfg renderer, ForgeConfig.MaterialCfg material) {
        RenderSettings.Layer layer = renderer.layer == ForgeConfig.Layer.Opaque
                ? RenderSettings.Layer.OPAQUE : RenderSettings.Layer.TRANSLUCENT;

        RenderSettings.VertexSorting sorting = material.depthMask
                ? RenderSettings.VertexSorting.DISTANCE : RenderSettings.VertexSorting.NONE;
        return new RenderSettings(layer, 0, sorting);
    }

    float distance(Vec3 p) {
        float dx = p.x - camPos.x;
        float dy = p.y - camPos.y;
        float dz = p.z - camPos.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

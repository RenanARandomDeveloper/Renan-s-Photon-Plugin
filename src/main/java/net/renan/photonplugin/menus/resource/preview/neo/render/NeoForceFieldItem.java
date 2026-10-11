package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundExtent;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public record NeoForceFieldItem(Vec3 center, float endRange, float depthKey) implements RenderItem {
    @Override
    public RenderSettings renderSettings() {
        return RenderSettings.DEFAULT;
    }

    @Override
    public String passKind() {
        return "gizmo";
    }

    @Override
    public void measure(GroundExtent extent) {
        extent.include(center);
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void draw(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        PreviewCamera.Projected projected = camera.project(center, width, height);
        if (projected == null) {
            return;
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2.setColor(new Color(0x63d0ff));
        g2.setStroke(new BasicStroke(1.2f));
        float screenRadius = endRange * projected.scale();
        Ellipse2D circle = new Ellipse2D.Float(projected.x() - screenRadius, projected.y() - screenRadius, screenRadius * 2, screenRadius * 2);
        g2.draw(circle);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

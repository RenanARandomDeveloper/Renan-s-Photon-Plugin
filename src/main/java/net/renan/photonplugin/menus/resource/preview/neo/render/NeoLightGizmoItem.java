package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundExtent;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

public record NeoLightGizmoItem(Vec3 position, Vec3 axis, float range, boolean spot, float outerAngleDeg, int rgb,
                                float depthKey) implements RenderItem {
    private static final int RING_SEGMENTS = 24;

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
        extent.include(position);
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void draw(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        PreviewCamera.Projected center = camera.project(position, width, height);
        if (center == null) {
            return;
        }
        Color color = new Color(rgb);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.2f));
        if (!spot) {
            float r = range * center.scale();
            g2.draw(new Ellipse2D.Float(center.x() - r, center.y() - r, r * 2, r * 2));
        } else {
            Vec3 a = axis.lengthSquared() > 1e-10f ? new Vec3(axis).normalize() : new Vec3(0, -1, 0);
            Vec3 helper = Math.abs(a.y) > 0.9f ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 u = Vec3.cross(a, helper).normalize();
            Vec3 v = Vec3.cross(a, u).normalize();
            double outer = Math.toRadians(Math.max(0.1f, Math.min(89f, outerAngleDeg)));
            float ringRadius = (float) Math.tan(outer) * range;
            Vec3 ringCenter = new Vec3(position).add(new Vec3(a).mul(range));
            PreviewCamera.Projected prev = null;
            PreviewCamera.Projected first = null;
            for (int i = 0; i <= RING_SEGMENTS; i++) {
                double ang = Math.PI * 2 * i / RING_SEGMENTS;
                Vec3 p = new Vec3(ringCenter).add(new Vec3(u).mul((float) Math.cos(ang) * ringRadius))
                        .add(new Vec3(v).mul((float) Math.sin(ang) * ringRadius));
                PreviewCamera.Projected pp = camera.project(p, width, height);
                if (prev != null && pp != null) {
                    g2.draw(new Line2D.Float(prev.x(), prev.y(), pp.x(), pp.y()));
                }
                if (i % (RING_SEGMENTS / 4) == 0 && pp != null && i < RING_SEGMENTS) {
                    g2.draw(new Line2D.Float(center.x(), center.y(), pp.x(), pp.y()));
                }
                prev = pp;
                if (first == null) {
                    first = pp;
                }
            }
        }
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(color);
        g2.fill(new Ellipse2D.Float(center.x() - 3, center.y() - 3, 6, 6));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Ellipse2D.Float(center.x() - 3, center.y() - 3, 6, 6));
    }
}

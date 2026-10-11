package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundExtent;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;

import java.awt.*;
import java.awt.geom.Line2D;

public record NeoFogGizmoItem(Mat4 localToWorld, boolean sphere, int rgb, float depthKey) implements RenderItem {
    private static final int RING_SEGMENTS = 24;
    private static final int[][] BOX_EDGES = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    @Override
    public RenderSettings renderSettings() {
        return RenderSettings.DEFAULT;
    }

    @Override
    public String passKind() {
        return "gizmo";
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void measure(GroundExtent extent) {
        for (int i = 0; i < 8; i++) {
            extent.include(localToWorld.transformPosition(new Vec3((i & 1) == 0 ? -0.5f : 0.5f,
                    (i & 2) == 0 ? -0.5f : 0.5f, (i & 4) == 0 ? -0.5f : 0.5f)));
        }
    }

    @Override
    public void draw(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g2.setColor(new Color(rgb));
        g2.setStroke(new BasicStroke(1.2f));
        if (sphere) {
            for (int axis = 0; axis < 3; axis++) {
                PreviewCamera.Projected prev = null;
                for (int i = 0; i <= RING_SEGMENTS; i++) {
                    double ang = Math.PI * 2 * i / RING_SEGMENTS;
                    float c = (float) Math.cos(ang) * 0.5f;
                    float s = (float) Math.sin(ang) * 0.5f;
                    Vec3 local = switch (axis) {
                        case 0 -> new Vec3(0, c, s);
                        case 1 -> new Vec3(c, 0, s);
                        default -> new Vec3(c, s, 0);
                    };
                    PreviewCamera.Projected pp = camera.project(localToWorld.transformPosition(local), width, height);
                    if (prev != null && pp != null) {
                        g2.draw(new Line2D.Float(prev.x(), prev.y(), pp.x(), pp.y()));
                    }
                    prev = pp;
                }
            }
        } else {
            PreviewCamera.Projected[] corners = new PreviewCamera.Projected[8];
            for (int i = 0; i < 8; i++) {
                Vec3 local = new Vec3((i & 1) == 0 ? -0.5f : 0.5f, (i & 2) == 0 ? -0.5f : 0.5f, (i & 4) == 0 ? -0.5f : 0.5f);
                corners[i] = camera.project(localToWorld.transformPosition(local), width, height);
            }
            for (int[] e : BOX_EDGES) {
                if (corners[e[0]] != null && corners[e[1]] != null) {
                    g2.draw(new Line2D.Float(corners[e[0]].x(), corners[e[0]].y(), corners[e[1]].x(), corners[e[1]].y()));
                }
            }
        }
        g2.setComposite(AlphaComposite.SrcOver);
    }
}

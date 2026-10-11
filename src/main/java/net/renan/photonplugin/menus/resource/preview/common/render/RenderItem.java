package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;

import java.awt.*;

public interface RenderItem {
    float depthKey();

    RenderSettings renderSettings();

    String passKind();

    default IMaterial material() {
        return null;
    }

    default BlendMode blendMode() {
        return null;
    }

    default boolean overlay() {
        return false;
    }

    default void draw(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera) {
        throw new UnsupportedOperationException(getClass().getName() + " must implement draw()");
    }

    default void measure(GroundExtent extent) {
    }

    record RibbonSegmentItem(Vec3 a, Vec3 b, float widthA, float widthB, int colorA, int colorB, IMaterial material,
                             BlendMode blendMode, UvRect uv, RenderSettings renderSettings,
                             Vec3 bitangentA, Vec3 bitangentB, float depthKey) implements RenderItem {
        @Override
        public String passKind() {
            return "ribbon";
        }

        @Override
        public void measure(GroundExtent extent) {
            extent.include(a);
            extent.include(b);
        }
    }

    record MeshTriangleItem(Vec3 a, Vec3 b, Vec3 c, float ua, float va, float ub, float vb, float uc, float vc,
                            int color, int blockLight, int skyLight, IMaterial material, BlendMode blendMode,
                            RenderSettings renderSettings, float depthKey) implements RenderItem {
        @Override
        public String passKind() {
            return "mesh";
        }

        @Override
        public void measure(GroundExtent extent) {
            extent.include(a);
            extent.include(b);
            extent.include(c);
        }
    }

    record QuadItem(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, UvRect uv, int color, int blockLight, int skyLight,
                    IMaterial material, BlendMode blendMode, boolean cull, RenderSettings renderSettings,
                    float depthKey) implements RenderItem {
        @Override
        public String passKind() {
            return "quad";
        }

        @Override
        public void measure(GroundExtent extent) {
            extent.include(v0);
            extent.include(v1);
            extent.include(v2);
            extent.include(v3);
        }
    }
}

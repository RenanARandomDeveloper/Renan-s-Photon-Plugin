package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.*;
import java.util.List;

public final class Java2DFxRenderer {
    private static final int TINT_CACHE_MAX_ENTRIES = 256;
    private static final float LIT_STEPS = 6f;
    private static final Color BACKGROUND = new Color(0x2b2f36);
    private static final Color GRID_LINE = new Color(0x3d434d);

    private static final float MAX_REASONABLE_SCREEN_EXTENT = 20_000f;

    private static final int MAX_RENDER_ITEMS_PER_FRAME = 5_000;

    private static final boolean TEXTURE_NEAREST = true;
    private static final float[] GROUND_CELL_SIZES = {1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1000f};
    private static final int GROUND_MAX_CELLS = 16;
    private static final float GROUND_MIN_HALF = 3f;
    private static final float GROUND_MAX_HALF = 10_000f;
    private static final float GROUND_MARGIN = 1.08f;
    private final TextureCache textures;
    private final Map<Long, BufferedImage> tintCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest) {
            return size() > TINT_CACHE_MAX_ENTRIES;
        }
    };
    private final PreviewDiagnostics diagnostics;
    private final Map<BlendMode, Composite> compositeCache = new HashMap<>();
    private final GroundSampler groundSampler = new GroundSampler();
    private SceneLighting lighting;
    private boolean currentTextureBlurred;
    private boolean warnedAboutItemBudget;
    private GroundForecast groundForecast = GroundForecast.NONE;
    private float groundHintReach;
    private float groundSeenReach;
    private float groundHalf = GROUND_MIN_HALF;
    private float groundStep = GROUND_CELL_SIZES[0];

    public Java2DFxRenderer(TextureCache textures, PreviewDiagnostics diagnostics) {
        this.textures = textures;
        this.diagnostics = diagnostics;
    }

    private static PassKey passKeyOf(RenderItem item) {
        RenderSettings rs = item.renderSettings();
        return new PassKey(rs.translucent(), rs.orderInLayer(), rs.vertexSorting(), item.passKind(), item.material(),
                item.blendMode());
    }

    private static boolean allFinite(float... values) {
        for (float v : values) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReasonableExtent(PreviewCamera.Projected p0, PreviewCamera.Projected p1, PreviewCamera.Projected p2) {
        float minX = Math.min(p0.x(), Math.min(p1.x(), p2.x()));
        float maxX = Math.max(p0.x(), Math.max(p1.x(), p2.x()));
        float minY = Math.min(p0.y(), Math.min(p1.y(), p2.y()));
        float maxY = Math.max(p0.y(), Math.max(p1.y(), p2.y()));
        return (maxX - minX) < MAX_REASONABLE_SCREEN_EXTENT && (maxY - minY) < MAX_REASONABLE_SCREEN_EXTENT;
    }

    private static float vanillaLightCurve(int level) {
        float f = Math.max(0, Math.min(15, level)) / 15f;
        return f / (4f - 3f * f);
    }

    private static int clamp255(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : (int) v);
    }

    static BufferedImage pixelate(BufferedImage base, int bits) {
        int w = base.getWidth();
        int h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int sy = Math.min(h - 1, (int) (((Math.floor((y + 0.5f) / h * bits) + 0.5f) / bits) * h));
            for (int x = 0; x < w; x++) {
                int sx = Math.min(w - 1, (int) (((Math.floor((x + 0.5f) / w * bits) + 0.5f) / bits) * w));
                out.setRGB(x, y, base.getRGB(sx, sy));
            }
        }
        return out;
    }

    private static void drawGroundLine(Graphics2D g2, Vec3 a, Vec3 b, int width, int height, PreviewCamera camera,
                                       Vec3 eye, Vec3 forward) {
        float da = new Vec3(a).sub(eye).dot(forward);
        float db = new Vec3(b).sub(eye).dot(forward);
        float near = PreviewCamera.NEAR_PLANE * 2f;
        if (da < near && db < near) {
            return;
        }
        Vec3 from = a;
        Vec3 to = b;
        if (da < near) {
            float t = (near - da) / (db - da);
            from = new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        } else if (db < near) {
            float t = (near - da) / (db - da);
            to = new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        }
        PreviewCamera.Projected p1 = camera.project(from, width, height);
        PreviewCamera.Projected p2 = camera.project(to, width, height);
        if (p1 != null && p2 != null) {
            g2.drawLine(Math.round(p1.x()), Math.round(p1.y()), Math.round(p2.x()), Math.round(p2.y()));
        }
    }

    public void setLighting(SceneLighting lighting) {
        this.lighting = lighting != null && !lighting.isEmpty() ? lighting : null;
    }

    public void renderItems(Graphics2D g2, int width, int height, List<RenderItem> raw, PreviewCamera camera) {
        try {
            renderFrame(g2, width, height, raw, camera);
        } finally {
            lighting = null;
        }
    }

    private void renderFrame(Graphics2D g2, int width, int height, List<RenderItem> raw, PreviewCamera camera) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(BACKGROUND);
        g2.fillRect(0, 0, width, height);
        adaptGround(raw);
        drawGroundGrid(g2, width, height, camera);
        if (lighting != null && lighting.hasLights()) {
            lighting.drawGround(g2, width, height, camera, 0x2b2f36);
        }

        List<RenderItem> gizmos = new ArrayList<>();
        List<RenderItem> drawable = new ArrayList<>();
        for (RenderItem item : raw) {
            if (item.overlay()) {
                gizmos.add(item);
            } else {
                drawable.add(item);
            }
        }

        if (drawable.size() > MAX_RENDER_ITEMS_PER_FRAME) {
            drawable.sort(Comparator.comparing(RenderItem::depthKey));
            if (!warnedAboutItemBudget) {
                warnedAboutItemBudget = true;
                diagnostics.warn("FX preview: this effect wants to draw " + drawable.size() + " items in one frame "
                        + "(likely a Model-mode particle emitter with a high particle count — every particle "
                        + "draws its whole mesh) — capping this CPU preview to the nearest " + MAX_RENDER_ITEMS_PER_FRAME
                        + " per frame so it stays responsive. Simulation and particle counts are unaffected; "
                        + "this only limits what the preview itself draws each frame, not the exported effect.");
            }
            drawable = drawable.subList(0, MAX_RENDER_ITEMS_PER_FRAME);
        }

        for (RenderItem item : orderForRendering(drawable)) {
            switch (item) {
                case RenderItem.RibbonSegmentItem r -> drawRibbonSegment(g2, width, height, camera, r);
                case RenderItem.MeshTriangleItem t -> drawMeshTriangle(g2, width, height, camera, t);
                case RenderItem.QuadItem q -> drawQuad(g2, width, height, camera, q);
                default -> item.draw(this, g2, width, height, camera);
            }
        }

        if (lighting != null) {
            lighting.drawHaze(g2, width, height, camera);
        }

        gizmos.sort(Comparator.comparing(RenderItem::depthKey).reversed());
        for (RenderItem gizmo : gizmos) {
            gizmo.draw(this, g2, width, height, camera);
        }
    }

    private List<RenderItem> orderForRendering(List<RenderItem> items) {
        Map<PassKey, List<RenderItem>> passes = new LinkedHashMap<>();
        for (RenderItem item : items) {
            passes.computeIfAbsent(passKeyOf(item), k -> new ArrayList<>()).add(item);
        }
        List<Map.Entry<PassKey, List<RenderItem>>> ordered = new ArrayList<>(passes.entrySet());
        ordered.sort(Comparator
                .comparing((Map.Entry<PassKey, List<RenderItem>> e) -> e.getKey().translucent() ? 1 : 0)
                .thenComparing(e -> e.getKey().orderInLayer()));
        List<RenderItem> result = new ArrayList<>(items.size());
        for (Map.Entry<PassKey, List<RenderItem>> entry : ordered) {
            List<RenderItem> group = entry.getValue();
            if (entry.getKey().sorting() == RenderSettings.VertexSorting.DISTANCE) {
                group.sort(Comparator.comparing(RenderItem::depthKey).reversed());
            }
            result.addAll(group);
        }
        return result;
    }

    private int fadeAlpha(int argb, float alphaMul) {
        int a = Math.round(ColorMath.alpha(argb) * alphaMul);
        return ColorMath.pack(a, ColorMath.red(argb), ColorMath.green(argb), ColorMath.blue(argb));
    }

    private void drawMeshTriangle(Graphics2D g2, int width, int height, PreviewCamera camera, RenderItem.MeshTriangleItem t) {
        Vec3 centroid = new Vec3(t.a()).add(t.b()).add(t.c()).mul(1f / 3f);
        BufferedImage tinted = tintedTexture(t.material(), t.color(), t.blockLight(), t.skyLight(), centroid);
        drawWorldTriangle(g2, width, height, camera, tinted,
                t.a(), t.ua(), t.va(), t.b(), t.ub(), t.vb(), t.c(), t.uc(), t.vc(), t.blendMode());
    }

    private void drawQuad(Graphics2D g2, int width, int height, PreviewCamera camera, RenderItem.QuadItem q) {
        if (q.cull()) {
            Vec3 normal = Vec3.cross(new Vec3(q.v1()).sub(q.v0()), new Vec3(q.v2()).sub(q.v0()));
            if (normal.dot(new Vec3(camera.position()).sub(q.v0())) <= 0f) {
                return;
            }
        }
        Vec3 quadCenter = new Vec3(q.v0()).add(q.v1()).add(q.v2()).add(q.v3()).mul(0.25f);
        BufferedImage tinted = tintedTexture(q.material(), q.color(), q.blockLight(), q.skyLight(), quadCenter);
        UvRect uv = q.uv();

        drawWorldTriangle(g2, width, height, camera, tinted,
                q.v2(), uv.u0(), uv.v0(), q.v1(), uv.u1(), uv.v0(), q.v3(), uv.u0(), uv.v1(), q.blendMode());
        drawWorldTriangle(g2, width, height, camera, tinted,
                q.v1(), uv.u1(), uv.v0(), q.v0(), uv.u1(), uv.v1(), q.v3(), uv.u0(), uv.v1(), q.blendMode());
    }

    private void drawRibbonSegment(Graphics2D g2, int width, int height, PreviewCamera camera, RenderItem.RibbonSegmentItem r) {
        Vec3 nA;
        Vec3 nB;
        if (r.bitangentA() != null && r.bitangentB() != null) {
            nA = new Vec3(r.bitangentA());
            nB = new Vec3(r.bitangentB());
            if (nA.lengthSquared() < 1e-10f || nB.lengthSquared() < 1e-10f) {
                return;
            }
            nA.normalize();
            nB.normalize();
        } else {
            Vec3 toCameraA = new Vec3(camera.position()).sub(r.a());
            Vec3 axis = new Vec3(r.b()).sub(r.a());
            Vec3 n = Vec3.cross(toCameraA, axis);
            if (n.lengthSquared() < 1e-10f) {
                return;
            }
            n.normalize();
            nA = n;
            nB = n;
        }

        Vec3 a0 = new Vec3(r.a()).add(new Vec3(nA).mul(r.widthA() * 0.5f));
        Vec3 a1 = new Vec3(r.a()).add(new Vec3(nA).mul(-r.widthA() * 0.5f));
        Vec3 b0 = new Vec3(r.b()).add(new Vec3(nB).mul(r.widthB() * 0.5f));
        Vec3 b1 = new Vec3(r.b()).add(new Vec3(nB).mul(-r.widthB() * 0.5f));

        BufferedImage tinted = tintedTexture(r.material(), r.colorA(), 15, 15,
                new Vec3(r.a()).add(r.b()).mul(0.5f));
        UvRect uv = r.uv();
        float uStart = uv.u0();
        float uEnd = uv.u1();
        if (Math.abs(uEnd - uStart) < 1e-6f) {
            uEnd = uStart + 1e-3f;
        }
        drawWorldTriangle(g2, width, height, camera, tinted, a0, uStart, uv.v0(), a1, uStart, uv.v1(), b0, uEnd, uv.v0(), r.blendMode());
        drawWorldTriangle(g2, width, height, camera, tinted, a1, uStart, uv.v1(), b1, uEnd, uv.v1(), b0, uEnd, uv.v0(), r.blendMode());
    }

    public void drawWorldTriangle(Graphics2D g2, int width, int height, PreviewCamera camera, BufferedImage img,
                                  Vec3 wa, float ua, float va, Vec3 wb, float ub, float vb, Vec3 wc, float uc, float vc,
                                  BlendMode blendMode) {
        List<ClipVertex> polygon = clipTriangleNearPlane(camera,
                new ClipVertex(wa, ua, va), new ClipVertex(wb, ub, vb), new ClipVertex(wc, uc, vc));
        for (int i = 1; i + 1 < polygon.size(); i++) {
            drawClippedTriangle(g2, width, height, camera, img, polygon.get(0), polygon.get(i), polygon.get(i + 1), blendMode);
        }
    }

    private void drawClippedTriangle(Graphics2D g2, int width, int height, PreviewCamera camera, BufferedImage img,
                                     ClipVertex a, ClipVertex b, ClipVertex c, BlendMode blendMode) {
        PreviewCamera.Projected pa = camera.project(a.pos(), width, height);
        PreviewCamera.Projected pb = camera.project(b.pos(), width, height);
        PreviewCamera.Projected pc = camera.project(c.pos(), width, height);
        if (pa == null || pb == null || pc == null) {
            return;
        }
        drawTexturedTriangle(g2, img, a.u(), a.v(), b.u(), b.v(), c.u(), c.v(), pa, pb, pc, blendMode);
    }

    private List<ClipVertex> clipTriangleNearPlane(PreviewCamera camera, ClipVertex a, ClipVertex b, ClipVertex c) {
        ClipVertex[] triangle = {a, b, c};
        List<ClipVertex> output = new ArrayList<>(4);
        for (int i = 0; i < triangle.length; i++) {
            ClipVertex current = triangle[i];
            ClipVertex previous = triangle[(i + triangle.length - 1) % triangle.length];
            float currentDepth = camera.depthOf(current.pos());
            float previousDepth = camera.depthOf(previous.pos());
            boolean currentInFront = currentDepth >= PreviewCamera.NEAR_PLANE;
            boolean previousInFront = previousDepth >= PreviewCamera.NEAR_PLANE;
            if (currentInFront != previousInFront) {
                float t = (PreviewCamera.NEAR_PLANE - previousDepth) / (currentDepth - previousDepth);
                output.add(lerpVertex(previous, current, t));
            }
            if (currentInFront) {
                output.add(current);
            }
        }
        return output;
    }

    private ClipVertex lerpVertex(ClipVertex a, ClipVertex b, float t) {
        Vec3 pos = new Vec3(a.pos()).lerp(b.pos(), t);
        return new ClipVertex(pos, a.u() + (b.u() - a.u()) * t, a.v() + (b.v() - a.v()) * t);
    }

    private void drawTexturedTriangle(Graphics2D g2, BufferedImage img, float u0, float v0, float u1, float v1,
                                      float u2, float v2, PreviewCamera.Projected p0, PreviewCamera.Projected p1,
                                      PreviewCamera.Projected p2, BlendMode blendMode) {
        float iw = img.getWidth();
        float ih = img.getHeight();
        float sx0 = u0 * iw, sy0 = v0 * ih;
        float sx1 = u1 * iw, sy1 = v1 * ih;
        float sx2 = u2 * iw, sy2 = v2 * ih;

        float dx1 = sx1 - sx0, dy1 = sy1 - sy0;
        float dx2 = sx2 - sx0, dy2 = sy2 - sy0;
        float det = dx1 * dy2 - dy1 * dx2;
        if (Math.abs(det) < 1e-6f) {
            return;
        }

        float destDx1 = p1.x() - p0.x(), destDy1 = p1.y() - p0.y();
        float destDx2 = p2.x() - p0.x(), destDy2 = p2.y() - p0.y();
        float a = (destDx1 * dy2 - destDx2 * dy1) / det;
        float b = (dx1 * destDx2 - dx2 * destDx1) / det;
        float c = (destDy1 * dy2 - destDy2 * dy1) / det;
        float d = (dx1 * destDy2 - dx2 * destDy1) / det;
        float tx = p0.x() - (a * sx0 + b * sy0);
        float ty = p0.y() - (c * sx0 + d * sy0);

        if (!allFinite(a, b, c, d, tx, ty) || !isReasonableExtent(p0, p1, p2)) {
            return;
        }

        Shape oldClip = g2.getClip();
        Composite oldComposite = g2.getComposite();
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            Path2D.Float tri = new Path2D.Float();
            tri.moveTo(p0.x(), p0.y());
            tri.lineTo(p1.x(), p1.y());
            tri.lineTo(p2.x(), p2.y());
            tri.closePath();
            g2.clip(tri);
            g2.setComposite(compositeFor(blendMode));

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, TEXTURE_NEAREST && !currentTextureBlurred
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform base = new AffineTransform(a, c, b, d, tx, ty);
            int tileU0 = (int) Math.floor(Math.min(u0, Math.min(u1, u2)));
            int tileU1 = (int) Math.ceil(Math.max(u0, Math.max(u1, u2))) - 1;
            int tileV0 = (int) Math.floor(Math.min(v0, Math.min(v1, v2)));
            int tileV1 = (int) Math.ceil(Math.max(v0, Math.max(v1, v2))) - 1;
            if ((long) (tileU1 - tileU0 + 1) * (tileV1 - tileV0 + 1) > 64) {
                tileU0 = tileU1 = tileV0 = tileV1 = 0;
            }
            for (int tv = tileV0; tv <= Math.max(tileV0, tileV1); tv++) {
                for (int tu = tileU0; tu <= Math.max(tileU0, tileU1); tu++) {
                    AffineTransform at = new AffineTransform(base);
                    at.translate(tu * iw, tv * ih);
                    g2.drawImage(img, at, null);
                }
            }
        } finally {
            g2.setClip(oldClip);
            g2.setComposite(oldComposite);
            if (oldInterp != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            }
        }
    }

    private Composite compositeFor(BlendMode blendMode) {
        if (blendMode.isStandardAlpha()) {
            return AlphaComposite.getInstance(AlphaComposite.SRC_OVER);
        }
        return compositeCache.computeIfAbsent(blendMode, BlendComposite::new);
    }

    public BufferedImage tintedTexture(IMaterial material, int particleColor, int blockLight, int skyLight) {
        return tintedTexture(material, particleColor, blockLight, skyLight, null);
    }

    public BufferedImage tintedTexture(IMaterial material, int particleColor, int blockLight, int skyLight,
                                       Vec3 worldPos) {
        TextureMaterial tex = textures.resolveTexture(material);
        if (tex == null) {
            currentTextureBlurred = false;
            return solidColorSwatch(particleColor);
        }
        BufferedImage base = textures.get(tex.texture());
        currentTextureBlurred = textures.isBlurred(tex.texture());
        float brightness = Math.max(vanillaLightCurve(blockLight), vanillaLightCurve(skyLight));
        float[] lit = null;
        if (tex.lit() && worldPos != null && lighting != null && lighting.hasLights()) {
            lit = new float[3];
            lighting.litParticleBoost(worldPos, tex.litParticles(), lit);
            for (int i = 0; i < 3; i++) {
                lit[i] = Math.min(8f, Math.round(lit[i] * LIT_STEPS) / LIT_STEPS);
            }
            if (lit[0] <= 0f && lit[1] <= 0f && lit[2] <= 0f) {
                lit = null;
            }
        }
        long key = tintKey(tex, particleColor, brightness);
        if (lit != null) {
            key = key * 31 + Math.round(lit[0] * LIT_STEPS);
            key = key * 31 + Math.round(lit[1] * LIT_STEPS);
            key = key * 31 + Math.round(lit[2] * LIT_STEPS);
        }
        synchronized (tintCache) {
            BufferedImage cached = tintCache.get(key);
            if (cached != null) {
                return cached;
            }
            BufferedImage built = buildTinted(base, tex, particleColor, brightness, lit);
            tintCache.put(key, built);
            return built;
        }
    }

    private long tintKey(TextureMaterial tex, int particleColor, float brightness) {
        long h = tex.texture().hashCode();
        h = h * 31 + particleColor;
        h = h * 31 + Math.round(brightness * 32);
        h = h * 31 + Float.floatToIntBits(tex.discardThreshold());
        h = h * 31 + Arrays.hashCode(tex.hdrColor());
        h = h * 31 + (tex.hdrAdditive() ? 1 : 0);
        h = h * 31 + tex.pixelArtBits();
        return h;
    }

    private BufferedImage buildTinted(BufferedImage base, TextureMaterial tex, int particleColor, float brightness,
                                      float[] lit) {
        if (tex.pixelArtBits() > 0) {
            base = pixelate(base, tex.pixelArtBits());
        }
        int w = base.getWidth();
        int h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float lr = lit == null ? 1f : 1f + lit[0];
        float lg = lit == null ? 1f : 1f + lit[1];
        float lb = lit == null ? 1f : 1f + lit[2];
        float pr = ColorMath.red(particleColor) / 255f * brightness * lr;
        float pg = ColorMath.green(particleColor) / 255f * brightness * lg;
        float pb = ColorMath.blue(particleColor) / 255f * brightness * lb;
        float pa = ColorMath.alpha(particleColor) / 255f;
        float threshold255 = tex.discardThreshold() * 255f;
        float[] hdr = tex.hdrColor();
        boolean additive = tex.hdrAdditive();
        float hdrR = additive ? hdr[0] * 255f : hdr[0];
        float hdrG = additive ? hdr[1] * 255f : hdr[1];
        float hdrB = additive ? hdr[2] * 255f : hdr[2];
        int[] src = base.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        int count = w * h;
        for (int i = 0; i < count; i++) {
            int argb = src[i];
            float outA = ((argb >>> 24) & 0xFF) * pa;
            if (outA < threshold255) {
                dst[i] = 0;
                continue;
            }
            float outR = ((argb >>> 16) & 0xFF) * pr;
            float outG = ((argb >>> 8) & 0xFF) * pg;
            float outB = (argb & 0xFF) * pb;
            if (additive) {
                outR += hdrR;
                outG += hdrG;
                outB += hdrB;
            } else {
                outR *= hdrR;
                outG *= hdrG;
                outB *= hdrB;
            }
            dst[i] = (clamp255(outA) << 24) | (clamp255(outR) << 16) | (clamp255(outG) << 8) | clamp255(outB);
        }
        return out;
    }

    private BufferedImage solidColorSwatch(int argb) {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    public void setGroundReachHint(float reach) {
        setGroundForecast(new GroundForecast(reach, -1, null));
    }

    public void setGroundForecast(GroundForecast forecast) {
        if (forecast == groundForecast) {
            return;
        }
        float reach = Float.isFinite(forecast.reach()) ? Math.max(0f, forecast.reach()) : 0f;
        if (reach < groundHintReach) {
            groundSeenReach = 0f;
        }
        groundForecast = forecast;
        groundHintReach = reach;
    }

    private void adaptGround(List<RenderItem> items) {
        groundSeenReach = Math.max(groundSeenReach, groundSampler.measure(items));
        float needed = Math.min(GROUND_MAX_HALF, Math.max(GROUND_MIN_HALF, Math.max(groundHintReach, groundSeenReach) * GROUND_MARGIN));
        float step = GROUND_CELL_SIZES[GROUND_CELL_SIZES.length - 1];
        for (float candidate : GROUND_CELL_SIZES) {
            if (Math.ceil(needed / candidate - 1e-4) <= GROUND_MAX_CELLS) {
                step = candidate;
                break;
            }
        }
        groundStep = step;
        groundHalf = (float) Math.ceil(needed / step - 1e-4) * step;
    }

    public float groundHalfSize() {
        return groundHalf;
    }

    public float groundCellSize() {
        return groundStep;
    }

    private void drawGroundGrid(Graphics2D g2, int width, int height, PreviewCamera camera) {
        g2.setColor(GRID_LINE);
        g2.setStroke(new BasicStroke(1f));
        float half = groundHalf;
        float step = groundStep;
        Vec3 eye = camera.position();
        Vec3 forward = camera.forward();
        int cells = Math.round(half / step);
        for (int i = -cells; i <= cells; i++) {
            float at = i * step;
            drawGroundLine(g2, new Vec3(at, 0, -half), new Vec3(at, 0, half), width, height, camera, eye, forward);
            drawGroundLine(g2, new Vec3(-half, 0, at), new Vec3(half, 0, at), width, height, camera, eye, forward);
        }
    }

    private record PassKey(boolean translucent, int orderInLayer, RenderSettings.VertexSorting sorting, String kind,
                           IMaterial material, BlendMode blendMode) {
    }

    private record ClipVertex(Vec3 pos, float u, float v) {
    }
}

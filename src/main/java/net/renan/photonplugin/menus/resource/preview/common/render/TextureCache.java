package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.json.MinimalJsonParser;
import net.renan.photonplugin.menus.resource.preview.common.model.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public final class TextureCache {
    private static final int MAX_REFERENCE_DEPTH = 8;

    private static final TextureMaterial UNRESOLVABLE = new TextureMaterial("", new float[]{0, 0, 0}, true, 0f, false);
    private static final BufferedImage MISSING = buildMissingTexture();
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    private static final String NO_GRAPH_TEXTURE = "";
    private final AssetResolver resolver;
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private final Map<String, TextureMaterial> libraryMaterialCache = new HashMap<>();
    private final Set<String> alreadyWarnedNoAlpha = new HashSet<>();
    private final PreviewDiagnostics diagnostics;
    private final Map<String, String> graphTextureCache = new HashMap<>();
    private final Map<KilaMaterial, TextureMaterial> kilaCache = new IdentityHashMap<>();
    private final Set<String> kilaNotes = new HashSet<>();
    private final Set<String> blurred = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private int kilaCounter;
    private LibraryResolver library = LibraryResolver.NONE;

    public TextureCache(List<File> assetRoots, PreviewDiagnostics diagnostics) {
        this(new AssetResolver(assetRoots, diagnostics), diagnostics);
    }

    public TextureCache(AssetResolver resolver, PreviewDiagnostics diagnostics) {
        this.resolver = resolver;
        this.diagnostics = diagnostics;
    }

    private static boolean hasKnownImageExtension(String reference) {
        String lower = reference.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static BufferedImage buildMissingTexture() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int half = size / 2;
        g.setColor(new Color(0, 0, 0));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(255, 0, 255));
        g.fillRect(0, 0, half, half);
        g.fillRect(half, half, half, half);
        g.dispose();
        return img;
    }

    private static BufferedImage whiteSquare() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                img.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        return img;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static int to255(float v) {
        return Math.round(clamp01(v) * 255f);
    }

    public void setLibraryResolver(LibraryResolver library) {
        this.library = library == null ? LibraryResolver.NONE : library;
    }

    public void putGenerated(String reference, BufferedImage image) {
        cache.put(reference, image);
    }

    public BufferedImage get(String reference) {
        if (reference == null || reference.isBlank()) {
            return MISSING;
        }
        return cache.computeIfAbsent(reference, this::load);
    }

    public TextureMaterial resolveTexture(IMaterial material) {
        return resolveTexture(material, 0);
    }

    private TextureMaterial resolveTexture(IMaterial material, int depth) {
        if (material == null || depth > MAX_REFERENCE_DEPTH) {
            if (depth > MAX_REFERENCE_DEPTH) {
                diagnostics.warn("FX preview: material reference chain is too deep (possible cycle) — giving up");
            }
            return null;
        }
        if (material instanceof TextureMaterial t) {
            return t;
        }
        if (material instanceof KilaMaterial kila) {
            return kilaCache.computeIfAbsent(kila, this::bakeKila);
        }
        if (material instanceof LibraryMaterialRef(String resourcePath)) {
            return resolveLibraryMaterialCached(resourcePath, depth);
        }
        if (material instanceof UnsupportedMaterial u) {
            String texture = u.fallbackTexture();
            if (texture == null && u.graphPath() != null && !u.graphPath().isBlank()) {
                texture = resolveGraphTextureCached(u.graphPath());
            }
            if (texture != null) {
                return new TextureMaterial(texture, new float[]{0, 0, 0}, true, 0.1f, false);
            }
        }
        return null;
    }

    private TextureMaterial bakeKila(KilaMaterial k) {
        if (!k.unsupportedModules().isEmpty()) {
            String note = String.join(", ", k.unsupportedModules());
            if (kilaNotes.add(k.texture() + "|" + note)) {
                diagnostics.warn("FX preview: this Kila material uses " + note + ", which the CPU preview can't "
                        + "evaluate — showing its main texture, tint, emission, alpha and blend only (the exported "
                        + "effect renders them in-game).");
            }
        }
        BufferedImage base = k.texture() == null ? whiteSquare() : get(k.texture());
        if (k.flipColumns() > 0) {
            int cw = Math.max(1, base.getWidth() / k.flipColumns());
            int ch = Math.max(1, base.getHeight() / k.flipRows());
            int frame = k.flipStart() % (k.flipColumns() * k.flipRows());
            int cx = (frame % k.flipColumns()) * cw;
            int cy = (frame / k.flipColumns()) * ch;
            base = base.getSubimage(Math.min(cx, base.getWidth() - 1), Math.min(cy, base.getHeight() - 1),
                    Math.min(cw, base.getWidth() - cx), Math.min(ch, base.getHeight() - cy));
        }
        int w = base.getWidth();
        int h = base.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] tint = k.tint();
        boolean multiply = "MULTIPLY".equals(k.blend()) && !k.slotRenderState();
        boolean opaque = "OPAQUE".equals(k.blend()) && !k.slotRenderState();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = base.getRGB(x, y);
                float a = ((argb >>> 24) & 0xFF) / 255f;
                float r = ((argb >>> 16) & 0xFF) / 255f;
                float g = ((argb >>> 8) & 0xFF) / 255f;
                float b = (argb & 0xFF) / 255f;
                switch (k.colorMode()) {
                    case 1 -> a = r;
                    case 2 -> {
                        r = g = b = 1f;
                    }
                    case 3 -> a = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                    default -> {
                    }
                }
                r *= tint[0] * k.intensity();
                g *= tint[1] * k.intensity();
                b *= tint[2] * k.intensity();
                a = clamp01(a * tint[3]);
                a = clamp01((float) Math.pow(a, k.alphaSharpen()) * k.alpha());
                if (opaque) {
                    a = a >= k.alphaClip() ? 1f : 0f;
                } else if (multiply) {
                    r = 1f + (r - 1f) * a;
                    g = 1f + (g - 1f) * a;
                    b = 1f + (b - 1f) * a;
                }
                out.setRGB(x, y, (to255(a) << 24) | (to255(r) << 16) | (to255(g) << 8) | to255(b));
            }
        }
        String key = "kila-baked:" + (kilaCounter++);
        putGenerated(key, out);
        blurred.add(key);
        float[] glow = k.emission();
        return new TextureMaterial(key, new float[]{glow[0], glow[1], glow[2]}, true, opaque ? 0.5f : k.alphaClip(), 0,
                k.litIntensity());
    }

    private String resolveGraphTextureCached(String graphPath) {
        String cached = graphTextureCache.get(graphPath);
        if (cached == null) {
            String found = library.graphTexture(graphPath);
            cached = found == null ? NO_GRAPH_TEXTURE : found;
            graphTextureCache.put(graphPath, cached);
        }
        return cached.equals(NO_GRAPH_TEXTURE) ? null : cached;
    }

    private TextureMaterial resolveLibraryMaterialCached(String resourcePath, int depth) {
        TextureMaterial cached = libraryMaterialCache.get(resourcePath);
        if (cached != null) {
            return cached == UNRESOLVABLE ? null : cached;
        }
        TextureMaterial resolved = resolveTexture(library.material(resourcePath), depth + 1);
        libraryMaterialCache.put(resourcePath, resolved == null ? UNRESOLVABLE : resolved);
        return resolved;
    }

    private BufferedImage load(String reference) {
        AssetResolver.Resolved resolved = resolver.resolve(reference);
        String resolvedReference = reference;
        if (resolved == null && !hasKnownImageExtension(reference)) {
            resolved = resolver.resolve(reference + ".png");
            resolvedReference = reference + ".png";
        }
        if (resolved == null) {
            BufferedImage generated = KilaTextures.generate(reference);
            if (generated != null) {
                blurred.add(reference);
                return generated;
            }
        }
        if (resolved == null) {
            diagnostics.warn("FX preview: texture not found: \"" + reference + "\" "
                    + "(checked " + resolver.describeAssetLookup() + ")");
            return MISSING;
        }
        BufferedImage img = decode(reference, resolved.bytes());
        if (img == null) {
            return MISSING;
        }
        readBlurFlag(reference, resolvedReference);
        if (!img.getColorModel().hasAlpha() && alreadyWarnedNoAlpha.add(reference)) {
            diagnostics.warn("FX preview: texture \"" + reference + "\" has no alpha channel — it will "
                    + "render as a fully opaque " + img.getWidth() + "x" + img.getHeight() + " rectangle "
                    + "instead of a cutout shape. If it looks like a plain colored square in the preview, "
                    + "re-export it with transparency preserved.");
        }
        return img;
    }

    private void readBlurFlag(String reference, String resolvedReference) {
        AssetResolver.Resolved meta = resolver.resolve(resolvedReference + ".mcmeta");
        if (meta == null) {
            return;
        }
        try {
            Object parsed = MinimalJsonParser.parse(new String(meta.bytes(), java.nio.charset.StandardCharsets.UTF_8));
            Object texture = MinimalJsonParser.asMap(parsed).get("texture");
            if (MinimalJsonParser.asBoolean(MinimalJsonParser.asMap(texture).get("blur"), false)) {
                blurred.add(reference);
            }
        } catch (RuntimeException e) {
            diagnostics.warn("FX preview: couldn't read " + resolvedReference + ".mcmeta (" + e.getMessage()
                    + ") — sampling it with the default nearest filter");
        }
    }

    public boolean isBlurred(String reference) {
        if (reference != null && !reference.isBlank()) {
            get(reference);
        }
        return blurred.contains(reference);
    }

    private BufferedImage decode(String reference, byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                diagnostics.warn("FX preview: found \"" + reference + "\" but couldn't decode it as an image "
                        + "(unsupported format?)");
                return null;
            }
            return img;
        } catch (IOException e) {
            diagnostics.warn("FX preview: failed to decode \"" + reference + "\": " + e.getMessage());
            return null;
        }
    }
}

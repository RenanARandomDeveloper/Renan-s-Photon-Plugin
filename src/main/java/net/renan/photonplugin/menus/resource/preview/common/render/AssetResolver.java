package net.renan.photonplugin.menus.resource.preview.common.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AssetResolver {
    private static final String BUNDLE_RESOURCE = "/photon-default-assets.zip";
    private static final String JPEG_COLOR_SUFFIX = ".jpg";
    private static final String JPEG_ALPHA_SUFFIX = ".a.jpg";
    private static final int ALPHA_SNAP = 5;
    private static final Map<String, byte[]> composedTextures = new ConcurrentHashMap<>();

    private static final String PACK_ASSETS_PREFIX = "assets/";
    private static final Pattern RESOURCE_PATH = Pattern.compile("^([a-zA-Z0-9_\\-]+)\\((.+)\\)$");
    private static volatile Map<String, byte[]> bundledEntries;

    private final List<File> assetRoots;
    private final AssetSource pack;
    private final PreviewDiagnostics diagnostics;

    public AssetResolver(List<File> assetRoots, PreviewDiagnostics diagnostics) {
        this(assetRoots, null, diagnostics);
    }

    public AssetResolver(List<File> assetRoots, AssetSource pack, PreviewDiagnostics diagnostics) {
        this.assetRoots = assetRoots;
        this.pack = pack;
        this.diagnostics = diagnostics;
    }

    public AssetResolver(File assetsRoot, PreviewDiagnostics diagnostics) {
        this(List.of(assetsRoot), diagnostics);
    }

    private static String cleanRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String cleaned = relativePath.trim().replace('\\', '/');
        return cleaned.startsWith("./") ? cleaned.substring(2) : cleaned;
    }

    private static Map<String, byte[]> bundledEntries(PreviewDiagnostics diagnostics) {
        Map<String, byte[]> result = bundledEntries;
        if (result == null) {
            synchronized (AssetResolver.class) {
                result = bundledEntries;
                if (result == null) {
                    result = unpackBundle(diagnostics);
                    bundledEntries = result;
                }
            }
        }
        return result;
    }

    private static Map<String, byte[]> unpackBundle(PreviewDiagnostics diagnostics) {
        Map<String, byte[]> entries = new HashMap<>();
        byte[] bundleBytes = readBundleBytes(diagnostics);
        if (bundleBytes != null) {
            unzipInto(bundleBytes, entries, diagnostics);
        }
        return entries;
    }

    private static void unzipInto(byte[] bundleBytes, Map<String, byte[]> entries, PreviewDiagnostics diagnostics) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundleBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                zip.transferTo(buffer);
                entries.put(entry.getName(), buffer.toByteArray());
            }
        } catch (IOException e) {
            diagnostics.warn("FX preview: failed to read bundled default assets (" + e.getMessage() + ")");
        }
    }

    static byte[] readBundleBytes(PreviewDiagnostics diagnostics) {
        try (InputStream in = AssetResolver.class.getResourceAsStream(BUNDLE_RESOURCE)) {
            if (in == null) {
                diagnostics.warn("FX preview: " + BUNDLE_RESOURCE + " not found on the plugin's classpath — "
                        + "Photon's own default effect assets (default particle textures, primitive meshes) "
                        + "won't render. Make sure it's included under the plugin's resources.");
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            diagnostics.warn("FX preview: failed to read bundled default assets (" + e.getMessage() + ")");
            return null;
        }
    }

    private static boolean isInstalledDefaults(File root) {
        return root != null && DefaultAssetInstaller.INSTALLED_FOLDER_NAME.equals(root.getName());
    }

    private static byte[] composeJpegTexture(Function<String, byte[]> read, String path) {
        if (!path.endsWith(".png")) {
            return null;
        }
        String base = path.substring(0, path.length() - 4);
        byte[] colorBytes = read.apply(base + JPEG_COLOR_SUFFIX);
        byte[] alphaBytes = read.apply(base + JPEG_ALPHA_SUFFIX);
        if (colorBytes == null && alphaBytes == null) {
            return null;
        }
        try {
            BufferedImage color = colorBytes == null ? null : ImageIO.read(new ByteArrayInputStream(colorBytes));
            BufferedImage alpha = alphaBytes == null ? null : ImageIO.read(new ByteArrayInputStream(alphaBytes));
            if (color == null && alpha == null) {
                return null;
            }
            int w = color != null ? color.getWidth() : alpha.getWidth();
            int h = color != null ? color.getHeight() : alpha.getHeight();
            Raster colorRaster = color == null ? null : color.getRaster();
            Raster alphaRaster = alpha == null ? null : alpha.getRaster();
            int colorBands = colorRaster == null ? 0 : colorRaster.getNumBands();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = 255, g = 255, b = 255, a = 255;
                    if (colorRaster != null) {
                        r = colorRaster.getSample(x, y, 0);
                        g = colorBands >= 3 ? colorRaster.getSample(x, y, 1) : r;
                        b = colorBands >= 3 ? colorRaster.getSample(x, y, 2) : r;
                    }
                    if (alphaRaster != null) {
                        int ax = alphaRaster.getWidth() == w ? x : Math.min(alphaRaster.getWidth() - 1, x * alphaRaster.getWidth() / w);
                        int ay = alphaRaster.getHeight() == h ? y : Math.min(alphaRaster.getHeight() - 1, y * alphaRaster.getHeight() / h);
                        a = alphaRaster.getSample(ax, ay, 0);
                        a = a <= ALPHA_SNAP ? 0 : (a >= 255 - ALPHA_SNAP ? 255 : a);
                    }
                    row[x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                out.setRGB(0, y, w, 1, row, 0, w);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(out, "png", png);
            return png.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public Resolved resolve(String reference) {
        Reference ref = Reference.parse(reference);
        if (ref == null) {
            return null;
        }
        for (File root : assetRoots) {
            byte[] bytes = readFromDisk(root, ref);
            if (bytes == null && isInstalledDefaults(root)) {
                bytes = composeJpegTexture(p -> readFromDisk(root, new Reference(ref.namespace(), p)), ref.path());
            }
            if (bytes != null) {
                return new Resolved(bytes, root);
            }
        }
        Map<String, byte[]> bundle = bundledEntries(diagnostics);
        String bundleKey = ref.namespace() + "/" + ref.path();
        byte[] fromBundle = bundle.get(bundleKey);
        if (fromBundle == null) {
            fromBundle = composedTextures.get(bundleKey);
        }
        if (fromBundle == null) {
            fromBundle = composeJpegTexture(p -> bundle.get(ref.namespace() + "/" + p), ref.path());
            if (fromBundle != null) {
                composedTextures.put(bundleKey, fromBundle);
            }
        }
        if (fromBundle != null) {
            return new Resolved(fromBundle, null);
        }
        byte[] fromPack = readFromPack(PACK_ASSETS_PREFIX + ref.namespace() + "/" + ref.path());
        return fromPack != null ? new Resolved(fromPack, null) : null;
    }

    public LibraryPath parseLibraryPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        Matcher matcher = RESOURCE_PATH.matcher(resourcePath.trim());
        if (!matcher.matches()) {
            return null;
        }
        String typeName = matcher.group(1);
        String value = matcher.group(2);
        String providerPrefix = typeName + ":";
        if (value.startsWith(providerPrefix)) {
            value = value.substring(providerPrefix.length());
        }
        return new LibraryPath(typeName, value);
    }

    public Resolved resolveLibraryFile(String relativePath) {
        for (File candidate : candidateLibraryFiles(relativePath)) {
            byte[] bytes = readBytes(candidate);
            if (bytes != null) {
                return new Resolved(bytes, candidate.getParentFile());
            }
        }
        String cleaned = cleanRelativePath(relativePath);
        byte[] fromPack = cleaned == null ? null : readFromPack(cleaned);
        return fromPack != null ? new Resolved(fromPack, null) : null;
    }

    public List<File> candidateLibraryFiles(String relativePath) {
        String cleaned = cleanRelativePath(relativePath);
        if (cleaned == null) {
            return List.of();
        }
        List<File> candidates = new java.util.ArrayList<>();
        for (File root : assetRoots) {
            if (root == null) {
                continue;
            }
            File parent = root.getParentFile();
            File grandparent = parent == null ? null : parent.getParentFile();
            for (File base : new File[]{root, parent, grandparent}) {
                if (base != null) {
                    candidates.add(new File(base, cleaned));
                }
            }
        }
        return candidates;
    }

    public String describeAssetLookup() {
        return pack == null
                ? "the workspace assets folder and the bundled default assets"
                : "the workspace assets folder, the bundled default assets and the loaded .fxpack";
    }

    public String describeLibraryLookup(String relativePath) {
        StringBuilder sb = new StringBuilder();
        for (File candidate : candidateLibraryFiles(relativePath)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(candidate);
        }
        if (pack != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("the loaded .fxpack");
        }
        return sb.length() == 0 ? "(no asset roots configured)" : sb.toString();
    }

    private byte[] readFromPack(String entryPath) {
        return pack == null ? null : pack.entry(entryPath);
    }

    private byte[] readFromDisk(File root, Reference ref) {
        return root == null ? null : readBytes(new File(new File(root, ref.namespace()), ref.path()));
    }

    private byte[] readBytes(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            diagnostics.warn("FX preview: failed to read " + file + " (" + e.getMessage() + ")");
            return null;
        }
    }

    public record Reference(String namespace, String path) {
        public static Reference parse(String s) {
            if (s == null) {
                return null;
            }
            int colon = s.indexOf(':');
            return colon < 0 ? null : new Reference(s.substring(0, colon), s.substring(colon + 1));
        }
    }

    public record Resolved(byte[] bytes, File fromRoot) {
    }

    public record LibraryPath(String typeName, String value) {
    }
}

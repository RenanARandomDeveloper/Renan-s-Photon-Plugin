package net.renan.photonplugin.menus.resource.preview.forge;

import java.awt.image.BufferedImage;
import java.util.Locale;

public final class ForgeTextures {
    private static final String PREFIX = "photon-forge:generated/";
    public static final String GLOW = PREFIX + "glow";
    private static final String CIRCLE = PREFIX + "circle?radius=";
    private static final int SIZE = 128;

    private ForgeTextures() {
    }

    public static boolean isGenerated(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    public static String circleRef(float radius) {
        return CIRCLE + String.format(Locale.ROOT, "%.4f", radius);
    }

    public static BufferedImage image(String ref) {
        float radius;
        if (GLOW.equals(ref)) {
            radius = 0.5f;
        } else if (ref != null && ref.startsWith(CIRCLE)) {
            try {
                radius = Float.parseFloat(ref.substring(CIRCLE.length()));
            } catch (NumberFormatException e) {
                radius = 0.3f;
            }
        } else {
            radius = 0.3f;
        }
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float u = (x + 0.5f) / SIZE;
                float v = (y + 0.5f) / SIZE;
                float dist = (float) Math.hypot(u - 0.5f, v - 0.5f);
                float a = smoothstep(1f - dist / radius);
                int alpha = Math.max(0, Math.min(255, Math.round(a * 255f)));
                img.setRGB(x, y, (alpha << 24) | 0x00ffffff);
            }
        }
        return img;
    }

    private static float smoothstep(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }
}

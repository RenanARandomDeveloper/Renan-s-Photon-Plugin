package net.renan.photonplugin.menus.resource.preview.common.render;

import java.awt.image.BufferedImage;
import java.util.Locale;

final class KilaTextures {
    private static final int SIZE = 64;
    private static final String PREFIX = "textures/kila/";

    private KilaTextures() {
    }

    static BufferedImage generate(String reference) {
        if (reference == null) {
            return null;
        }
        String lower = reference.toLowerCase(Locale.ROOT).replace('\\', '/');
        int at = lower.indexOf(PREFIX);
        if (at < 0) {
            return null;
        }
        String name = lower.substring(at + PREFIX.length());
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        return switch (name) {
            case "white" -> fill((x, y) -> new float[]{1, 1, 1, 1});
            case "soft_circle" -> fill((x, y) -> new float[]{1, 1, 1, smooth(1f - radius(x, y))});
            case "glow" -> fill((x, y) -> {
                float d = Math.max(0f, 1f - radius(x, y));
                return new float[]{1, 1, 1, d * d * d};
            });
            case "ring" -> fill((x, y) -> {
                float d = Math.abs(radius(x, y) - 0.78f);
                return new float[]{1, 1, 1, smooth(1f - d / 0.12f)};
            });
            case "gradient_linear" -> fill((x, y) -> new float[]{x, x, x, 1});
            case "gradient_radial" -> fill((x, y) -> {
                float v = Math.max(0f, 1f - radius(x, y));
                return new float[]{v, v, v, 1};
            });
            default -> null;
        };
    }

    private static float radius(float x, float y) {
        float dx = (x - 0.5f) * 2f;
        float dy = (y - 0.5f) * 2f;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static BufferedImage fill(Pixel pixel) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float[] c = pixel.at((x + 0.5f) / SIZE, (y + 0.5f) / SIZE);
                int a = Math.round(Math.max(0f, Math.min(1f, c[3])) * 255f);
                int r = Math.round(Math.max(0f, Math.min(1f, c[0])) * 255f);
                int g = Math.round(Math.max(0f, Math.min(1f, c[1])) * 255f);
                int b = Math.round(Math.max(0f, Math.min(1f, c[2])) * 255f);
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private interface Pixel {
        float[] at(float x, float y);
    }
}

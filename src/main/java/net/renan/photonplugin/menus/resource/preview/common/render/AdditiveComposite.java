package net.renan.photonplugin.menus.resource.preview.common.render;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public final class AdditiveComposite implements Composite {
    private final float extraAlpha;

    private AdditiveComposite(float extraAlpha) {
        this.extraAlpha = extraAlpha;
    }

    public static AdditiveComposite withAlpha(float extraAlpha) {
        return new AdditiveComposite(Math.max(0f, Math.min(1f, extraAlpha)));
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(extraAlpha);
    }

    private record Context(float extraAlpha) implements CompositeContext {
        private static int clamp(float v) {
            return v < 0 ? 0 : (v > 255 ? 255 : (int) v);
        }

        @Override
        public void dispose() {
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), dstIn.getWidth());
            int h = Math.min(src.getHeight(), dstIn.getHeight());
            int[] srcRow = new int[w * 4];
            int[] dstRow = new int[w * 4];
            for (int y = 0; y < h; y++) {
                src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, srcRow);
                dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, dstRow);
                for (int x = 0; x < w; x++) {
                    int i = x * 4;
                    float sa = (srcRow[i + 3] / 255f) * extraAlpha;
                    dstRow[i] = clamp(dstRow[i] + srcRow[i] * sa);
                    dstRow[i + 1] = clamp(dstRow[i + 1] + srcRow[i + 1] * sa);
                    dstRow[i + 2] = clamp(dstRow[i + 2] + srcRow[i + 2] * sa);
                    dstRow[i + 3] = clamp(Math.max(dstRow[i + 3], srcRow[i + 3] * sa));
                }
                dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, dstRow);
            }
        }
    }
}

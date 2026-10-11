package net.renan.photonplugin.menus.resource.preview.common.render;

import java.awt.*;
import java.awt.image.*;

final class FastComposite implements Composite {
    private final Mode mode;

    FastComposite(Mode mode) {
        this.mode = mode;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(mode);
    }

    enum Mode {
        ADD,
        MULTIPLY,
        SOFT_ADD
    }

    private record Context(Mode mode) implements CompositeContext {
        private static int op(Mode mode, int d, int s) {
            int o = switch (mode) {
                case ADD -> d + s;
                case MULTIPLY -> (d * s + 127) / 255;
                case SOFT_ADD -> s + d - (d * s + 127) / 255;
            };
            return o > 255 ? 255 : Math.max(o, 0);
        }

        private static SinglePixelPackedSampleModel packed(Raster r) {
            return r.getSampleModel() instanceof SinglePixelPackedSampleModel sm && r.getDataBuffer() instanceof DataBufferInt
                    && sm.getNumBands() >= 3 ? sm : null;
        }

        private static int base(Raster r, SinglePixelPackedSampleModel sm, int y) {
            DataBuffer db = r.getDataBuffer();
            return db.getOffset() + (r.getMinY() + y - r.getSampleModelTranslateY()) * sm.getScanlineStride()
                    + (r.getMinX() - r.getSampleModelTranslateX());
        }

        @Override
        public void dispose() {
        }

        private boolean composeInts(Raster src, Raster dstIn, WritableRaster dstOut, int w, int h) {
            SinglePixelPackedSampleModel ssm = packed(src);
            SinglePixelPackedSampleModel dsm = packed(dstIn);
            SinglePixelPackedSampleModel osm = packed(dstOut);
            if (ssm == null || dsm == null || osm == null) {
                return false;
            }
            int[] sm = ssm.getBitMasks();
            int[] dm = dsm.getBitMasks();
            int[] om = osm.getBitMasks();
            for (int c = 0; c < 3; c++) {
                if (sm[c] != dm[c] || om[c] != dm[c]) {
                    return false;
                }
            }
            int[] sd = ((DataBufferInt) src.getDataBuffer()).getData();
            int[] dd = ((DataBufferInt) dstIn.getDataBuffer()).getData();
            int[] od = ((DataBufferInt) dstOut.getDataBuffer()).getData();
            int m0 = dm[0];
            int m1 = dm[1];
            int m2 = dm[2];
            int sh0 = Integer.numberOfTrailingZeros(m0);
            int sh1 = Integer.numberOfTrailingZeros(m1);
            int sh2 = Integer.numberOfTrailingZeros(m2);
            int colorMask = m0 | m1 | m2;
            for (int y = 0; y < h; y++) {
                int si = base(src, ssm, y);
                int di = base(dstIn, dsm, y);
                int oi = base(dstOut, osm, y);
                for (int x = 0; x < w; x++) {
                    int s = sd[si + x];
                    int d = dd[di + x];
                    int r = op(mode, (d & m0) >>> sh0, (s & m0) >>> sh0);
                    int g = op(mode, (d & m1) >>> sh1, (s & m1) >>> sh1);
                    int b = op(mode, (d & m2) >>> sh2, (s & m2) >>> sh2);
                    od[oi + x] = (d & ~colorMask) | (r << sh0) | (g << sh1) | (b << sh2);
                }
            }
            return true;
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), dstIn.getWidth());
            int h = Math.min(src.getHeight(), dstIn.getHeight());
            if (composeInts(src, dstIn, dstOut, w, h)) {
                return;
            }
            int sBands = src.getNumBands();
            int dBands = dstIn.getNumBands();
            int oBands = dstOut.getNumBands();
            int[] srcRow = new int[w * sBands];
            int[] dstRow = new int[w * dBands];
            int[] outRow = new int[w * oBands];
            for (int y = 0; y < h; y++) {
                src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, srcRow);
                dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, dstRow);
                for (int x = 0; x < w; x++) {
                    int si = x * sBands;
                    int di = x * dBands;
                    int oi = x * oBands;
                    for (int c = 0; c < 3; c++) {
                        int s = srcRow[si + c];
                        int d = dstRow[di + c];
                        outRow[oi + c] = op(mode, d, s);
                    }
                    if (oBands > 3) {
                        outRow[oi + 3] = dBands > 3 ? dstRow[di + 3] : 255;
                    }
                }
                dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, outRow);
            }
        }
    }
}

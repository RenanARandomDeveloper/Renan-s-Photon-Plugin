package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;

import java.awt.*;
import java.awt.image.*;

public final class BlendComposite implements Composite {
    private final BlendMode mode;
    private final Context.Plan[] plans = new Context.Plan[4];

    public BlendComposite(BlendMode mode) {
        this.mode = mode;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(mode, srcColorModel.hasAlpha(), plans);
    }

    private static final class Context implements CompositeContext {
        private static final float[] UNIT = new float[256];
        private static final int F_ZERO = 0, F_ONE = 1, F_SRC_COLOR = 2, F_ONE_MINUS_SRC_COLOR = 3, F_DST_COLOR = 4,
                F_ONE_MINUS_DST_COLOR = 5, F_SRC_ALPHA = 6, F_ONE_MINUS_SRC_ALPHA = 7, F_DST_ALPHA = 8,
                F_ONE_MINUS_DST_ALPHA = 9, F_CONST_ZERO = 10, F_CONST_ONE = 11, F_SATURATE = 12;
        private static final int E_ADD = 0, E_SUBTRACT = 1, E_REVERSE_SUBTRACT = 2, E_MIN = 3, E_MAX = 4;
        private static final boolean ROUND_TRIP_EXACT;

        static {
            for (int i = 0; i < UNIT.length; i++) {
                UNIT[i] = i / 255f;
            }
        }

        static {
            boolean exact = true;
            for (int i = 0; i < 256; i++) {
                exact &= to255(UNIT[i]) == i;
            }
            ROUND_TRIP_EXACT = exact;
        }

        private final boolean blendEnabled;
        private final int srcColor;
        private final int dstColor;
        private final int srcAlpha;
        private final int dstAlpha;
        private final int func;
        private final boolean srcHasAlpha;
        private final Plan[] plans;

        Context(BlendMode mode, boolean srcHasAlpha, Plan[] plans) {
            this.plans = plans;
            this.blendEnabled = mode.enableBlend();
            this.srcColor = code(mode.srcColorFactor());
            this.dstColor = code(mode.dstColorFactor());
            this.srcAlpha = code(mode.srcAlphaFactor());
            this.dstAlpha = code(mode.dstAlphaFactor());
            this.func = switch (mode.blendFunc()) {
                case ADD -> E_ADD;
                case SUBTRACT -> E_SUBTRACT;
                case REVERSE_SUBTRACT -> E_REVERSE_SUBTRACT;
                case MIN -> E_MIN;
                case MAX -> E_MAX;
            };
            this.srcHasAlpha = srcHasAlpha;
        }

        private static int code(BlendMode.Factor f) {
            return switch (f) {
                case ZERO -> F_ZERO;
                case ONE -> F_ONE;
                case SRC_COLOR -> F_SRC_COLOR;
                case ONE_MINUS_SRC_COLOR -> F_ONE_MINUS_SRC_COLOR;
                case DST_COLOR -> F_DST_COLOR;
                case ONE_MINUS_DST_COLOR -> F_ONE_MINUS_DST_COLOR;
                case SRC_ALPHA -> F_SRC_ALPHA;
                case ONE_MINUS_SRC_ALPHA -> F_ONE_MINUS_SRC_ALPHA;
                case DST_ALPHA -> F_DST_ALPHA;
                case ONE_MINUS_DST_ALPHA -> F_ONE_MINUS_DST_ALPHA;
                case CONSTANT_COLOR, CONSTANT_ALPHA -> F_CONST_ZERO;
                case ONE_MINUS_CONSTANT_COLOR, ONE_MINUS_CONSTANT_ALPHA -> F_CONST_ONE;
                case SRC_ALPHA_SATURATE -> F_SATURATE;
            };
        }

        private static float factor(int f, int ch, float sr, float sg, float sb, float sa,
                                    float dr, float dg, float db, float da, boolean color) {
            switch (f) {
                case F_ZERO:
                case F_CONST_ZERO:
                    return 0f;
                case F_ONE:
                case F_CONST_ONE:
                    return 1f;
                case F_SRC_COLOR:
                    return ch == 0 ? sr : ch == 1 ? sg : ch == 2 ? sb : sa;
                case F_ONE_MINUS_SRC_COLOR:
                    return 1 - (ch == 0 ? sr : ch == 1 ? sg : ch == 2 ? sb : sa);
                case F_DST_COLOR:
                    return ch == 0 ? dr : ch == 1 ? dg : ch == 2 ? db : da;
                case F_ONE_MINUS_DST_COLOR:
                    return 1 - (ch == 0 ? dr : ch == 1 ? dg : ch == 2 ? db : da);
                case F_SRC_ALPHA:
                    return sa;
                case F_ONE_MINUS_SRC_ALPHA:
                    return 1 - sa;
                case F_DST_ALPHA:
                    return da;
                case F_ONE_MINUS_DST_ALPHA:
                    return 1 - da;
                default:
                    return ch == 3 ? 1f : (color ? Math.min(sa, 1 - da) : 1f);
            }
        }

        private static int to255(float v) {
            float x = v * 255f;
            if (!(x >= 0.5f)) {
                return 0;
            }
            if (x >= 254.5f) {
                return 255;
            }
            int i = (int) x;
            return x - i >= 0.5f ? i + 1 : i;
        }

        private static float combine(int func, float s, float d, float rawS, float rawD) {
            switch (func) {
                case E_ADD:
                    return s + d;
                case E_SUBTRACT:
                    return s - d;
                case E_REVERSE_SUBTRACT:
                    return d - s;
                case E_MIN:
                    return Math.min(rawS, rawD);
                default:
                    return Math.max(rawS, rawD);
            }
        }

        private static boolean alphaOnly(int f, boolean dstHasAlpha) {
            return switch (f) {
                case F_ZERO, F_ONE, F_SRC_ALPHA, F_ONE_MINUS_SRC_ALPHA, F_CONST_ZERO, F_CONST_ONE -> true;
                case F_DST_ALPHA, F_ONE_MINUS_DST_ALPHA, F_SATURATE -> !dstHasAlpha;
                default -> false;
            };
        }

        private static float[] factorTable(int f, int ch, boolean color) {
            float[] t = new float[256];
            for (int i = 0; i < 256; i++) {
                t[i] = factor(f, ch, 0f, 0f, 0f, UNIT[i], 0f, 0f, 0f, 1f, color);
            }
            return t;
        }

        private static SinglePixelPackedSampleModel packed(Raster r) {
            if (!(r.getSampleModel() instanceof SinglePixelPackedSampleModel sm) || !(r.getDataBuffer() instanceof DataBufferInt)
                    || sm.getNumBands() < 3 || sm.getNumBands() > 4) {
                return null;
            }
            for (int b = 0; b < sm.getNumBands(); b++) {
                if (sm.getSampleSize(b) != 8) {
                    return null;
                }
            }
            return sm;
        }

        private static int base(Raster r, SinglePixelPackedSampleModel sm, int y) {
            DataBuffer db = r.getDataBuffer();
            return db.getOffset() + (r.getMinY() + y - r.getSampleModelTranslateY()) * sm.getScanlineStride()
                    + (r.getMinX() - r.getSampleModelTranslateX());
        }

        private void blend(float sr, float sg, float sb, float sa, float dr, float dg, float db, float da, int[] out) {
            if (!blendEnabled) {
                out[0] = to255(sr);
                out[1] = to255(sg);
                out[2] = to255(sb);
                out[3] = to255(sa);
                return;
            }
            float r = combine(func, sr * factor(srcColor, 0, sr, sg, sb, sa, dr, dg, db, da, true),
                    dr * factor(dstColor, 0, sr, sg, sb, sa, dr, dg, db, da, true), sr, dr);
            float g = combine(func, sg * factor(srcColor, 1, sr, sg, sb, sa, dr, dg, db, da, true),
                    dg * factor(dstColor, 1, sr, sg, sb, sa, dr, dg, db, da, true), sg, dg);
            float b = combine(func, sb * factor(srcColor, 2, sr, sg, sb, sa, dr, dg, db, da, true),
                    db * factor(dstColor, 2, sr, sg, sb, sa, dr, dg, db, da, true), sb, db);
            float a = combine(func, sa * factor(srcAlpha, 3, sr, sg, sb, sa, dr, dg, db, da, false),
                    da * factor(dstAlpha, 3, sr, sg, sb, sa, dr, dg, db, da, false), sa, da);
            out[0] = to255(r);
            out[1] = to255(g);
            out[2] = to255(b);
            out[3] = to255(a);
        }

        private Plan plan(boolean dAlpha, boolean oAlpha) {
            int index = (dAlpha ? 1 : 0) + (oAlpha ? 2 : 0);
            Plan plan = plans.length > index ? plans[index] : null;
            if (plan == null) {
                plan = buildPlan(dAlpha, oAlpha);
                if (plans.length > index) {
                    plans[index] = plan;
                }
            }
            return plan;
        }

        private Plan buildPlan(boolean dAlpha, boolean oAlpha) {
            boolean tabled = !blendEnabled || (alphaOnly(srcColor, dAlpha) && alphaOnly(dstColor, dAlpha)
                    && alphaOnly(srcAlpha, dAlpha) && alphaOnly(dstAlpha, dAlpha));
            if (!tabled || !blendEnabled) {
                return new Plan(tabled, null, null, null, null, false);
            }
            float[] fsc = factorTable(srcColor, 0, true);
            float[] fdc = factorTable(dstColor, 0, true);
            float[] fsa = factorTable(srcAlpha, 3, false);
            float[] fda = factorTable(dstAlpha, 3, false);
            boolean skipClear = ROUND_TRIP_EXACT && func == E_ADD && fsc[0] == 0f && fdc[0] == 1f
                    && (!oAlpha || (fsa[0] == 0f && fda[0] == 1f));
            return new Plan(true, fsc, fdc, fsa, fda, skipClear);
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
            int[] sMask = ssm.getBitMasks();
            int[] dMask = dsm.getBitMasks();
            int[] oMask = osm.getBitMasks();
            boolean sAlpha = sMask.length > 3 && srcHasAlpha;
            boolean dAlpha = dMask.length > 3;
            boolean oAlpha = oMask.length > 3;
            int[] sd = ((DataBufferInt) src.getDataBuffer()).getData();
            int[] dd = ((DataBufferInt) dstIn.getDataBuffer()).getData();
            int[] od = ((DataBufferInt) dstOut.getDataBuffer()).getData();
            int s0 = Integer.numberOfTrailingZeros(sMask[0]);
            int s1 = Integer.numberOfTrailingZeros(sMask[1]);
            int s2 = Integer.numberOfTrailingZeros(sMask[2]);
            int s3 = sAlpha ? Integer.numberOfTrailingZeros(sMask[3]) : 0;
            int d0 = Integer.numberOfTrailingZeros(dMask[0]);
            int d1 = Integer.numberOfTrailingZeros(dMask[1]);
            int d2 = Integer.numberOfTrailingZeros(dMask[2]);
            int d3 = dAlpha ? Integer.numberOfTrailingZeros(dMask[3]) : 0;
            int o0 = Integer.numberOfTrailingZeros(oMask[0]);
            int o1 = Integer.numberOfTrailingZeros(oMask[1]);
            int o2 = Integer.numberOfTrailingZeros(oMask[2]);
            int o3 = oAlpha ? Integer.numberOfTrailingZeros(oMask[3]) : 0;
            int written = oMask[0] | oMask[1] | oMask[2] | (oAlpha ? oMask[3] : 0);
            int sm0 = sMask[0], sm1 = sMask[1], sm2 = sMask[2], sm3 = sAlpha ? sMask[3] : 0;
            int dm0 = dMask[0], dm1 = dMask[1], dm2 = dMask[2], dm3 = dAlpha ? dMask[3] : 0;
            int[] out = new int[4];
            Plan plan = plan(dAlpha, oAlpha);
            boolean tabled = plan.tabled;
            float[] fsc = plan.fsc, fdc = plan.fdc, fsa = plan.fsa, fda = plan.fda;
            boolean skipClear = plan.skipClear && dd == od;
            for (int y = 0; y < h; y++) {
                int si = base(src, ssm, y);
                int di = base(dstIn, dsm, y);
                int oi = base(dstOut, osm, y);
                if (tabled && blendEnabled) {
                    boolean skip = skipClear && di == oi;
                    for (int x = 0; x < w; x++) {
                        int s = sd[si + x];
                        int saI = sAlpha ? (s & sm3) >>> s3 : 255;
                        if (skip && saI == 0) {
                            continue;
                        }
                        int d = dd[di + x];
                        float sr = UNIT[(s & sm0) >>> s0];
                        float sg = UNIT[(s & sm1) >>> s1];
                        float sb = UNIT[(s & sm2) >>> s2];
                        float sa = UNIT[saI];
                        float dr = UNIT[(d & dm0) >>> d0];
                        float dg = UNIT[(d & dm1) >>> d1];
                        float db = UNIT[(d & dm2) >>> d2];
                        float fs = fsc[saI];
                        float fd = fdc[saI];
                        int r = to255(combine(func, sr * fs, dr * fd, sr, dr));
                        int g = to255(combine(func, sg * fs, dg * fd, sg, dg));
                        int b = to255(combine(func, sb * fs, db * fd, sb, db));
                        int packed = (r << o0) | (g << o1) | (b << o2);
                        if (oAlpha) {
                            float da = dAlpha ? UNIT[(d & dm3) >>> d3] : 1f;
                            packed |= to255(combine(func, sa * fsa[saI], da * fda[saI], sa, da)) << o3;
                        }
                        od[oi + x] = (od[oi + x] & ~written) | packed;
                    }
                    continue;
                }
                for (int x = 0; x < w; x++) {
                    int s = sd[si + x];
                    int d = dd[di + x];
                    float sr = UNIT[(s & sm0) >>> s0];
                    float sg = UNIT[(s & sm1) >>> s1];
                    float sb = UNIT[(s & sm2) >>> s2];
                    float sa = sAlpha ? UNIT[(s & sm3) >>> s3] : 1f;
                    float dr = UNIT[(d & dm0) >>> d0];
                    float dg = UNIT[(d & dm1) >>> d1];
                    float db = UNIT[(d & dm2) >>> d2];
                    float da = dAlpha ? UNIT[(d & dm3) >>> d3] : 1f;
                    blend(sr, sg, sb, sa, dr, dg, db, da, out);
                    int packed = (out[0] << o0) | (out[1] << o1) | (out[2] << o2) | (oAlpha ? out[3] << o3 : 0);
                    od[oi + x] = (od[oi + x] & ~written) | packed;
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
            int[] out = new int[4];
            for (int y = 0; y < h; y++) {
                src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, srcRow);
                dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, dstRow);
                for (int x = 0; x < w; x++) {
                    int si = x * sBands;
                    int di = x * dBands;
                    float sr = srcRow[si] / 255f;
                    float sg = srcRow[si + 1] / 255f;
                    float sb = srcRow[si + 2] / 255f;
                    float sa = (sBands > 3 && srcHasAlpha) ? srcRow[si + 3] / 255f : 1f;
                    float dr = dstRow[di] / 255f;
                    float dg = dstRow[di + 1] / 255f;
                    float db = dstRow[di + 2] / 255f;
                    float da = dBands > 3 ? dstRow[di + 3] / 255f : 1f;
                    blend(sr, sg, sb, sa, dr, dg, db, da, out);
                    int oi = x * oBands;
                    outRow[oi] = out[0];
                    outRow[oi + 1] = out[1];
                    outRow[oi + 2] = out[2];
                    if (oBands > 3) {
                        outRow[oi + 3] = out[3];
                    }
                }
                dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, outRow);
            }
        }

        record Plan(boolean tabled, float[] fsc, float[] fdc, float[] fsa, float[] fda, boolean skipClear) {
        }
    }
}

package net.renan.photonplugin.menus.resource.preview.common.sim;

import net.renan.photonplugin.menus.resource.preview.common.render.GroundForecast;
import net.renan.photonplugin.menus.resource.preview.common.render.GroundSampler;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GroundProbe {
    public static final int HORIZON_TICKS = 600;
    public static final int SAMPLE_EVERY_TICKS = 3;
    public static final long BUDGET_NANOS = 600_000_000L;
    public static final int IDLE_TICKS_TO_STOP = 240;
    private static final float GROWTH_EPSILON = 0.02f;

    private final Runnable tickOnce;
    private final BooleanSupplier finished;
    private final Supplier<List<RenderItem>> items;
    private final GroundSampler sampler = new GroundSampler();
    private float widest;
    private int peakTick = -1;
    private int lastGrowthTick;
    private float[] bounds;
    private int tick;
    private long spentNanos;
    private boolean done;

    public GroundProbe(Runnable tickOnce, BooleanSupplier finished, Supplier<List<RenderItem>> items) {
        this.tickOnce = tickOnce;
        this.finished = finished;
        this.items = items;
    }

    public static GroundForecast run(Runnable tickOnce, BooleanSupplier finished, Supplier<List<RenderItem>> items) {
        return new GroundProbe(tickOnce, finished, items).finish();
    }

    public boolean step(long sliceNanos) {
        if (done) {
            return true;
        }
        long start = System.nanoTime();
        long sliceEnd = start + sliceNanos;
        while (!done) {
            advance();
            long now = System.nanoTime();
            if (now > sliceEnd) {
                spentNanos += now - start;
                if (spentNanos > BUDGET_NANOS) {
                    done = true;
                }
                return done;
            }
        }
        spentNanos += System.nanoTime() - start;
        return true;
    }

    public GroundForecast finish() {
        while (!step(Long.MAX_VALUE)) {
        }
        return current();
    }

    public GroundForecast current() {
        return new GroundForecast(widest, peakTick, bounds == null ? null : bounds.clone());
    }

    private void advance() {
        tick++;
        tickOnce.run();
        boolean over = finished.getAsBoolean();
        if (over || tick % SAMPLE_EVERY_TICKS == 0) {
            float reach = sampler.measure(items.get());
            boolean grew = false;
            if (reach > widest) {
                grew = reach > widest * (1f + GROWTH_EPSILON) + 0.01f;
                widest = reach;
                peakTick = tick;
            }
            grew |= union(sampler.bounds());
            if (grew) {
                lastGrowthTick = tick;
            }
        }
        if (over || tick >= HORIZON_TICKS || tick - lastGrowthTick >= IDLE_TICKS_TO_STOP) {
            done = true;
        }
    }

    private boolean union(float[] b) {
        if (b == null) {
            return false;
        }
        if (bounds == null) {
            bounds = b;
            return true;
        }
        boolean grew = false;
        for (int k = 0; k < 3; k++) {
            if (b[k] < bounds[k] - GROWTH_EPSILON) {
                grew = true;
            }
            if (b[k + 3] > bounds[k + 3] + GROWTH_EPSILON) {
                grew = true;
            }
            bounds[k] = Math.min(bounds[k], b[k]);
            bounds[k + 3] = Math.max(bounds[k + 3], b[k + 3]);
        }
        return grew;
    }
}

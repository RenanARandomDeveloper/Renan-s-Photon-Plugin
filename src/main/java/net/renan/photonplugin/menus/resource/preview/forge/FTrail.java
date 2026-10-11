package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.TrailCfg;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.TrailsCfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

final class FTrail extends FParticle {
    private static final int MAX_SMOOTH_STEPS = 256;
    private static final int MAX_COLOR_SLICES = 8;
    final FEmitter emitter;
    final TrailCfg config;

    float r = 1, g = 1, b = 1, a = 1;
    float ro = 1, go = 1, bo = 1, ao = 1;
    int light = FUtil.FULL_BRIGHT;

    boolean dieWhenAllTailsRemoved = true;
    Runnable onUpdate;
    HeadSupplier head;
    Supplier<Float> lifetimeSupplier;
    Supplier<Float> widthMultiplier;
    Function<Float, float[]> colorMultiplier;

    Tails rawTails = new Tails();
    Tails tails = new Tails();

    FTrail(FEmitter emitter, TrailCfg config, Random random) {
        super(random);
        this.emitter = emitter;
        this.config = config;
        this.head = partial -> emitter.transform.position();
        setup();
    }

    static void setup(FParticleEmitter emitter, FTile particle) {
        TrailsCfg s = particle.config.trails;
        if (emitter.particleRandom.nextFloat() < s.ratio) {
            FTrail trail = new FTrail(emitter, s.config, emitter.particleRandom);
            trail.delay = particle.delay + trail.delay;
            trail.head = particle::getWorldPos;
            trail.dieWhenAllTailsRemoved = !s.dieWithParticles;
            trail.onUpdate = () -> {
                if (particle.removed) {
                    trail.removed = true;
                }
            };
            trail.lifetimeSupplier = () -> {
                float time = (float) s.lifetime.get(particle.t, () -> particle.getMemRandom("trails-lifetime"))
                        * particle.lifetime;
                if (s.sizeAffectsLifetime) {
                    time *= FUtil.max(particle.getRealSize(0));
                }
                return time;
            };
            trail.widthMultiplier = () -> s.sizeAffectsWidth ? FUtil.max(particle.getRealSize(0)) : 1f;
            trail.colorMultiplier = partial -> {
                float[] color = {1, 1, 1, 1};
                if (s.inheritParticleColor) {
                    float[] c = particle.getRealColor(partial);
                    for (int i = 0; i < 4; i++) {
                        color[i] *= c[i];
                    }
                }
                if (s.colorOverLifetime != null) {
                    int c = (int) s.colorOverLifetime.get(particle.getT(partial), () -> particle.getMemRandom("trails-color"));
                    color[0] *= FUtil.red(c);
                    color[1] *= FUtil.green(c);
                    color[2] *= FUtil.blue(c);
                    color[3] *= FUtil.alpha(c);
                }
                return color;
            };
            emitter.emitParticle(trail);
        }
    }

    private static Tails generateSmoothPath(Tails vertices, float distance) {
        if (vertices.size <= 2) {
            return vertices;
        }
        Tails smooth = new Tails();
        float minDistanceSq = Math.max(distance, 0.05f) * Math.max(distance, 0.05f);
        Vec3 prevDir = null;
        for (int i = 0; i < vertices.size - 1; i++) {
            Vec3 curr = vertices.position(i);
            Vec3 next = vertices.position(i + 1);
            Vec3 dir = next.copy().sub(curr).normalize();
            vertices.copyTo(smooth, i);
            if (prevDir != null && dir.dot(prevDir) > 0.99f) {
                prevDir = dir;
                continue;
            }
            float dx = next.x - curr.x;
            float dy = next.y - curr.y;
            float dz = next.z - curr.z;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > minDistanceSq) {
                int steps = (int) Math.min(MAX_SMOOTH_STEPS, distSq / minDistanceSq);
                for (int j = 1; j < steps; j++) {
                    float t = j / (float) steps;
                    Vec3 pos = catmullRom(vertices.position(Math.max(i - 1, 0)), curr, next,
                            vertices.position(Math.min(i + 2, vertices.size - 1)), t);
                    smooth.add(pos, vertices.life[i], vertices.cr[i], vertices.cg[i], vertices.cb[i], vertices.ca[i],
                            vertices.width[i]);
                }
            }
            prevDir = dir;
        }
        vertices.copyTo(smooth, vertices.size - 1);
        return smooth;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return new Vec3(
                0.5f * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5f * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                0.5f * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
    }

    private static int slices(int c0, int c1) {
        int diff = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            diff = Math.max(diff, Math.abs(((c0 >>> shift) & 0xff) - ((c1 >>> shift) & 0xff)));
        }
        return Math.max(1, Math.min(MAX_COLOR_SLICES, (diff + 15) / 16));
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float f) {
        return new Vec3(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f);
    }

    private static int blend(int c0, int c1, float f) {
        int a = snap((int) (((c0 >>> 24) & 0xff) + ((((c1 >>> 24) & 0xff) - ((c0 >>> 24) & 0xff)) * f)));
        int r = snap((int) (((c0 >> 16) & 0xff) + ((((c1 >> 16) & 0xff) - ((c0 >> 16) & 0xff)) * f)));
        int g = snap((int) (((c0 >> 8) & 0xff) + ((((c1 >> 8) & 0xff) - ((c0 >> 8) & 0xff)) * f)));
        int b = snap((int) ((c0 & 0xff) + (((c1 & 0xff) - (c0 & 0xff)) * f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int snap(int v) {
        return Math.min(255, (v + 4) & ~7);
    }

    private void setup() {
        delay = config.startDelay;
        lifetimeSupplier = () -> (float) config.time;
        update();
        updateOrigin();
        tails.clear();
        rawTails.clear();
    }

    @Override
    boolean isAlive() {
        if (removed) {
            return dieWhenAllTailsRemoved && !tails.isEmpty();
        }
        return true;
    }

    float getT(float partial) {
        return emitter.getT(partial);
    }

    float[] getRealColor(float partial) {
        float[] e = emitter.rgba();
        float aa = FUtil.lerp(partial, ao, a);
        float rr = FUtil.lerp(partial, ro, r);
        float gg = FUtil.lerp(partial, go, g);
        float bb = FUtil.lerp(partial, bo, b);
        if (colorMultiplier != null) {
            float[] c = colorMultiplier.apply(partial);
            rr *= c[0];
            gg *= c[1];
            bb *= c[2];
            aa *= c[3];
        }
        return new float[]{e[0] * rr, e[1] * gg, e[2] * bb, e[3] * aa};
    }

    int getRealLight(float partial) {
        if (config.renderer.bloomEffect) {
            return FUtil.FULL_BRIGHT;
        }
        if (config.lights.enable) {
            float t = getT(partial);
            int sky = (int) config.lights.skyLight.get(t, () -> getMemRandom("sky-light"));
            int block = (int) config.lights.blockLight.get(t, () -> getMemRandom("block-light"));
            return sky << 20 | block << 4;
        }
        return light;
    }

    @Override
    void tick() {
        if (delay > 0) {
            delay--;
            return;
        }
        updateOrigin();
        update();
    }

    private void updateOrigin() {
        ro = r;
        go = g;
        bo = b;
        ao = a;
    }

    private void update() {
        boolean changed = updateTails();
        if (changed) {
            updateRawTailsProperties();
            tails = config.smoothInterpolation ? generateSmoothPath(rawTails, config.minVertexDistance) : rawTails;
        }
        if (!config.lights.enable && !config.renderer.bloomEffect) {
            light = FUtil.FULL_BRIGHT;
        }
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    private boolean updateTails() {
        boolean changed = false;
        for (int i = 0; i < rawTails.size; i++) {
            rawTails.life[i] -= 1;
        }
        Tails fresh = new Tails();
        for (int i = 0; i < rawTails.size; i++) {
            if (rawTails.life[i] > 0) {
                rawTails.copyTo(fresh, i);
            } else {
                changed = true;
            }
        }
        rawTails = fresh;

        if (!removed) {
            Vec3 headPos = head.get(0);
            boolean shouldAdd = true;
            if (!rawTails.isEmpty()) {
                int last = rawTails.size - 1;
                float dx = headPos.x - rawTails.px[last];
                float dy = headPos.y - rawTails.py[last];
                float dz = headPos.z - rawTails.pz[last];
                if (dx * dx + dy * dy + dz * dz < config.minVertexDistance * config.minVertexDistance) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                rawTails.add(headPos, lifetimeSupplier.get(), 1, 1, 1, 1, 0.2f);
                changed = true;
            }
        }
        return changed;
    }

    private void updateRawTailsProperties() {
        if (rawTails.size > 1) {
            for (int i = 0; i < rawTails.size; i++) {
                updateRawTrailProperties(i, rawTails.size);
            }
        } else if (rawTails.size == 1) {
            updateRawTrailProperties(0, 2);
        }
    }

    private void updateRawTrailProperties(int index, int size) {
        float t = ((float) index) / (size - 1);
        int color = (int) config.colorOverTrail.get(t, () -> getMemRandom("trails-colorOverTrail"));
        rawTails.cr[index] = FUtil.red(color);
        rawTails.cg[index] = FUtil.green(color);
        rawTails.cb[index] = FUtil.blue(color);
        rawTails.ca[index] = FUtil.alpha(color);
        float width = (float) config.widthOverTrail.get(t, () -> getMemRandom("trails-widthOverTrail"));
        if (widthMultiplier != null) {
            width *= widthMultiplier.get();
        }
        rawTails.width[index] = width;
    }

    private float[] getUVs(int index, int size, float partial) {
        float u0, u1, v0, v1;
        if (config.uvMode == ForgeConfig.UvMode.Stretch) {
            u0 = index / (size - 1f);
            u1 = (index + 1f) / (size - 1f);
            v0 = 0;
            v1 = 1;
            if (config.uvAnimation.enable) {
                UvRect uvs = animation(partial);
                float x = uvs.u0();
                float y = uvs.v0();
                float w = uvs.u1() - uvs.u0();
                float h = uvs.v1() - uvs.v0();
                u0 = x + w * u0;
                v0 = y + h * v0;
                u1 = x + w * u1;
                v1 = y + h * v1;
            }
        } else if (config.uvAnimation.enable) {
            UvRect uvs = animation(partial);
            u0 = uvs.u0();
            v0 = uvs.v0();
            u1 = uvs.u1();
            v1 = uvs.v1();
        } else {
            u0 = 0;
            v0 = 0;
            u1 = 1;
            v1 = 1;
        }
        return new float[]{u0, v0, u1, v1};
    }

    private UvRect animation(float partial) {
        ForgeConfig.UvAnimationCfg c = config.uvAnimation;
        float t = getT(partial);
        int tilesA = (int) c.tiles.a;
        int tilesB = (int) c.tiles.b;
        float cellU = 1f / tilesA;
        float cellV = 1f / tilesB;
        float frame = (float) c.startFrame.get(t, () -> getMemRandom("startFrame"));
        frame += c.cycle * (float) c.frameOverTime.get(t, () -> getMemRandom("frameOverTime"));
        float u0, v0;
        if (c.animation == ForgeConfig.UvAnimationMode.WholeSheet) {
            u0 = (int) (frame % tilesA) * cellU;
            v0 = (int) (frame / tilesA) * cellV;
        } else {
            u0 = (int) (frame % tilesA) * cellU;
            v0 = (int) (getMemRandom("randomRow") * tilesB) * cellV;
        }
        if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(cellU) || !Float.isFinite(cellV)) {
            return UvRect.FULL;
        }
        return new UvRect(u0, v0, u0 + cellU, v0 + cellV);
    }

    @Override
    Object renderKey() {
        return config;
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !emitter.isVisible()) {
            return;
        }
        IMaterial material = config.material.material;
        if (material == null) {
            return;
        }
        float partial = ctx.partial;
        float[] color = getRealColor(partial);
        List<Pair> pairs = buildPairs(ctx, color, partial);
        if (pairs.size() < 2) {
            return;
        }
        var settings = FCtx.settings(config.renderer, config.material);
        for (int i = 0; i + 1 < pairs.size(); i++) {
            Pair p0 = pairs.get(i);
            Pair p1 = pairs.get(i + 1);
            Vec3 a = p0.up.copy().add(p0.down).mul(0.5f);
            Vec3 b = p1.up.copy().add(p1.down).mul(0.5f);
            Vec3 sideA = p0.up.copy().sub(p0.down);
            Vec3 sideB = p1.up.copy().sub(p1.down);

            int slices = slices(p0.color, p1.color);
            for (int k = 0; k < slices; k++) {
                float f0 = k / (float) slices;
                float f1 = (k + 1) / (float) slices;
                Vec3 sa = lerp(a, b, f0);
                Vec3 sb = lerp(a, b, f1);
                Vec3 ba = lerp(sideA, sideB, f0);
                Vec3 bb = lerp(sideA, sideB, f1);
                int c = blend(p0.color, p1.color, (f0 + f1) * 0.5f);
                float u0 = p0.u + (p1.u - p0.u) * f0;
                float u1 = p0.u + (p1.u - p0.u) * f1;
                float depth = ctx.distance(sa.copy().add(sb).mul(0.5f));
                ctx.items.add(new RenderItem.RibbonSegmentItem(sa, sb, ba.length(), bb.length(), c, c, material,
                        config.material.blendMode, new UvRect(u0, p0.v0, u1, p0.v1), settings, ba, bb, depth));
            }
        }
    }

    private List<Pair> buildPairs(FCtx ctx, float[] color, float partial) {
        List<Pair> pairs = new ArrayList<>();
        Vec3 camPos = ctx.camPos;

        Vec3 headPos = head.get(partial);
        boolean pushHead = true;
        int tailSize = tails.size;
        if (tailSize > 0) {
            int last = tailSize - 1;
            if (tails.px[last] == headPos.x && tails.py[last] == headPos.y && tails.pz[last] == headPos.z) {
                pushHead = false;
            }
        }
        if (pushHead) {
            if (tailSize > 1) {
                int last = tailSize - 1;
                tails.add(headPos, tails.life[last], tails.cr[last], tails.cg[last], tails.cb[last], tails.ca[last],
                        tails.width[last]);
            } else {
                tails.add(headPos, 100, 1, 1, 1, 1, 0.2f);
            }
        }
        try {
            int size = tails.size;
            Vec3 lastNormal = null;
            for (int i = 0; i < size - 1; i++) {
                if (tails.life[i] - partial <= 0 || tails.life[i + 1] - partial <= 0) {
                    continue;
                }
                Vec3 curr = tails.position(i);
                Vec3 next = tails.position(i + 1);
                Vec3 vec = next.copy().sub(curr);
                Vec3 toTail = curr.copy().sub(camPos);
                Vec3 normal = vec.cross(toTail).normalize();
                if (lastNormal == null) {
                    lastNormal = normal;
                }
                Vec3 avg = lastNormal.copy().add(normal).mul(0.5f);
                Vec3 up = curr.copy().add(avg.copy().mul(tails.width[i]));
                Vec3 down = curr.copy().add(avg.copy().mul(-tails.width[i]));
                int argb = FUtil.argb(color[0] * tails.cr[i], color[1] * tails.cg[i], color[2] * tails.cb[i],
                        color[3] * tails.ca[i]);
                float[] uv = getUVs(i, size, partial);
                pairs.add(new Pair(up, down, argb, uv[0], uv[1], uv[3]));
                lastNormal = normal;
            }

            int headIndex = size - 1;
            if (headIndex > 0 && lastNormal != null) {
                Vec3 pos = tails.position(headIndex);
                Vec3 up = pos.copy().add(lastNormal.copy().mul(tails.width[headIndex]));
                Vec3 down = pos.copy().add(lastNormal.copy().mul(-tails.width[headIndex]));
                int argb = FUtil.argb(color[0] * tails.cr[headIndex], color[1] * tails.cg[headIndex],
                        color[2] * tails.cb[headIndex], color[3] * tails.ca[headIndex]);
                float[] uv = getUVs(headIndex - 1, size, partial);
                pairs.add(new Pair(up, down, argb, uv[2], uv[1], uv[3]));
            }
        } finally {
            if (pushHead) {
                tails.removeLast();
            }
        }
        return pairs;
    }

    interface HeadSupplier {
        Vec3 get(float partial);
    }

    static final class Tails {
        int size;
        float[] px = new float[16], py = new float[16], pz = new float[16];
        float[] cr = new float[16], cg = new float[16], cb = new float[16], ca = new float[16];
        float[] width = new float[16];
        float[] life = new float[16];

        void clear() {
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void ensure() {
            if (size >= px.length) {
                int n = px.length * 2;
                px = Arrays.copyOf(px, n);
                py = Arrays.copyOf(py, n);
                pz = Arrays.copyOf(pz, n);
                cr = Arrays.copyOf(cr, n);
                cg = Arrays.copyOf(cg, n);
                cb = Arrays.copyOf(cb, n);
                ca = Arrays.copyOf(ca, n);
                width = Arrays.copyOf(width, n);
                life = Arrays.copyOf(life, n);
            }
        }

        void add(Vec3 pos, float lifeTime, float r, float g, float b, float a, float w) {
            ensure();
            px[size] = pos.x;
            py[size] = pos.y;
            pz[size] = pos.z;
            cr[size] = r;
            cg[size] = g;
            cb[size] = b;
            ca[size] = a;
            width[size] = w;
            life[size] = lifeTime;
            size++;
        }

        void copyTo(Tails dest, int i) {
            dest.ensure();
            int d = dest.size;
            dest.px[d] = px[i];
            dest.py[d] = py[i];
            dest.pz[d] = pz[i];
            dest.cr[d] = cr[i];
            dest.cg[d] = cg[i];
            dest.cb[d] = cb[i];
            dest.ca[d] = ca[i];
            dest.width[d] = width[i];
            dest.life[d] = life[i];
            dest.size++;
        }

        Vec3 position(int i) {
            return new Vec3(px[i], py[i], pz[i]);
        }

        void removeLast() {
            if (size > 0) {
                size--;
            }
        }
    }

    private record Pair(Vec3 up, Vec3 down, int color, float u, float v0, float v1) {
    }
}

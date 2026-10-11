package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.neo.model.TrailConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class TrailEmitterSim {
    private static final float PT = 0f;
    private static final int MAX_COLOR_STEPS = 4;

    private static final int MAX_RIBBON_VERTICES = 256;

    private final TrailConfig authored;
    private final java.util.Map<String, Object> overrides = new java.util.HashMap<>();
    private final MemRandom mem;
    private final boolean standalone;
    private final Supplier<Vec3> headPosition;
    private final List<Tail> rawTails = new ArrayList<>();
    private TrailConfig config;
    private Supplier<Float> lifetimeSupplier;
    private Supplier<Float> widthMultiplier;
    private Supplier<float[]> colorMultiplier;
    private IntSupplier emitterColor = () -> -1;
    private DoubleSupplier emitterT = () -> 0;
    private Runnable onUpdate;
    private boolean dieWhenAllTailsRemoved = true;
    private boolean removed;
    private float delay;
    private float age;
    private SceneNode node;
    private List<Tail> tails = rawTails;

    public TrailEmitterSim(TrailConfig config, Transform sceneTransform, Random random) {
        this.config = config;
        this.authored = config;
        this.mem = new MemRandom(random);
        this.standalone = true;
        this.headPosition = () -> sceneTransform.position().copy();
        this.lifetimeSupplier = () -> (float) config.time;
        this.delay = config.startDelay;
        this.emitterT = this::standaloneT;
    }

    public TrailEmitterSim(TrailConfig config, Random random, Supplier<Vec3> headPosition) {
        this.config = config;
        this.authored = config;
        this.mem = new MemRandom(random);
        this.standalone = false;
        this.headPosition = headPosition;
        this.lifetimeSupplier = () -> (float) config.time;
        this.delay = config.startDelay;
    }

    private static List<Tail> generateSmoothPath(List<Tail> vertices, float distance) {
        if (vertices.size() <= 2) {
            return vertices;
        }
        List<Tail> smooth = new ArrayList<>();
        float minDistanceSq = Math.max(distance, 0.05f) * Math.max(distance, 0.05f);
        Vec3 prevDir = null;
        int last = vertices.size() - 1;
        for (int i = 0; i < last; i++) {
            Tail currTail = vertices.get(i);
            Tail nextTail = vertices.get(i + 1);
            Vec3 curr = currTail.position;
            Vec3 next = nextTail.position;
            Vec3 dir = new Vec3(next).sub(curr).normalize();
            smooth.add(currTail.copy());

            if (prevDir != null && dir.dot(prevDir) > 0.99f) {
                prevDir = dir;
                continue;
            }
            float distSq = new Vec3(curr).sub(next).lengthSquared();
            if (distSq > minDistanceSq) {
                int steps = (int) (distSq / minDistanceSq);
                for (int j = 1; j < steps; j++) {
                    float t = j / (float) steps;
                    Vec3 interp = catmullRom(vertices.get(Math.max(i - 1, 0)).position, curr, next,
                            vertices.get(Math.min(i + 2, last)).position, t);
                    smooth.add(new Tail(interp,
                            lerp(t, currTail.life, nextTail.life),
                            lerp(t, currTail.r, nextTail.r), lerp(t, currTail.g, nextTail.g),
                            lerp(t, currTail.b, nextTail.b), lerp(t, currTail.a, nextTail.a),
                            lerp(t, currTail.width, nextTail.width)));
                }
            }
            prevDir = dir;
        }
        smooth.add(vertices.get(last).copy());
        return smooth;
    }

    private static Vec3 lerpVec(Vec3 a, Vec3 b, float t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return new Vec3(
                0.5f * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5f * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                0.5f * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
    }

    private static int packColor(float[] real, float r, float g, float b, float a) {
        return ColorMath.pack(channel(real[3] * a), channel(real[0] * r), channel(real[1] * g), channel(real[2] * b));
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    public void setLifetimeSupplier(Supplier<Float> supplier) {
        this.lifetimeSupplier = supplier;
    }

    public void setWidthMultiplier(Supplier<Float> supplier) {
        this.widthMultiplier = supplier;
    }

    public void setColorMultiplier(Supplier<float[]> supplier) {
        this.colorMultiplier = supplier;
    }

    public void setEmitterColor(IntSupplier supplier) {
        this.emitterColor = supplier;
    }

    public void setEmitterT(DoubleSupplier supplier) {
        this.emitterT = supplier;
    }

    public void setDieWhenAllTailsRemoved(boolean value) {
        this.dieWhenAllTailsRemoved = value;
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void setDelay(float delay) {
        this.delay = delay;
    }

    public float delay() {
        return delay;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public SceneNode node() {
        return node;
    }

    public void setNode(SceneNode node) {
        this.node = node;
    }

    public boolean isVisible() {
        return node == null || node.isVisible();
    }

    public void setOverride(String path, Object value) {
        if (!java.util.Objects.equals(overrides.put(path, value), value)) {
            config = ConfigOverrides.apply(authored, overrides);
        }
    }

    public TrailConfig config() {
        return config;
    }

    public boolean isAlive() {
        if (removed) {
            return dieWhenAllTailsRemoved && !tails.isEmpty();
        }
        return true;
    }

    public boolean isFinishedForGood() {
        return standalone && !config.looping && config.duration > 0 && age > config.duration && !isAlive();
    }

    private double standaloneT() {
        if (config.duration <= 0) {
            return 0;
        }
        return config.looping ? (age % config.duration) / config.duration : Math.min(age / config.duration, 1.0);
    }

    public void tick(float dt) {
        updateTick(dt);
        if (standalone) {
            age += dt;
            if (!removed && !config.looping && config.duration > 0 && age >= config.duration) {
                removed = true;
            }
        }
    }

    private void updateTick(float dt) {
        if (delay > 0) {
            delay -= dt;
            return;
        }
        update(dt);
    }

    private void update(float dt) {
        updateTails(dt);
        updateRawTailsProperties();
        tails = config.smoothInterpolation ? generateSmoothPath(rawTails, config.minVertexDistance) : rawTails;
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    private void updateTails(float dt) {
        for (Tail tail : rawTails) {
            tail.life -= dt;
        }
        rawTails.removeIf(tail -> tail.life < 0);

        if (!removed) {
            Vec3 headPos = headPosition.get();
            boolean shouldAdd = true;
            if (!rawTails.isEmpty()) {
                Vec3 last = rawTails.get(rawTails.size() - 1).position;
                float minVertexDistance = config.minVertexDistance;
                if (new Vec3(headPos).sub(last).lengthSquared() < minVertexDistance * minVertexDistance) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                rawTails.add(new Tail(headPos, lifetimeSupplier.get(), 1, 1, 1, 1, 0.2f));
            } else if (rawTails.size() == 1) {
                Tail only = rawTails.get(0);
                only.position.set(headPos);
                only.life = lifetimeSupplier.get();
            }
        }
    }

    private void updateRawTailsProperties() {
        int size = rawTails.size();
        if (size > 1) {
            for (int i = 0; i < size; i++) {
                updateRawTrailProperties(i, size);
            }
        } else if (size == 1) {
            updateRawTrailProperties(0, 2);
        }
    }

    private void updateRawTrailProperties(int index, int size) {
        Tail tail = rawTails.get(index);
        float t = ((float) index) / (size - 1);

        int color = (int) Math.round(config.colorOverTrail.get(t, mem.of("trails-colorOverTrail")));
        tail.r = ColorMath.red(color) / 255f;
        tail.g = ColorMath.green(color) / 255f;
        tail.b = ColorMath.blue(color) / 255f;
        tail.a = ColorMath.alpha(color) / 255f;

        float width = (float) config.widthOverTrail.get(t, mem.of("trails-widthOverTrail"));
        if (widthMultiplier != null) {
            width *= widthMultiplier.get();
        }
        tail.width = width;
    }

    private UvRect uvs(int tailIndex, int size) {
        boolean animated = config.uvAnimation != null && config.uvAnimation.enable();
        if (config.uvMode == TrailConfig.UvMode.STRETCH) {
            float u0 = tailIndex / (size - 1f);
            float u1 = (tailIndex + 1f) / (size - 1f);
            float v0 = 0;
            float v1 = 1;
            if (animated) {
                UvRect cell = config.uvAnimation.getUvs(emitterT.getAsDouble(), mem);
                float x = cell.u0();
                float y = cell.v0();
                float w = cell.u1() - cell.u0();
                float h = cell.v1() - cell.v0();
                u0 = x + w * u0;
                v0 = y + h * v0;
                u1 = x + w * u1;
                v1 = y + h * v1;
            }
            return new UvRect(u0, v0, u1, v1);
        }
        return animated ? config.uvAnimation.getUvs(emitterT.getAsDouble(), mem) : UvRect.FULL;
    }

    private float[] realColor() {
        int emitter = emitterColor.getAsInt();
        float[] c = {ColorMath.red(emitter) / 255f, ColorMath.green(emitter) / 255f,
                ColorMath.blue(emitter) / 255f, ColorMath.alpha(emitter) / 255f};
        if (colorMultiplier != null) {
            float[] m = colorMultiplier.get();
            c[0] *= m[0];
            c[1] *= m[1];
            c[2] *= m[2];
            c[3] *= m[3];
        }
        return c;
    }

    public List<Segment> buildSegments(Vec3 cameraPos) {
        List<Segment> out = new ArrayList<>();
        if (delay > 0 || tails.isEmpty()) {
            return out;
        }
        List<Tail> list = new ArrayList<>(tails);
        Vec3 headPos = headPosition.get();
        boolean pushHead = true;
        Vec3 lastPos = list.get(list.size() - 1).position;
        if (lastPos.x == headPos.x && lastPos.y == headPos.y && lastPos.z == headPos.z) {
            pushHead = false;
        }
        if (pushHead) {
            Tail oldest = list.get(0);
            list.add(new Tail(new Vec3(headPos), 100, oldest.r, oldest.g, oldest.b, oldest.a, oldest.width));
        }
        int size = list.size();
        if (size < 2) {
            return out;
        }

        float lerpDur = 0;
        float t = 0;
        if (rawTails.size() > 1) {
            lerpDur = rawTails.get(1).life - rawTails.get(0).life;
            t = 1 - (rawTails.get(0).life + 1 - PT) / lerpDur;
        }

        float[] real = realColor();
        List<Vertex> vertices = new ArrayList<>();
        Vec3 lastNormal = null;
        for (int i = 0; i < size - 1; i++) {
            Tail tail = list.get(i);
            Tail nextTail = list.get(i + 1);
            if (tail.life - PT < 0f && nextTail.life - PT < 0) {
                continue;
            }
            float currT = tail.life / lerpDur;
            float nextT = nextTail.life / lerpDur;
            if (nextT < t) {
                continue;
            }
            Vec3 pos = new Vec3(tail.position);
            Vec3 next = nextTail.position;
            if (lerpDur > 0 && currT <= t && t <= nextT) {
                pos.lerp(next, (t - currT) / (nextT - currT));
            }
            Vec3 vec = new Vec3(next).sub(pos);
            Vec3 toTail = new Vec3(pos).sub(cameraPos);
            Vec3 normal = Vec3.cross(vec, toTail).normalize();
            if (lastNormal == null) {
                lastNormal = normal;
            }
            Vec3 avg = new Vec3(lastNormal).add(normal).mul(0.5f);
            UvRect uv = uvs(i, size);
            vertices.add(new Vertex(pos, avg, tail.width, packColor(real, tail.r, tail.g, tail.b, tail.a),
                    uv.u0(), uv.v0(), uv.v1()));
            lastNormal = normal;
        }

        int headIndex = size - 1;
        if (headIndex > 0 && lastNormal != null) {
            Tail head = list.get(headIndex);
            UvRect uv = uvs(headIndex - 1, size);
            vertices.add(new Vertex(new Vec3(head.position), new Vec3(lastNormal), head.width,
                    packColor(real, head.r, head.g, head.b, head.a), uv.u1(), uv.v0(), uv.v1()));
        }

        if (vertices.size() > MAX_RIBBON_VERTICES) {
            List<Vertex> kept = new ArrayList<>(MAX_RIBBON_VERTICES);
            for (int k = 0; k < MAX_RIBBON_VERTICES; k++) {
                kept.add(vertices.get(Math.round(k * (vertices.size() - 1f) / (MAX_RIBBON_VERTICES - 1))));
            }
            vertices = kept;
        }
        for (int k = 0; k + 1 < vertices.size(); k++) {
            Vertex a = vertices.get(k);
            Vertex b = vertices.get(k + 1);
            if (a.side().length() < 1e-5f || b.side().length() < 1e-5f) {
                continue;
            }

            int delta = Math.max(Math.max(Math.abs(ColorMath.alpha(a.color()) - ColorMath.alpha(b.color())),
                            Math.abs(ColorMath.red(a.color()) - ColorMath.red(b.color()))),
                    Math.max(Math.abs(ColorMath.green(a.color()) - ColorMath.green(b.color())),
                            Math.abs(ColorMath.blue(a.color()) - ColorMath.blue(b.color()))));
            int steps = Math.max(1, Math.min(MAX_COLOR_STEPS, (delta + 31) / 32));
            for (int s = 0; s < steps; s++) {
                float t0 = s / (float) steps;
                float t1 = (s + 1) / (float) steps;
                Vec3 side0 = lerpVec(a.side(), b.side(), t0);
                Vec3 side1 = lerpVec(a.side(), b.side(), t1);
                float len0 = side0.length();
                float len1 = side1.length();
                if (len0 < 1e-5f || len1 < 1e-5f) {
                    continue;
                }

                float w0 = 2f * (a.width() + (b.width() - a.width()) * t0) * len0;
                float w1 = 2f * (a.width() + (b.width() - a.width()) * t1) * len1;
                int color = ColorMath.blendArgb(a.color(), b.color(), (t0 + t1) * 0.5f);
                out.add(new Segment(lerpVec(a.position(), b.position(), t0), lerpVec(a.position(), b.position(), t1),
                        side0.normalize(), side1.normalize(), w0, w1, color, color,
                        new UvRect(a.u() + (b.u() - a.u()) * t0, a.v0(),
                                a.u() + (b.u() - a.u()) * t1, a.v1())));
            }
        }
        return out;
    }

    public record Segment(Vec3 a, Vec3 b, Vec3 sideA, Vec3 sideB, float widthA, float widthB, int colorA,
                          int colorB, UvRect uv) {
    }

    private record Vertex(Vec3 position, Vec3 side, float width, int color, float u, float v0, float v1) {
    }

    private static final class Tail {
        final Vec3 position;
        float life;
        float r;
        float g;
        float b;
        float a;
        float width;

        Tail(Vec3 position, float life, float r, float g, float b, float a, float width) {
            this.position = new Vec3(position);
            this.life = life;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.width = width;
        }

        Tail copy() {
            return new Tail(position, life, r, g, b, a, width);
        }
    }
}

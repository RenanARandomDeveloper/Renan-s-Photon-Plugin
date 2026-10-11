package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;
import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.AraTrailConfig;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AraTrailSim {
    private final AraTrailConfig authored;
    private final Map<String, Object> overrides = new HashMap<>();
    private final SceneNode node;
    private final Function<String, SceneNode> customSpaceResolver;
    private final Random random;
    private final Map<String, Float> memRandom = new HashMap<>();
    private final List<Point> points = new ArrayList<>();
    private AraTrailConfig config;
    private Vec3 velocity = new Vec3(0, 0, 0);
    private Vec3 prevPosition;
    private float accumTime;
    private float age;
    private boolean removed;

    private Supplier<Vec3> worldPositionSupplier;
    private Supplier<Vec3> worldUpSupplier;
    private Supplier<Vec3> worldForwardSupplier;
    private Supplier<Vec3> worldRightSupplier;

    private Supplier<Float> lifetimeSupplier;
    private Supplier<Float> thicknessMultiplierSupplier;

    private Supplier<float[]> colorMultiplierSupplier;
    private DoubleSupplier emitterTSupplier;
    private boolean dieWhenAllTailsRemoved = true;
    private Runnable onUpdate;

    private float lifeTime;
    private float thicknessMultiplier = 1f;
    private float[] colorMultiplier = {1f, 1f, 1f, 1f};

    public AraTrailSim(AraTrailConfig config, SceneNode node, Function<String, SceneNode> customSpaceResolver,
                       Random random) {
        this.config = config;
        this.authored = config;
        this.node = node;
        this.customSpaceResolver = customSpaceResolver;
        this.random = random;
        this.lifeTime = config.time;
        warmup();
        prevPosition = worldPosition();
    }

    public AraTrailSim(AraTrailConfig config, SceneNode node, Function<String, SceneNode> customSpaceResolver,
                       Random random, Supplier<Vec3> position, Supplier<Vec3> up, Supplier<Vec3> forward,
                       Supplier<Vec3> right) {
        this.config = config;
        this.authored = config;
        this.node = node;
        this.customSpaceResolver = customSpaceResolver;
        this.random = random;
        this.worldPositionSupplier = position;
        this.worldUpSupplier = up;
        this.worldForwardSupplier = forward;
        this.worldRightSupplier = right;
        this.lifeTime = config.time;
        prevPosition = worldPosition();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t2 * t);
    }

    private static Vec3 catmullRom3(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        return new Vec3(catmullRom(p0.x, p1.x, p2.x, p3.x, t), catmullRom(p0.y, p1.y, p2.y, p3.y, t),
                catmullRom(p0.z, p1.z, p2.z, p3.z, t));
    }

    private static int catmullRomColor(int c0, int c1, int c2, int c3, float t) {
        int a = Math.round(catmullRom(ColorMath.alpha(c0), ColorMath.alpha(c1), ColorMath.alpha(c2), ColorMath.alpha(c3), t));
        int r = Math.round(catmullRom(ColorMath.red(c0), ColorMath.red(c1), ColorMath.red(c2), ColorMath.red(c3), t));
        int g = Math.round(catmullRom(ColorMath.green(c0), ColorMath.green(c1), ColorMath.green(c2), ColorMath.green(c3), t));
        int b = Math.round(catmullRom(ColorMath.blue(c0), ColorMath.blue(c1), ColorMath.blue(c2), ColorMath.blue(c3), t));
        return ColorMath.pack(clampByte(a), clampByte(r), clampByte(g), clampByte(b));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public void setLifetimeSupplier(Supplier<Float> supplier) {
        this.lifetimeSupplier = supplier;
    }

    public void setThicknessMultiplierSupplier(Supplier<Float> supplier) {
        this.thicknessMultiplierSupplier = supplier;
    }

    public void setColorMultiplierSupplier(Supplier<float[]> supplier) {
        this.colorMultiplierSupplier = supplier;
    }

    public void setEmitterTSupplier(DoubleSupplier supplier) {
        this.emitterTSupplier = supplier;
    }

    public void setDieWhenAllTailsRemoved(boolean value) {
        this.dieWhenAllTailsRemoved = value;
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public boolean isRemoved() {
        return removed;
    }

    private void refreshDriverData() {
        if (colorMultiplierSupplier != null) {
            colorMultiplier = colorMultiplierSupplier.get();
        }
        if (thicknessMultiplierSupplier != null) {
            thicknessMultiplier = thicknessMultiplierSupplier.get();
        }
        lifeTime = lifetimeSupplier == null ? config.time : lifetimeSupplier.get();
    }

    private int applyColorMultiplier(int argb) {
        if (colorMultiplierSupplier == null) {
            return argb;
        }
        return ColorMath.pack(
                clampByte(Math.round(ColorMath.alpha(argb) * colorMultiplier[3])),
                clampByte(Math.round(ColorMath.red(argb) * colorMultiplier[0])),
                clampByte(Math.round(ColorMath.green(argb) * colorMultiplier[1])),
                clampByte(Math.round(ColorMath.blue(argb) * colorMultiplier[2])));
    }

    public SceneNode node() {
        return node;
    }

    public void setOverride(String path, Object value) {
        if (!Objects.equals(overrides.put(path, value), value)) {
            config = ConfigOverrides.apply(authored, overrides);
        }
    }

    public AraTrailConfig config() {
        return config;
    }

    public boolean isVisible() {
        return node.isVisible();
    }

    public boolean isAlive() {
        if (removed) {
            return dieWhenAllTailsRemoved && points.size() > 1;
        }
        return true;
    }

    public boolean isFinishedForGood() {
        return isStandalone() && !config.looping && removed && !isAlive();
    }

    private float memRandom(String key) {
        return memRandom.computeIfAbsent(key, k -> random.nextFloat());
    }

    private float emitterT() {
        if (emitterTSupplier != null) {
            return (float) emitterTSupplier.getAsDouble();
        }
        if (config.duration <= 0) {
            return 0;
        }
        return config.looping ? (age % config.duration) / config.duration : Math.min(age / config.duration, 1f);
    }

    private Vec3 worldPosition() {
        return worldPositionSupplier != null ? worldPositionSupplier.get().copy() : node.world().position().copy();
    }

    private Vec3 worldForward() {
        return worldForwardSupplier != null ? worldForwardSupplier.get().copy()
                : node.world().rotation().rotate(new Vec3(0, 0, -1));
    }

    private Vec3 worldUp() {
        return worldUpSupplier != null ? worldUpSupplier.get().copy()
                : node.world().rotation().rotate(new Vec3(0, 1, 0));
    }

    private Vec3 worldRight() {
        return worldRightSupplier != null ? worldRightSupplier.get().copy()
                : node.world().rotation().rotate(new Vec3(1, 0, 0));
    }

    private Mat4 worldToTrail() {
        return switch (config.space) {
            case WORLD -> Mat4.identity();
            case LOCAL -> node.worldToLocal();
            case CUSTOM -> {
                SceneNode custom = customSpaceResolver.apply(config.customSpaceId);
                yield custom == null ? Mat4.identity() : custom.worldToLocal();
            }
        };
    }

    private Mat4 trailToWorld() {
        return switch (config.space) {
            case WORLD -> Mat4.identity();
            case LOCAL -> node.localToWorld();
            case CUSTOM -> {
                SceneNode custom = customSpaceResolver.apply(config.customSpaceId);
                yield custom == null ? Mat4.identity() : custom.localToWorld();
            }
        };
    }

    private void warmup() {
        if (!config.physics.enable()) {
            return;
        }
        float simulatedTime = config.physics.warmup();
        float fixedDt = 0.05f;
        while (simulatedTime > fixedDt) {
            physicsStep(fixedDt);
            emissionStep(fixedDt);
            snapLastPointToTransform();
            updatePointsLifecycle(fixedDt);
            simulatedTime -= fixedDt;
        }
    }

    public void tick(float dtTicks) {
        if (dtTicks <= 0) {
            return;
        }
        float timeStep = dtTicks / 20f;
        refreshDriverData();
        if (!removed) {
            age += dtTicks;
            updateVelocity(timeStep);
            if (config.physics.enable()) {
                physicsStep(timeStep);
            }
            emissionStep(timeStep);
            snapLastPointToTransform();
        } else if (config.physics.enable()) {
            physicsStep(timeStep);
        }
        updatePointsLifecycle(timeStep);

        if (!removed && isStandalone() && !config.looping && age >= config.duration) {
            removed = true;
        }
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    private boolean isStandalone() {
        return worldPositionSupplier == null;
    }

    public void markRemoved() {
        removed = true;
    }

    private void updateVelocity(float dt) {
        Vec3 current = worldPosition();
        if (dt > 0) {
            Vec3 instant = new Vec3(current).sub(prevPosition).mul(1f / dt);
            velocity = velocity.lerp(instant, config.physics.velocitySmoothing());
        }
        prevPosition = current;
    }

    private Vec3 trailGravity() {
        Vec3 gravity = new Vec3(config.physics.gravity());
        switch (config.physics.gravitySpace()) {
            case LOCAL -> gravity = node.localToWorld().transformDirection(gravity);
            case CUSTOM -> {
                String id = config.physics.gravityCustomSpaceId();
                SceneNode custom = id == null || id.isEmpty() ? null : customSpaceResolver.apply(id);
                if (custom != null) {
                    gravity = custom.localToWorld().transformDirection(gravity);
                }
            }
            default -> {
            }
        }
        return worldToTrail().transformDirection(gravity);
    }

    private void physicsStep(float dt) {
        float velocityScale = (float) Math.pow(1 - clamp01(config.physics.damping()), dt);
        Vec3 gravity = trailGravity();
        for (Point point : points) {
            point.velocity.add(new Vec3(gravity).mul(dt));
            point.velocity.mul(velocityScale);
            point.position.add(new Vec3(point.velocity).mul(dt));
        }
    }

    private void emissionStep(float dt) {
        accumTime += dt;
        if (accumTime < config.timeInterval || !config.emit) {
            return;
        }
        Vec3 position = worldToTrail().transformPosition(worldPosition());
        if (points.isEmpty() || new Vec3(position).sub(points.get(points.size() - 1).position).length() >= config.minDistance) {
            emitPoint(position);
            accumTime = 0;
        }
    }

    private void emitPoint(Vec3 position) {
        float texcoord = points.isEmpty() ? 0
                : points.get(points.size() - 1).texcoord + position.copy().sub(points.get(points.size() - 1).position).length();
        Mat4 worldToTrail = worldToTrail();
        Vec3 normal = worldToTrail.transformDirection(worldForward());
        Vec3 tangent = worldToTrail.transformDirection(worldRight());
        Vec3 pointVelocity = new Vec3(velocity).mul(config.physics.inertia()).add(config.initialVelocity);
        points.add(new Point(position, pointVelocity, tangent, normal, config.initialColor, config.initialThickness,
                texcoord, lifeTime));
    }

    private void snapLastPointToTransform() {
        if (points.isEmpty()) {
            return;
        }
        Point last = points.get(points.size() - 1);
        if (!config.emit) {
            last.discontinuous = true;
        }
        if (last.discontinuous) {
            return;
        }
        Mat4 worldToTrail = worldToTrail();
        last.position = worldToTrail.transformPosition(worldPosition());
        last.normal = worldToTrail.transformDirection(worldForward());
        last.tangent = worldToTrail.transformDirection(worldRight());
        if (points.size() > 1) {
            Point prev = points.get(points.size() - 2);
            last.texcoord = prev.texcoord + last.position.copy().sub(prev.position).length();
        }
    }

    private void updatePointsLifecycle(float dt) {
        for (int i = points.size() - 1; i >= 0; i--) {
            Point point = points.get(i);
            point.life -= dt;
            if (point.life > 0) {
                continue;
            }
            int next = Math.min(i + 1, points.size() - 1);
            if (config.smoothness <= 1) {
                if (points.get(next).life <= 0) {
                    points.remove(i);
                }
            } else {
                int next2 = Math.min(i + 2, points.size() - 1);
                if (points.get(next).life <= 0 && points.get(next2).life <= 0) {
                    points.remove(i);
                }
            }
        }
    }

    private CurveFrame initializeCurveFrame(List<Point> trail) {
        Vec3 point = trail.get(trail.size() - 1).position;
        Vec3 nextPoint = trail.get(trail.size() - 2).position;
        Vec3 tangent = new Vec3(nextPoint).sub(point);
        Vec3 forward = trailToWorld().transformDirection(worldForward());
        float proximity = Math.abs(tangent.copy().normalize().dot(forward));
        if (Math.abs(proximity - 1f) < 1e-4f) {
            tangent.add(trailToWorld().transformDirection(worldRight()).mul(0.01f));
        }
        return new CurveFrame(point, forward, trailToWorld().transformDirection(worldUp()), tangent);
    }

    private List<RenderVertex> buildSpan(int start, int end, Vec3 cameraPositionWorld) {
        List<Point> trail = renderablePoints(start, end);
        if (config.sorting == AraTrailConfig.Sorting.NEWER_ON_TOP) {
            Collections.reverse(trail);
        }
        if (trail.size() < 2) {
            return List.of();
        }
        Vec3 localCamPosition = worldToTrail().transformPosition(cameraPositionWorld);
        CurveFrame frame = config.alignment == AraTrailConfig.Alignment.VELOCITY ? initializeCurveFrame(trail) : null;

        float totalLength = 0;
        for (int i = 0; i < trail.size() - 1; i++) {
            totalLength += trail.get(i).position.copy().sub(trail.get(i + 1).position).length();
        }
        totalLength = Math.max(totalLength, 1e-5f);

        float normalizedLife = clamp01(emitterT());
        int timeColor = (int) config.colorOverTime.get(normalizedLife, () -> (double) memRandom("trails-colorOverTime"));
        float timeThickness = (float) config.thicknessOverTime.get(normalizedLife, () -> (double) memRandom("trails-thicknessOverTime"));

        List<RenderVertex> out = new ArrayList<>(trail.size());
        float partialLength = 0;

        float vCoord = config.textureMode == AraTrailConfig.TextureMode.STRETCH
                ? config.uvFactor
                : config.uvFactor * totalLength * (1 - config.tileAnchor);
        if (config.sorting == AraTrailConfig.Sorting.NEWER_ON_TOP) {
            vCoord = 1 - vCoord;
        }
        for (int i = trail.size() - 1; i >= 0; i--) {
            int nextIndex = Math.max(i - 1, 0);
            int prevIndex = Math.min(i + 1, trail.size() - 1);
            Vec3 nextV = trail.get(nextIndex).position.copy().sub(trail.get(i).position);
            Vec3 prevV = trail.get(i).position.copy().sub(trail.get(prevIndex).position);
            float sectionLength = nextIndex == i ? prevV.length() : nextV.length();
            boolean nextZero = nextV.lengthSquared() < 1e-10f;
            boolean prevZero = prevV.lengthSquared() < 1e-10f;
            if (!nextZero) {
                nextV.normalize();
            }
            if (!prevZero) {
                prevV.normalize();
            }
            if (nextZero) {
                nextV.set(prevV);
            }
            if (prevZero) {
                prevV.set(nextV);
            }

            Vec3 tangent = config.alignment == AraTrailConfig.Alignment.LOCAL
                    ? trail.get(i).tangent.copy().normalize()
                    : new Vec3(nextV).add(prevV).mul(0.5f);

            Vec3 normal;
            Vec3 bitangent;
            if (config.alignment == AraTrailConfig.Alignment.LOCAL) {
                normal = trail.get(i).normal.copy().normalize();
                bitangent = Vec3.cross(tangent, normal).normalize();
            } else if (config.alignment == AraTrailConfig.Alignment.VIEW) {
                normal = new Vec3(localCamPosition).sub(trail.get(i).position).normalize();
                bitangent = Vec3.cross(tangent, normal).normalize();
            } else {
                normal = frame.transport(tangent, trail.get(i).position).copy().normalize();
                bitangent = frame.bitangent.copy().normalize();
            }

            float normalizedLength = config.sorting == AraTrailConfig.Sorting.OLDER_ON_TOP
                    ? partialLength / totalLength : (totalLength - partialLength) / totalLength;
            partialLength += sectionLength;
            float normalizedSegmentLife = Float.isInfinite(lifeTime) ? 1f : clamp01(1 - trail.get(i).life / Math.max(lifeTime, 1e-5f));

            int lengthColor = (int) config.colorOverLength.get(normalizedLength, () -> (double) memRandom("trails-colorOverLength"));
            int segmentColor = (int) config.colorOverSegmentTime.get(normalizedSegmentLife,
                    () -> (double) memRandom("trails-colorOverSegmentTime"));
            int combinedColor = ColorMath.multiplyArgb(trail.get(i).color,
                    ColorMath.multiplyArgb(timeColor, ColorMath.multiplyArgb(lengthColor, segmentColor)));
            combinedColor = applyColorMultiplier(combinedColor);

            float lengthThickness = (float) config.thicknessOverLength.get(normalizedLength,
                    () -> (double) memRandom("trails-thicknessOverLength"));
            float segmentThickness = (float) config.thicknessOverSegmentTime.get(normalizedSegmentLife,
                    () -> (double) memRandom("trails-thicknessOverSegmentTime"));
            float finalThickness = config.thickness * thicknessMultiplier * trail.get(i).thickness * timeThickness
                    * segmentThickness * lengthThickness;

            if (config.textureMode == AraTrailConfig.TextureMode.WORLD_TILE) {
                vCoord = config.tileAnchor + trail.get(i).texcoord * config.uvFactor;
            }
            float u = vCoord;
            float uvDelta = config.textureMode == AraTrailConfig.TextureMode.STRETCH ? sectionLength / totalLength : sectionLength;
            vCoord += config.uvFactor * (config.sorting == AraTrailConfig.Sorting.NEWER_ON_TOP ? uvDelta : -uvDelta);

            out.add(new RenderVertex(trailToWorld().transformPosition(trail.get(i).position), tangent, normal,
                    bitangent, combinedColor, finalThickness, u, u));
        }
        return out;
    }

    public List<List<RenderVertex>> buildRenderSpans(Vec3 cameraPositionWorld) {
        List<List<RenderVertex>> spans = new ArrayList<>();
        if (points.size() < 2) {
            return spans;
        }
        int start = 0;
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).discontinuous || i == points.size() - 1) {
                List<RenderVertex> span = buildSpan(start, i, cameraPositionWorld);
                if (span.size() > 1) {
                    spans.add(span);
                }
                start = i + 1;
            }
        }
        return spans;
    }

    private List<Point> renderablePoints(int start, int end) {
        List<Point> result = new ArrayList<>();
        if (config.smoothness <= 1) {
            for (int i = start; i <= end; i++) {
                result.add(points.get(i));
            }
            return result;
        }
        float sampleSize = 1f / config.smoothness;
        for (int i = start; i < end; i++) {
            int i0 = i == start ? start : i - 1;
            int i2 = i + 1;
            int i3 = i == end - 1 ? end : i + 2;
            Point a = points.get(i0);
            Point b = points.get(i);
            Point c = points.get(i2);
            Point d = points.get(i3);
            for (int j = 0; j < config.smoothness; j++) {
                float t = j * sampleSize;
                if (c.position.copy().sub(b.position).lengthSquared() < config.smoothingDistance * config.smoothingDistance) {
                    result.add(b);
                    break;
                }
                float life = catmullRom(a.life, b.life, c.life, d.life, t);
                if (life > 0) {
                    Point interpolated = new Point(
                            catmullRom3(a.position, b.position, c.position, d.position, t),
                            catmullRom3(a.velocity, b.velocity, c.velocity, d.velocity, t),
                            catmullRom3(a.tangent, b.tangent, c.tangent, d.tangent, t),
                            catmullRom3(a.normal, b.normal, c.normal, d.normal, t),
                            catmullRomColor(a.color, b.color, c.color, d.color, t),
                            catmullRom(a.thickness, b.thickness, c.thickness, d.thickness, t),
                            catmullRom(a.texcoord, b.texcoord, c.texcoord, d.texcoord, t), life);
                    result.add(interpolated);
                }
            }
        }
        if (points.get(end).life > 0) {
            result.add(points.get(end));
        }
        return result;
    }

    public static final class Point {
        Vec3 position;
        Vec3 velocity;
        Vec3 tangent;
        Vec3 normal;
        int color;
        float thickness;
        float life;
        float texcoord;
        boolean discontinuous;

        Point(Vec3 position, Vec3 velocity, Vec3 tangent, Vec3 normal, int color, float thickness, float texcoord,
              float life) {
            this.position = position;
            this.velocity = velocity;
            this.tangent = tangent;
            this.normal = normal;
            this.color = color;
            this.thickness = thickness;
            this.texcoord = texcoord;
            this.life = life;
        }
    }

    public record RenderVertex(Vec3 position, Vec3 tangent, Vec3 normal, Vec3 bitangent, int color, float thickness,
                               float u, float vCoord) {
    }

    private static final class CurveFrame {
        Vec3 position;
        Vec3 normal;
        Vec3 bitangent;
        Vec3 tangent;

        CurveFrame(Vec3 position, Vec3 normal, Vec3 bitangent, Vec3 tangent) {
            this.position = position;
            this.normal = normal;
            this.bitangent = bitangent;
            this.tangent = tangent;
        }

        Vec3 transport(Vec3 newTangent, Vec3 newPosition) {
            Vec3 v1 = new Vec3(newPosition).sub(position);
            float c1 = v1.dot(v1);
            Vec3 rL = new Vec3(normal).sub(new Vec3(v1).mul(2 / (c1 + 1e-5f) * v1.dot(normal)));
            Vec3 tL = new Vec3(tangent).sub(new Vec3(v1).mul(2 / (c1 + 1e-5f) * v1.dot(tangent)));
            Vec3 v2 = new Vec3(newTangent).sub(tL);
            float c2 = v2.dot(v2);
            Vec3 r1 = new Vec3(rL).sub(new Vec3(v2).mul(2 / (c2 + 1e-5f) * v2.dot(rL)));
            Vec3 s1 = Vec3.cross(newTangent, r1);
            normal = r1;
            bitangent = s1;
            tangent = newTangent;
            position = newPosition;
            return normal;
        }
    }
}

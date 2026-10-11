package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;
import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.SceneLighting;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.HdrSampler;
import net.renan.photonplugin.menus.resource.preview.neo.model.shape.EmissionContext;

import java.util.*;
import java.util.function.Function;

public final class ParticleEmitterSim {
    private final ParticleEmitterConfig authored;
    private final Map<String, Object> overrides = new HashMap<>();
    private final SceneNode node;
    private final Transform sceneTransform;
    private final Function<String, SceneNode> customSpaceResolver;
    private final Random random;
    private final MemRandom emitterMem;
    private final ObjMesh shapeMesh;
    private final EmissionSetting.State emissionState = new EmissionSetting.State();
    private final List<ParticleState> particles = new ArrayList<>();
    private final List<SubEmitterSpawnRequest> pendingSubEmitterSpawns = new ArrayList<>();
    private final Vec3 previousPosition = new Vec3();
    private final Vec3 emitterVelocity = new Vec3();
    private final Vec3 simScale = new Vec3(1, 1, 1);

    private final List<TrailEmitterSim> carriedTrails = new ArrayList<>();
    private final List<AraTrailSim> carriedAraTrails = new ArrayList<>();
    private final Object lightPickKey = new Object();
    private final Object lightColorKey = new Object();
    private final Object lightIntensityKey = new Object();
    private final Object lightRangeKey = new Object();
    private final Object lightVolumetricKey = new Object();
    private ParticleEmitterConfig config;
    private boolean overridesDirty;
    private int colorTintArgb = -1;
    private float emitterAge;
    private boolean hasFirstUpdate;
    private List<ForceFieldSim> sceneForceFields = List.of();
    private String objectId = "";
    private boolean hasPreviousPosition;
    private Mat4 simToWorld = Mat4.identity();
    private Mat4 worldToSim = Mat4.identity();
    private Quat simRotation = Quat.identity();
    private boolean worldAxes;
    private SpawnFrame stepFrame;

    public ParticleEmitterSim(ParticleEmitterConfig config, SceneNode node, Function<String, SceneNode> customSpaceResolver,
                              Function<ModelSource, ObjMesh> meshResolver, Random random) {
        this.authored = config;
        this.config = config;
        this.node = node;
        this.sceneTransform = node.world();
        this.customSpaceResolver = customSpaceResolver;
        this.random = random;
        this.emitterMem = new MemRandom(random);
        ModelSource shapeMeshSource = config.shape.meshSourceIfAny();
        this.shapeMesh = shapeMeshSource == null ? ObjMesh.EMPTY : meshResolver.apply(shapeMeshSource);
        refreshSpace();
    }

    private static Vec3 emitterDirToSim(ParticleState p, Vec3 d) {
        return p.frame == null ? new Vec3(d) : p.frame.emitterDirToSim(d);
    }

    private static Vec3 simDirToEmitter(ParticleState p, Vec3 d) {
        return p.frame == null ? new Vec3(d) : p.frame.simDirToEmitter(d);
    }

    private static Vec3 divSafe(Vec3 a, Vec3 b) {
        return new Vec3(b.x == 0 ? 0 : a.x / b.x, b.y == 0 ? 0 : a.y / b.y, b.z == 0 ? 0 : a.z / b.z);
    }

    private static int mulArgb(int a, int b) {
        int oa = Math.round(ColorMath.alpha(a) * ColorMath.alpha(b) / 255f);
        int or = Math.round(ColorMath.red(a) * ColorMath.red(b) / 255f);
        int og = Math.round(ColorMath.green(a) * ColorMath.green(b) / 255f);
        int ob = Math.round(ColorMath.blue(a) * ColorMath.blue(b) / 255f);
        return ColorMath.pack(oa, or, og, ob);
    }

    private static float maxComponent(Vec3 v) {
        return v.x > v.y ? Math.max(v.x, v.z) : Math.max(v.y, v.z);
    }

    public void setSceneForceFields(List<ForceFieldSim> forceFields, String objectId) {
        this.sceneForceFields = forceFields;
        this.objectId = objectId;
    }

    void setEmitterAge(float age) {
        this.emitterAge = age;
    }

    void setColorTint(int argb) {
        this.colorTintArgb = argb;
    }

    public void setOverride(String path, Object value) {
        Object previous = overrides.put(path, value);
        if (!Objects.equals(previous, value)) {
            overridesDirty = true;
        }
    }

    public List<ParticleState> particles() {
        return particles;
    }

    public ParticleEmitterConfig config() {
        return config;
    }

    public SceneNode node() {
        return node;
    }

    public boolean isVisible() {
        return node.isVisible();
    }

    public Vec3 simSpaceScale() {
        return simScale;
    }

    public Quat simSpaceRotation() {
        return simRotation;
    }

    public Transform emitterTransform() {
        return sceneTransform;
    }

    public Vec3 emitterVelocity() {
        return emitterVelocity;
    }

    public boolean isFinishedForGood() {
        return !config.looping && emitterAge > config.duration && particles.isEmpty()
                && carriedTrails.isEmpty() && carriedAraTrails.isEmpty();
    }

    public List<TrailEmitterSim> carriedTrails() {
        return carriedTrails;
    }

    public List<AraTrailSim> carriedAraTrails() {
        return carriedAraTrails;
    }

    public void beginTick(float dtReal) {
        Vec3 position = sceneTransform.position();
        if (hasPreviousPosition && dtReal > 0f) {
            emitterVelocity.set(position).sub(previousPosition).mul(1f / dtReal);
        }
        previousPosition.set(position);
        hasPreviousPosition = true;
    }

    public void tick(float dt) {
        if (overridesDirty) {
            config = ConfigOverrides.apply(authored, overrides);
            overridesDirty = false;
        }
        refreshSpace();
        if (!hasFirstUpdate) {
            hasFirstUpdate = true;
            int prewarm = config.prewarm;
            if (!config.looping) {
                prewarm = Math.min(prewarm, Math.max(config.duration - 1, 0));
            }
            for (int i = 0; i < prewarm; i++) {
                step(1f);
            }
        }
        step(dt);
    }

    private void step(float dt) {
        boolean stillEmitting = config.looping || config.duration <= 0 || emitterAge < config.duration;
        double t = getEmitterT();
        if (stillEmitting) {
            spawn(dt, t);
        }
        emitterAge += dt;
        updateParticles(dt);
    }

    private double getEmitterT() {
        if (config.duration <= 0) {
            return 0;
        }
        if (config.looping) {
            return (emitterAge % config.duration) / config.duration;
        }
        return Math.min(emitterAge / config.duration, 1.0);
    }

    private void refreshSpace() {
        stepFrame = null;
        worldAxes = config.simulationSpace == ParticleEmitterConfig.SimulationSpace.WORLD;
        SceneNode space = switch (config.simulationSpace) {
            case WORLD -> null;
            case CUSTOM -> customSpaceResolver.apply(config.customSpaceId);
            default -> node;
        };
        if (space == null) {
            simToWorld = Mat4.identity();
            worldToSim = Mat4.identity();
            simScale.set(1, 1, 1);
            simRotation = Quat.identity();
            return;
        }
        simToWorld = space.localToWorld();
        worldToSim = space.worldToLocal();
        simScale.set(space.world().scale());
        simRotation = space.world().rotation().copy();
    }

    private SpawnFrame currentSpawnFrame() {
        if (config.simulationSpace == ParticleEmitterConfig.SimulationSpace.LOCAL) {
            return null;
        }
        if (stepFrame == null) {
            stepFrame = new SpawnFrame(node.localToWorld(), node.worldToLocal(), worldToSim, simToWorld);
        }
        return stepFrame;
    }

    private Vec3 simDirToWorld(Vec3 d) {
        return worldAxes ? new Vec3(d) : simToWorld.transformDirection(d);
    }

    private Vec3 worldDirToSim(Vec3 d) {
        return worldAxes ? new Vec3(d) : worldToSim.transformDirection(d);
    }

    private void spawn(float dt, double t) {
        emissionState.accumulatedDistance += emitterVelocity.length() * dt;
        int count = config.emission.getEmissionCount(emissionState, random, () -> random.nextDouble(), t, emitterAge, dt,
                config.duration, config.looping);
        int room = config.maxParticles - particles.size();
        int toSpawn = Math.max(0, Math.min(count, room));
        for (int i = 0; i < toSpawn; i++) {
            particles.add(spawnOne(t, i, toSpawn));
        }
    }

    private ParticleState spawnOne(double emitterT, int batchIndex, int batchCount) {
        ParticleState p = new ParticleState(random);
        p.delay = (int) config.startDelay.get(emitterT, p.mem.of("start-delay"));
        int baseLifetime = (int) config.startLifetime.get(emitterT, p.mem.of("start-lifetime"));
        p.lifetime = config.lifetimeByEmitterSpeed.enable()
                ? config.lifetimeByEmitterSpeed.getLifetime(emitterVelocity.length() * 20f, baseLifetime, p.mem)
                : baseLifetime;
        Vec3 localPos = new Vec3();
        Vec3 localVel = new Vec3();
        EmissionContext context = new EmissionContext(emitterT, (int) emitterAge, batchIndex, batchCount);
        config.shape.sample(random, context, emitterMem, shapeMesh, localPos, localVel);
        if (config.simulationSpace == ParticleEmitterConfig.SimulationSpace.LOCAL) {
            p.basePos.set(localPos);
            p.storedVelocity.set(localVel);
        } else {
            Mat4 emitterToWorld = node.localToWorld();
            p.basePos.set(worldToSim.transformPosition(emitterToWorld.transformPosition(localPos)));
            p.storedVelocity.set(worldToSim.transformDirection(emitterToWorld.transformDirection(localVel)));
            p.frame = currentSpawnFrame();
        }
        if (config.inheritVelocity.enable() && config.inheritVelocity.mode() == InheritVelocity.Mode.INITIAL) {
            Vec3 inherited = config.inheritVelocity.getVelocity(emitterVelocity, emitterT, emitterMem);
            p.storedVelocity.add(worldToSim.transformDirection(inherited));
        }
        float startSpeed = (float) config.startSpeed.get(emitterT, p.mem.of("start-speed"));
        p.storedVelocity.mul(startSpeed);
        p.initialSize.set(config.startSize.get(emitterT, p.mem.of("start-size")));
        if (config.simulationSpace != ParticleEmitterConfig.SimulationSpace.LOCAL) {
            p.initialSize.mul(divSafe(sceneTransform.scale(), simScale));
        }
        float toRad = (float) (Math.PI / 180.0);
        p.initialRotationRad.set(config.startRotation.get(emitterT, p.mem.of("start-rotation")).mul(toRad));
        p.initialColor = (int) Math.round(config.startColor.get(emitterT, p.mem.of("start-color")));
        if (colorTintArgb != -1) {
            p.initialColor = ColorMath.multiplyArgb(p.initialColor, colorTintArgb);
        }
        p.currentColor = p.initialColor;
        p.currentSize.set(p.initialSize);
        p.currentRotationRad.set(p.initialRotationRad);
        p.position.set(simToWorld.transformPosition(p.basePos));
        update(p, 1f);
        attachTrail(p);
        return p;
    }

    private void updateParticles(float dt) {
        Iterator<ParticleState> purge = particles.iterator();
        while (purge.hasNext()) {
            if (purge.next().removed) {
                purge.remove();
            }
        }
        for (ParticleState p : particles) {
            updateTick(p, dt);
        }
        for (TrailEmitterSim trail : carriedTrails) {
            trail.tick(dt);
        }
        carriedTrails.removeIf(trail -> !trail.isAlive());
        for (AraTrailSim trail : carriedAraTrails) {
            trail.tick(dt);
        }
        carriedAraTrails.removeIf(trail -> !trail.isAlive());
    }

    private void updateTick(ParticleState p, float dt) {
        if (p.delay > 0) {
            p.delay -= dt;
            return;
        }
        if (p.age == 0) {
            triggerSubEmitters(p, SubEmitterDef.Event.BIRTH);
        }
        if (p.lifetime > 0 && p.age >= p.lifetime) {
            p.age += dt;
            updateVisuals(p);
            p.removed = true;
            triggerSubEmitters(p, SubEmitterDef.Event.DEATH);
            return;
        }
        p.age += dt;
        update(p, dt);
        triggerSubEmitterTick(p, dt);
    }

    private void update(ParticleState p, float dt) {
        updatePositionAndInternalVelocity(p, dt);
        updateVisuals(p);
    }

    List<SubEmitterSpawnRequest> drainSubEmitterSpawns() {
        if (pendingSubEmitterSpawns.isEmpty()) {
            return List.of();
        }
        List<SubEmitterSpawnRequest> drained = new ArrayList<>(pendingSubEmitterSpawns);
        pendingSubEmitterSpawns.clear();
        return drained;
    }

    private void triggerSubEmitters(ParticleState p, SubEmitterDef.Event event) {
        if (!config.subEmitters.enable()) {
            return;
        }
        for (SubEmitterDef def : config.subEmitters.emitters()) {
            if (def.event() == event) {
                trySpawnSubEmitter(p, def);
            }
        }
    }

    private void triggerSubEmitterTick(ParticleState p, float dt) {
        if (!config.subEmitters.enable()) {
            return;
        }
        int newTick = (int) Math.floor(p.age);
        if (newTick <= (int) Math.floor(p.age - dt)) {
            return;
        }
        for (SubEmitterDef def : config.subEmitters.emitters()) {
            if (def.event() == SubEmitterDef.Event.TICK && newTick % def.tickInterval() == 0) {
                trySpawnSubEmitter(p, def);
            }
        }
    }

    private void trySpawnSubEmitter(ParticleState p, SubEmitterDef def) {
        if (def.fxId() == null) {
            return;
        }
        float probability = (float) def.emitProbability().get(p.t(), p.mem.of("sub_emitter_probability"));
        if (random.nextFloat() >= probability) {
            return;
        }
        pendingSubEmitterSpawns.add(new SubEmitterSpawnRequest(def.fxId(), p.position.copy(), def, p.age, p.lifetime,
                p.currentColor, p.currentSize.copy(), p.currentRotationRad.copy()));
    }

    private void updateVisuals(ParticleState p) {
        p.velocity.set(realVelocity(p));
        double t = p.t();
        updateColor(p, t);
        updateSize(p, t);
        updateRotation(p, t);
        updateLight(p, t);
        if (config.uvAnimation.enable()) {
            p.currentUv = config.uvAnimation.getUvs(t, p.mem);
        }
        refreshRenderPosition(p, t);
    }

    private Vec3 simPositionWithNoise(ParticleState p, double t) {
        Vec3 pos = new Vec3(p.basePos);
        if (config.noise.enable()) {
            Vec3 noise = config.noise.getPositionDelta(t, p.mem);
            if (!noise.isZero()) {
                pos.add(noise);
            }
        }
        return pos;
    }

    private void refreshRenderPosition(ParticleState p, double t) {
        p.position.set(simToWorld.transformPosition(simPositionWithNoise(p, t)));
    }

    private Vec3 realVelocity(ParticleState p) {
        double t = p.t();
        Vec3 internal = new Vec3(p.storedVelocity);
        boolean vol = config.velocityOverLifetime.enable();
        if (vol) {
            internal.add(velocityOverLifetimeAddition(p, t));
        }
        Vec3 velocity = simDirToWorld(internal);
        if (config.forceOverLifetime.enable()) {
            Vec3 force = config.forceOverLifetime.getForce(t, p.mem);
            switch (config.forceOverLifetime.space()) {
                case LOCAL -> force = simDirToWorld(emitterDirToSim(p, force));
                case CUSTOM -> {
                    SceneNode custom = customNode(config.forceOverLifetime.customSpaceId());
                    if (custom != null) {
                        force = custom.localToWorld().transformDirection(force);
                    }
                }
                default -> {
                }
            }
            velocity.add(force);
        }
        if (config.inheritVelocity.enable() && config.inheritVelocity.mode() == InheritVelocity.Mode.CURRENT) {
            velocity.add(config.inheritVelocity.getVelocity(emitterVelocity, t, emitterMem));
        }
        if (vol) {
            velocity.mul(config.velocityOverLifetime.getVelocityMultiplier(t, p.mem));
        }
        return velocity;
    }

    public void submitLights(List<SceneLighting.Light> out) {
        LightEmission le = config.lightEmission;
        if (le == null || !le.enable() || !node.isActive() || !node.isVisible()) {
            return;
        }
        int budget = le.maxLights();
        float[] rgba = new float[4];
        for (ParticleState p : particles) {
            if (budget <= 0) {
                return;
            }
            if (p.delay > 0 || p.removed || p.mem.get(lightPickKey) > le.ratio()) {
                continue;
            }
            float t = p.t();
            float intensity = (float) le.intensity().get(t, p.mem.of(lightIntensityKey));
            float range = (float) le.range().get(t, p.mem.of(lightRangeKey));
            float alpha = ColorMath.alpha(p.currentColor) / 255f;
            if (le.alphaAffectsIntensity()) {
                intensity *= alpha;
            }
            if (le.sizeAffectsRange()) {
                range *= p.maxSizeComponent();
            }
            if (intensity <= 0 || range <= 0) {
                continue;
            }
            HdrSampler.sample(le.color(), t, p.mem.of(lightColorKey), rgba);
            float r = rgba[0], g = rgba[1], b = rgba[2];
            if (le.useParticleColor()) {
                r *= ColorMath.red(p.currentColor) / 255f;
                g *= ColorMath.green(p.currentColor) / 255f;
                b *= ColorMath.blue(p.currentColor) / 255f;
            }
            boolean spot = le.spot().enable();
            Vec3 axis = new Vec3(0, -1, 0);
            if (spot) {
                axis = lightSpotAxis(p, le.spot());
            }
            float volumetric = 0f;
            if (le.volumetricEnable()) {
                volumetric = Math.max(0f, (float) le.volumetricStrength().get(t, p.mem.of(lightVolumetricKey)));
            }
            out.add(SceneLighting.Light.of(new Vec3(p.position), r * intensity, g * intensity, b * intensity, range,
                    spot, axis, le.spot().innerAngle(), le.spot().outerAngle(), volumetric));
            budget--;
        }
    }

    private Vec3 lightSpotAxis(ParticleState p, LightEmission.Spot spot) {
        if (spot.alignToVelocity() && p.velocity.lengthSquared() > 1e-8f) {
            return new Vec3(p.velocity).normalize();
        }
        Vec3 dir = new Vec3(spot.direction());
        switch (spot.space()) {
            case LOCAL -> dir = simDirToWorld(emitterDirToSim(p, dir));
            case CUSTOM -> {
                SceneNode custom = customNode(spot.customSpaceId());
                if (custom != null) {
                    dir = custom.localToWorld().transformDirection(dir);
                }
            }
            default -> {
            }
        }
        return dir.lengthSquared() > 1e-12f ? dir.normalize() : new Vec3(0, -1, 0);
    }

    private SceneNode customNode(String id) {
        return id == null || id.isEmpty() ? null : customSpaceResolver.apply(id);
    }

    private Vec3 velocityOverLifetimeAddition(ParticleState p, double t) {
        VelocityOverLifetime vol = config.velocityOverLifetime;
        ValueSpace frame = vol.space();
        SceneNode custom = null;
        if (frame == ValueSpace.CUSTOM) {
            custom = customNode(vol.customSpaceId());
            if (custom == null) {
                frame = ValueSpace.WORLD;
            }
        }
        Vec3 simPos = simPositionWithNoise(p, t);
        Vec3 framePos;
        if (frame == ValueSpace.CUSTOM) {
            framePos = custom.worldToLocal().transformPosition(simToWorld.transformPosition(simPos));
        } else {
            Vec3 relative = new Vec3(simPos);
            if (p.frame != null) {
                relative.sub(p.frame.origin());
            }
            framePos = frame == ValueSpace.LOCAL ? simDirToEmitter(p, relative) : simDirToWorld(relative);
        }
        Vec3 addition = vol.getVelocityAddition(t, p.mem, framePos);
        return switch (frame) {
            case LOCAL -> emitterDirToSim(p, addition);
            case CUSTOM -> worldDirToSim(custom.localToWorld().transformDirection(addition));
            default -> worldDirToSim(addition);
        };
    }

    public Quat outerRotationOverLifetime(ParticleState p) {
        RotationOverLifetime rol = config.rotationOverLifetime;
        if (!rol.enable() || rol.isLocal()) {
            return null;
        }
        Vec3 degrees = rol.getRotationDegrees(p.t(), p.mem).mul((float) (Math.PI / 180.0));
        Quat spin = Quat.euler(degrees);
        if (rol.space() == ValueSpace.CUSTOM) {
            SceneNode custom = customNode(rol.customSpaceId());
            if (custom != null) {
                Quat frame = custom.world().rotation();
                spin = frame.multiply(spin).multiply(frame.conjugate());
            }
        }
        return spin;
    }

    private void updatePositionAndInternalVelocity(ParticleState p, float dt) {
        double t = p.t();
        Vec3 velocity = realVelocity(p);
        p.basePos.add(worldToSim.transformDirection(new Vec3(velocity).mul(dt)));
        if (config.externalForces.enable() && !sceneForceFields.isEmpty()) {
            float multiplier = config.externalForces.getMultiplier(t, p.mem);
            if (multiplier != 0) {
                Vec3 worldPos = simToWorld.transformPosition(simPositionWithNoise(p, t));
                Vec3 worldVelocity = simToWorld.transformDirection(p.storedVelocity);
                float size = p.maxSizeComponent();
                for (ForceFieldSim field : sceneForceFields) {
                    if (field.isActive() && config.externalForces.isInfluencedBy(field.objectId())) {
                        field.config().apply(field.transform(), worldPos, worldVelocity, size, dt, t, p.mem, multiplier);
                    }
                }
                p.storedVelocity.set(worldToSim.transformDirection(worldVelocity));
            }
        }
        if (!config.physics.enable()) {
            return;
        }
        float gravity = config.physics.getGravity(t, p.mem);
        if (gravity != 0) {
            p.storedVelocity.add(worldToSim.transformDirection(new Vec3(0, -gravity * 0.04f * dt, 0)));
        }
        float friction = (float) Math.pow(config.physics.getFriction(t, p.mem), dt);
        p.storedVelocity.mul(friction);
    }

    private void attachTrail(ParticleState p) {
        TrailsOnParticle trails = config.trails;
        if (!trails.enable() || random.nextFloat() >= trails.ratio()) {
            return;
        }
        boolean dieWithParticles = trails.dieWithParticles();

        java.util.function.Supplier<Float> lifetimeTicks = () -> {
            TrailsOnParticle live = config.trails;
            float time = (float) live.lifetime().get(p.t(), p.mem.of("trails-lifetime")) * p.lifetime;
            if (live.sizeAffectsLifetime()) {
                time *= maxComponent(p.currentSize);
            }
            return time;
        };
        java.util.function.Supplier<float[]> colorMultiplier = () -> {
            float[] color = {1f, 1f, 1f, 1f};
            TrailsOnParticle live = config.trails;
            if (live.inheritParticleColor()) {
                color[0] *= ColorMath.red(p.currentColor) / 255f;
                color[1] *= ColorMath.green(p.currentColor) / 255f;
                color[2] *= ColorMath.blue(p.currentColor) / 255f;
                color[3] *= ColorMath.alpha(p.currentColor) / 255f;
            }
            int c = (int) Math.round(live.colorOverLifetime().get(p.t(), p.mem.of("trails-color")));
            color[0] *= ColorMath.red(c) / 255f;
            color[1] *= ColorMath.green(c) / 255f;
            color[2] *= ColorMath.blue(c) / 255f;
            color[3] *= ColorMath.alpha(c) / 255f;
            return color;
        };

        if (trails.trailType() == TrailsOnParticle.Type.TRAIL) {
            TrailEmitterSim trail = new TrailEmitterSim(trails.trailConfig(), random, () -> p.position.copy());
            trail.setNode(node);
            trail.setDelay(p.delay + trails.trailConfig().startDelay);
            trail.setDieWhenAllTailsRemoved(!dieWithParticles);
            trail.setOnUpdate(() -> {
                if (p.removed) {
                    trail.setRemoved(true);
                }
            });
            trail.setLifetimeSupplier(lifetimeTicks);
            trail.setWidthMultiplier(() -> config.trails.sizeAffectsWidth() ? maxComponent(p.currentSize) : 1f);
            trail.setColorMultiplier(colorMultiplier);
            trail.setEmitterColor(() -> colorTintArgb);
            trail.setEmitterT(this::getEmitterT);
            carriedTrails.add(trail);
        } else {
            AraTrailSim trail = new AraTrailSim(trails.araConfig(), node, customSpaceResolver, random,
                    () -> p.position.copy(),
                    () -> simRotation.rotate(new Vec3(0, 1, 0)),
                    () -> simRotation.rotate(new Vec3(0, 0, -1)),
                    () -> simRotation.rotate(new Vec3(1, 0, 0)));
            trail.setDieWhenAllTailsRemoved(!dieWithParticles);
            trail.setOnUpdate(() -> {
                if (p.removed) {
                    trail.markRemoved();
                }
            });
            trail.setLifetimeSupplier(() -> lifetimeTicks.get() / 20f);
            trail.setThicknessMultiplierSupplier(() -> config.trails.sizeAffectsWidth() ? maxComponent(p.currentSize) : 1f);
            trail.setColorMultiplierSupplier(colorMultiplier);
            trail.setEmitterTSupplier(this::getEmitterT);
            carriedAraTrails.add(trail);
        }
    }

    private void updateColor(ParticleState p, double t) {
        if (!config.colorOverLifetime.enable() && !config.colorBySpeed.enable()) {
            return;
        }
        int color = p.initialColor;
        if (config.colorOverLifetime.enable()) {
            color = mulArgb(color, config.colorOverLifetime.getColor(t, p.mem.of("col")));
        }
        if (config.colorBySpeed.enable()) {
            color = mulArgb(color, config.colorBySpeed.getColor(p.speedBlocksPerSecond(), p.mem.of("cbs")));
        }
        p.currentColor = color;
    }

    private void updateSize(ParticleState p, double t) {
        if (!config.sizeBySpeed.enable() && !config.sizeOverLifetime.enable() && !config.noise.enable()) {
            return;
        }
        Vec3 size = new Vec3(p.initialSize);
        if (config.noise.enable()) {
            size.add(config.noise.getSizeDelta(t, p.mem));
        }
        Vec3 mul = new Vec3(1, 1, 1);
        if (config.sizeBySpeed.enable()) {
            mul.mul(config.sizeBySpeed.getSize(p.speedBlocksPerSecond(), p.mem.of("size-by-speed")));
        }
        if (config.sizeOverLifetime.enable()) {
            mul.mul(config.sizeOverLifetime.getSize(t, p.mem.of("size-over-life")));
        }
        p.currentSize.set(size.mul(mul));
    }

    private void updateRotation(ParticleState p, double t) {
        if (!config.rotationOverLifetime.enable() && !config.rotationBySpeed.enable() && !config.noise.enable()) {
            return;
        }
        Vec3 rotation = new Vec3(p.initialRotationRad);
        float toRad = (float) (Math.PI / 180.0);
        if (config.rotationOverLifetime.enable() && config.rotationOverLifetime.isLocal()) {
            rotation.add(config.rotationOverLifetime.getRotationDegrees(t, p.mem).mul(toRad));
        }
        if (config.rotationBySpeed.enable()) {
            rotation.add(config.rotationBySpeed.getRotationDegrees(p.speedBlocksPerSecond(), p.mem).mul(toRad));
        }
        if (config.noise.enable()) {
            rotation.add(config.noise.getRotationDegrees(t, p.mem));
        }
        p.currentRotationRad.set(rotation);
    }

    private void updateLight(ParticleState p, double t) {
        if (config.lights.enable()) {
            p.blockLight = config.lights.getBlockLight(t, p.mem);
            p.skyLight = config.lights.getSkyLight(t, p.mem);
        } else {
            p.blockLight = 15;
            p.skyLight = 15;
        }
    }
}

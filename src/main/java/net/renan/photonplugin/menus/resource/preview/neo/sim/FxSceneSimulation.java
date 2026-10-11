package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.render.SceneLighting;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FxSceneSimulation {
    private static final int MAX_SUB_EMITTER_DEPTH = 4;
    private static final int MAX_TOTAL_SUB_INSTANCES = 400;

    private final FxScene scene;
    private final Function<ModelSource, ObjMesh> meshResolver;
    private final Function<String, FxScene> subEffectResolver;
    private final boolean autoRestart;
    private final Vec3 rootWorldPosition;
    private final int depth;
    private final int[] globalSubInstanceCount;
    private final List<FxSceneSimulation> subInstances = new ArrayList<>();
    private long baseSeed;
    private Random random;
    private SceneNode root;
    private Map<String, SceneNode> nodes;
    private Map<SceneNode, ParticleEmitterSim> emitterByNode;

    private Map<SceneNode, java.util.function.BiConsumer<String, Object>> overrideTargets;
    private Map<SceneNode, Consumer<Long>> rebuilders;
    private List<ParticleEmitterSim> particleEmitters;
    private List<BeamSim> beams;
    private List<TrailEmitterSim> trails;
    private List<ForceFieldSim> forceFields;
    private List<AraTrailSim> araTrails;
    private List<LightSim> lights;
    private List<FogVolumeSim> fogs;
    private TimelineSim timelineSim;
    private float sceneAge;

    public FxSceneSimulation(FxScene scene, long baseSeed) {
        this(scene, baseSeed, source -> ObjMesh.EMPTY, id -> null);
    }

    public FxSceneSimulation(FxScene scene, long baseSeed, Function<ModelSource, ObjMesh> meshResolver) {
        this(scene, baseSeed, meshResolver, id -> null);
    }

    public FxSceneSimulation(FxScene scene, long baseSeed, Function<ModelSource, ObjMesh> meshResolver,
                             Function<String, FxScene> subEffectResolver) {
        this(scene, baseSeed, meshResolver, subEffectResolver, true, Vec3.zero(), 0, new int[1]);
    }

    private FxSceneSimulation(FxScene scene, long baseSeed, Function<ModelSource, ObjMesh> meshResolver,
                              Function<String, FxScene> subEffectResolver, boolean autoRestart,
                              Vec3 rootWorldPosition, int depth, int[] globalSubInstanceCount) {
        this.scene = scene;
        this.baseSeed = baseSeed;
        this.meshResolver = meshResolver;
        this.subEffectResolver = subEffectResolver;
        this.autoRestart = autoRestart;
        this.rootWorldPosition = rootWorldPosition;
        this.depth = depth;
        this.globalSubInstanceCount = globalSubInstanceCount;
        restart();
    }

    private static boolean createsCycle(SceneNode node, SceneNode candidateParent) {
        for (SceneNode cursor = candidateParent; cursor != null; cursor = cursor.parent()) {
            if (cursor == node) {
                return true;
            }
        }
        return false;
    }

    private static void stepObject(SceneNode node, float dt, Consumer<Float> begin, Consumer<Float> tick) {
        if (node != null && !node.isActive()) {
            return;
        }
        begin.accept(dt);
        float owed = dt * (node == null ? 1f : node.timeScale());
        while (owed > 1e-6f) {
            float step = Math.min(owed, 1f);
            tick.accept(step);
            owed -= step;
        }
    }

    public long seed() {
        return baseSeed;
    }

    public FxSceneSimulation probeCopy() {
        return new FxSceneSimulation(scene, baseSeed, meshResolver, subEffectResolver, false, rootWorldPosition.copy(), 0,
                new int[1]);
    }

    public boolean isFinished() {
        return isEverythingFinished();
    }

    public void setSeed(long seed) {
        this.baseSeed = seed;
    }

    public float playbackTime() {
        return sceneAge;
    }

    public void restart() {
        random = new Random(baseSeed);
        root = new SceneNode(SceneNode.ROOT_ID, new Transform(rootWorldPosition.copy(), Quat.identity(), new Vec3(1, 1, 1)));
        nodes = new LinkedHashMap<>();
        nodes.put(SceneNode.ROOT_ID, root);
        emitterByNode = new HashMap<>();
        overrideTargets = new HashMap<>();
        rebuilders = new HashMap<>();
        subInstances.clear();

        List<SceneNode> objectNodes = new ArrayList<>();
        int index = 0;
        for (FxObject obj : scene.objects()) {
            String id = obj.id().isEmpty() ? "object#" + index : obj.id();
            SceneNode node = new SceneNode(id, obj.transform());
            objectNodes.add(node);
            nodes.putIfAbsent(id, node);
            index++;
        }
        for (int i = 0; i < objectNodes.size(); i++) {
            FxObject obj = scene.objects().get(i);
            SceneNode node = objectNodes.get(i);
            SceneNode parent = obj.parentId() == null || obj.parentId().isEmpty() ? root : nodes.getOrDefault(obj.parentId(), root);
            node.setParent(createsCycle(node, parent) ? root : parent);
        }
        root.refresh();

        particleEmitters = new ArrayList<>();
        beams = new ArrayList<>();
        trails = new ArrayList<>();
        forceFields = new ArrayList<>();
        araTrails = new ArrayList<>();
        lights = new ArrayList<>();
        fogs = new ArrayList<>();

        for (int i = 0; i < scene.objects().size(); i++) {
            FxObject obj = scene.objects().get(i);
            SceneNode node = objectNodes.get(i);
            switch (obj.data()) {
                case ParticleEmitterConfig pec -> {
                    ParticleEmitterSim pe = new ParticleEmitterSim(pec, node, this::node, meshResolver, random);
                    particleEmitters.add(pe);
                    emitterByNode.put(node, pe);
                    int at = particleEmitters.size() - 1;
                    rebuilders.put(node, seed -> {
                        ParticleEmitterSim fresh = new ParticleEmitterSim(pec, node, this::node, meshResolver, new Random(seed));
                        fresh.setSceneForceFields(forceFields, "");
                        particleEmitters.set(at, fresh);
                        emitterByNode.put(node, fresh);
                    });
                }
                case BeamConfig bc -> {
                    BeamSim beam = new BeamSim(bc, node.world(), random);
                    beam.setNode(node);
                    beams.add(beam);
                    overrideTargets.put(node, beam::setOverride);
                    int at = beams.size() - 1;
                    rebuilders.put(node, seed -> {
                        BeamSim fresh = new BeamSim(bc, node.world(), new Random(seed));
                        fresh.setNode(node);
                        beams.set(at, fresh);
                        overrideTargets.put(node, fresh::setOverride);
                    });
                }
                case TrailConfig tc -> {
                    TrailEmitterSim trail = new TrailEmitterSim(tc, node.world(), random);
                    trail.setNode(node);
                    trails.add(trail);
                    overrideTargets.put(node, trail::setOverride);
                    int at = trails.size() - 1;
                    rebuilders.put(node, seed -> {
                        TrailEmitterSim fresh = new TrailEmitterSim(tc, node.world(), new Random(seed));
                        fresh.setNode(node);
                        trails.set(at, fresh);
                        overrideTargets.put(node, fresh::setOverride);
                    });
                }
                case ForceFieldConfig ffc -> {
                    ForceFieldSim field = new ForceFieldSim(obj.id(), ffc, node.world(), node);
                    forceFields.add(field);
                    overrideTargets.put(node, field::setOverride);
                }
                case AraTrailConfig atc -> {
                    AraTrailSim araTrail = new AraTrailSim(atc, node, this::node, random);
                    araTrails.add(araTrail);
                    overrideTargets.put(node, araTrail::setOverride);
                    int at = araTrails.size() - 1;
                    rebuilders.put(node, seed -> {
                        AraTrailSim fresh = new AraTrailSim(atc, node, this::node, new Random(seed));
                        araTrails.set(at, fresh);
                        overrideTargets.put(node, fresh::setOverride);
                    });
                }
                case LightConfig lc -> {
                    LightSim light = new LightSim(lc, node, random);
                    lights.add(light);
                    overrideTargets.put(node, light::setOverride);
                    int at = lights.size() - 1;
                    rebuilders.put(node, seed -> {
                        LightSim fresh = new LightSim(lc, node, new Random(seed));
                        lights.set(at, fresh);
                        overrideTargets.put(node, fresh::setOverride);
                    });
                }
                case FogVolumeConfig fc -> {
                    FogVolumeSim fog = new FogVolumeSim(fc, node, random);
                    fogs.add(fog);
                    overrideTargets.put(node, fog::setOverride);
                    int at = fogs.size() - 1;
                    rebuilders.put(node, seed -> {
                        FogVolumeSim fresh = new FogVolumeSim(fc, node, new Random(seed));
                        fogs.set(at, fresh);
                        overrideTargets.put(node, fresh::setOverride);
                    });
                }
                default -> {
                }
            }
        }
        for (ParticleEmitterSim pe : particleEmitters) {
            pe.setSceneForceFields(forceFields, "");
        }
        sceneAge = 0;
        timelineSim = new TimelineSim(this, scene.timeline());
        timelineSim.begin();
        root.refresh();
    }

    SceneNode node(String id) {
        return id == null ? null : nodes.get(id);
    }

    Random random() {
        return random;
    }

    void setOverride(SceneNode node, String path, Object value) {
        ParticleEmitterSim pe = emitterByNode.get(node);
        if (pe != null) {
            pe.setOverride(path, value);
            return;
        }
        java.util.function.BiConsumer<String, Object> target = overrideTargets.get(node);
        if (target != null) {
            target.accept(path, value);
        }
    }

    void resetSubtree(SceneNode node, long seed) {
        node.resetTimelineState();
        Consumer<Long> rebuild = rebuilders.get(node);
        if (rebuild != null) {
            rebuild.accept(seed);
        }
        for (SceneNode child : new ArrayList<>(node.children())) {
            resetSubtree(child, seed);
        }
    }

    public void tick(float dt) {
        float remaining = dt;
        while (remaining > 1f) {
            tickStep(1f);
            remaining -= 1f;
        }
        if (remaining > 0f) {
            tickStep(remaining);
        }
    }

    private void tickStep(float dt) {
        sceneAge += dt;
        timelineSim.tick(dt);
        root.refresh();
        for (ParticleEmitterSim pe : new ArrayList<>(particleEmitters)) {
            stepObject(pe.node(), dt, pe::beginTick, pe::tick);
        }
        for (BeamSim b : beams) {
            stepObject(b.node(), dt, x -> {
            }, b::tick);
        }
        for (TrailEmitterSim t : trails) {
            stepObject(t.node(), dt, x -> {
            }, t::tick);
        }
        for (AraTrailSim a : araTrails) {
            stepObject(a.node(), dt, x -> {
            }, a::tick);
        }
        for (LightSim l : lights) {
            stepObject(l.node(), dt, x -> {
            }, l::tick);
        }
        for (FogVolumeSim f : fogs) {
            stepObject(f.node(), dt, x -> {
            }, f::tick);
        }

        for (FxSceneSimulation child : subInstances) {
            child.tickStep(dt);
        }
        subInstances.removeIf(FxSceneSimulation::isEverythingFinished);

        drainAndSpawnSubEmitters();

        if (autoRestart && isEverythingFinished()) {
            restart();
        }
    }

    private void drainAndSpawnSubEmitters() {
        for (ParticleEmitterSim pe : particleEmitters) {
            for (SubEmitterSpawnRequest request : pe.drainSubEmitterSpawns()) {
                trySpawnSubInstance(request);
            }
        }
    }

    private void trySpawnSubInstance(SubEmitterSpawnRequest request) {
        if (depth >= MAX_SUB_EMITTER_DEPTH || globalSubInstanceCount[0] >= MAX_TOTAL_SUB_INSTANCES) {
            return;
        }
        FxScene childScene = subEffectResolver.apply(request.fxId());
        if (childScene == null) {
            return;
        }
        globalSubInstanceCount[0]++;
        FxSceneSimulation child = new FxSceneSimulation(childScene, random.nextLong(), meshResolver, subEffectResolver,
                false, request.worldPosition(), depth + 1, globalSubInstanceCount);
        SubEmitterDef def = request.def();
        for (ParticleEmitterSim childEmitter : child.particleEmitters()) {
            if (def.inheritLifetime()) {
                childEmitter.setEmitterAge(request.fatherAge());
            }
            if (def.inheritDuration()) {
                childEmitter.setOverride("duration", Math.round(request.fatherLifetime()));
            }
            if (def.inheritColor()) {
                childEmitter.setColorTint(request.fatherColor());
            }
            SceneNode childNode = childEmitter.node();
            if (def.inheritSize()) {
                Vec3 s = childNode.local().scale();
                childNode.setLocalScale(s.x * request.fatherSize().x, s.y * request.fatherSize().y,
                        s.z * request.fatherSize().z);
            }
            if (def.inheritRotation()) {
                Vec3 fatherRotation = request.fatherRotationRad();

                childNode.setLocalRotation(Quat.rotationXYZ(fatherRotation.x, fatherRotation.y, fatherRotation.z));
            }
        }
        child.root.refresh();
        subInstances.add(child);
    }

    public void simulateTo(float targetTicks) {
        restart();
        float remaining = Math.max(0f, targetTicks);
        int steps = 0;
        while (remaining > 0f && steps < 200_000) {
            float step = Math.min(1f, remaining);
            tick(step);
            remaining -= step;
            steps++;
        }
    }

    private boolean isEverythingFinished() {
        if (sceneAge < 1f) {
            return false;
        }
        if (!subInstances.isEmpty()) {
            return false;
        }
        boolean timelineDone = timelineSim.isFinished();
        for (ParticleEmitterSim pe : particleEmitters) {
            if (!pe.isFinishedForGood() && !(timelineDone && !pe.node().isActive())) {
                return false;
            }
        }
        for (BeamSim b : beams) {
            if (!b.isFinishedForGood() && !(timelineDone && !b.node().isActive())) {
                return false;
            }
        }
        for (TrailEmitterSim t : trails) {
            if (!t.isFinishedForGood() && !(timelineDone && !t.node().isActive())) {
                return false;
            }
        }
        for (AraTrailSim a : araTrails) {
            if (!a.isFinishedForGood() && !(timelineDone && !a.node().isActive())) {
                return false;
            }
        }
        for (LightSim l : lights) {
            if (!l.isFinishedForGood() && !(timelineDone && !l.node().isActive())) {
                return false;
            }
        }
        for (FogVolumeSim f : fogs) {
            if (!f.isFinishedForGood() && !(timelineDone && !f.node().isActive())) {
                return false;
            }
        }
        return !particleEmitters.isEmpty() || !beams.isEmpty() || !trails.isEmpty() || !araTrails.isEmpty()
                || !lights.isEmpty() || !fogs.isEmpty();
    }

    public List<ParticleEmitterSim> particleEmitters() {
        return particleEmitters;
    }

    public List<BeamSim> beams() {
        return beams;
    }

    public List<TrailEmitterSim> trails() {
        return trails;
    }

    public List<ForceFieldSim> forceFields() {
        return forceFields;
    }

    public List<AraTrailSim> araTrails() {
        return araTrails;
    }

    public List<LightSim> lights() {
        return lights;
    }

    public List<FogVolumeSim> fogVolumes() {
        return fogs;
    }

    public List<LightSim> allLightsIncludingSubInstances() {
        List<LightSim> all = new ArrayList<>(lights);
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allLightsIncludingSubInstances());
        }
        return all;
    }

    public List<FogVolumeSim> allFogVolumesIncludingSubInstances() {
        List<FogVolumeSim> all = new ArrayList<>(fogs);
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allFogVolumesIncludingSubInstances());
        }
        return all;
    }

    public SceneLighting collectLighting() {
        List<SceneLighting.Light> lightList = new ArrayList<>();
        for (LightSim l : allLightsIncludingSubInstances()) {
            SceneLighting.Light light = l.sample();
            if (light != null) {
                lightList.add(light);
            }
        }
        for (ParticleEmitterSim pe : allParticleEmittersIncludingSubInstances()) {
            pe.submitLights(lightList);
        }
        List<SceneLighting.Fog> fogList = new ArrayList<>();
        for (FogVolumeSim f : allFogVolumesIncludingSubInstances()) {
            SceneLighting.Fog fog = f.sample();
            if (fog != null) {
                fogList.add(fog);
            }
        }
        if (lightList.isEmpty() && fogList.isEmpty()) {
            return null;
        }
        return new SceneLighting(lightList, fogList);
    }

    public List<ParticleEmitterSim> allParticleEmittersIncludingSubInstances() {
        List<ParticleEmitterSim> all = new ArrayList<>(particleEmitters);
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allParticleEmittersIncludingSubInstances());
        }
        return all;
    }

    public List<BeamSim> allBeamsIncludingSubInstances() {
        List<BeamSim> all = new ArrayList<>(beams);
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allBeamsIncludingSubInstances());
        }
        return all;
    }

    public List<TrailEmitterSim> allTrailsIncludingSubInstances() {
        List<TrailEmitterSim> all = new ArrayList<>(trails);
        for (ParticleEmitterSim pe : particleEmitters) {
            all.addAll(pe.carriedTrails());
        }
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allTrailsIncludingSubInstances());
        }
        return all;
    }

    public List<ForceFieldSim> allForceFieldsIncludingSubInstances() {
        List<ForceFieldSim> all = new ArrayList<>(forceFields);
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allForceFieldsIncludingSubInstances());
        }
        return all;
    }

    public List<AraTrailSim> allAraTrailsIncludingSubInstances() {
        List<AraTrailSim> all = new ArrayList<>(araTrails);
        for (ParticleEmitterSim pe : particleEmitters) {
            all.addAll(pe.carriedAraTrails());
        }
        for (FxSceneSimulation child : subInstances) {
            all.addAll(child.allAraTrailsIncludingSubInstances());
        }
        return all;
    }
}

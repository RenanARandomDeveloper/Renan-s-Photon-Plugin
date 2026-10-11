package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Timeline;

import java.util.ArrayList;
import java.util.List;

public final class FxNbtParser {
    private FxNbtParser() {
    }

    public static boolean looksLikeNeo(NbtTag.CompoundTag root) {
        if (root == null) {
            return false;
        }
        return root.has("fxData") || root.has("meta") || root.getCompound("data").getCompound("fx").has("fxData");
    }

    public static FxScene parseScene(NbtTag.CompoundTag root) {
        int version = root.getInt("version", 0);
        NbtTag.CompoundTag fxRoot = root.get("version") instanceof NbtTag.IntTag(int value)
                ? FxDataFixer.fix(root, value)
                : root;
        return parseFx(fxRoot, version);
    }

    public static FxScene parseProject(NbtTag.CompoundTag projectRoot) {
        int version = Math.max(1, projectRoot.getCompound("meta").getInt("version_num", 0));
        NbtTag.CompoundTag fx = projectRoot.getCompound("data").getCompound("fx");
        return parseFx(FxDataFixer.fix(fx, version), version);
    }

    private static FxScene parseFx(NbtTag.CompoundTag fxRoot, int version) {
        NbtTag.CompoundTag fxData = fxRoot.getCompound("fxData");
        NbtTag.ListTag fxObjects = ParseUtil.resolveList(fxData, "fxObjects");
        List<FxObject> objects = new ArrayList<>();
        for (int i = 0; i < fxObjects.size(); i++) {
            if (fxObjects.get(i) instanceof NbtTag.CompoundTag entry) {
                objects.add(parseObject(entry));
            }
        }
        Timeline timeline = fxData.get("timeline") instanceof NbtTag.CompoundTag timelineTag
                ? TimelineParser.parse(timelineTag)
                : new Timeline();
        return new FxScene(version, objects, timeline);
    }

    private static FxObject parseObject(NbtTag.CompoundTag entry) {
        String type = entry.getString("type", "");
        NbtTag.CompoundTag data = entry.getCompound("data");
        String name = data.getString("name", type);
        NbtTag.CompoundTag transformTag = data.getCompound("transform");
        Transform transform = parseTransform(transformTag);
        String id = transformTag.getString("id", "");
        String parentId = transformTag.getString("_parentId", "");

        NbtTag.CompoundTag config = data.getCompound("config");
        FxObjectData objectData = switch (type) {
            case "particle_emitter" -> parseParticleEmitter(config);
            case "beam_emitter" -> parseBeam(config);
            case "force_field" -> parseForceField(config);
            case "trail_emitter" -> parseTrail(config);
            case "ara_trail_emitter" -> parseAraTrailEmitter(config);
            case "light" -> parseLight(config);
            case "fog_volume" -> parseFogVolume(config);
            default -> new UnknownObjectData(type);
        };
        return new FxObject(id, name, parentId, transform, objectData);
    }

    private static Transform parseTransform(NbtTag.CompoundTag t) {
        Vec3 pos = parseVec3(t.getList("localPosition"), 0, 0, 0);
        Vec3 scale = parseVec3(t.getList("localScale"), 1, 1, 1);
        NbtTag.ListTag rotList = t.getList("localRotation");
        Quat rot = rotList.size() >= 4
                ? new Quat(numAt(rotList, 0, 0), numAt(rotList, 1, 0), numAt(rotList, 2, 0), numAt(rotList, 3, 1))
                : Quat.identity();
        return new Transform(pos, rot, scale);
    }

    private static Vec3 parseVec3(NbtTag.ListTag list, float fx, float fy, float fz) {
        if (list.size() < 3) {
            return new Vec3(fx, fy, fz);
        }
        return new Vec3(numAt(list, 0, fx), numAt(list, 1, fy), numAt(list, 2, fz));
    }

    private static float numAt(NbtTag.ListTag list, int index, float fallback) {
        if (index >= list.size()) {
            return fallback;
        }
        return switch (list.get(index)) {
            case NbtTag.FloatTag f -> f.value();
            case NbtTag.DoubleTag d -> (float) d.value();
            case NbtTag.IntTag i -> i.value();
            default -> fallback;
        };
    }

    private static ParticleEmitterConfig parseParticleEmitter(NbtTag.CompoundTag c) {
        ParticleEmitterConfig cfg = new ParticleEmitterConfig();
        cfg.duration = c.getInt("duration", 100);
        cfg.looping = c.getBoolean("looping", true);
        cfg.prewarm = c.getInt("prewarm", 0);
        cfg.simulationSpace = switch (c.getString("simulationSpace", "Local")) {
            case "World" -> ParticleEmitterConfig.SimulationSpace.WORLD;
            case "Custom" -> ParticleEmitterConfig.SimulationSpace.CUSTOM;
            default -> ParticleEmitterConfig.SimulationSpace.LOCAL;
        };
        cfg.customSpaceId = c.getString("customSpace", "");
        cfg.maxParticles = c.getInt("maxParticles", 2000);
        cfg.startDelay = NumberFunctionParser.parse(c.get("startDelay"), 0);
        cfg.startLifetime = NumberFunctionParser.parse(c.get("startLifetime"), 100);
        cfg.startSpeed = NumberFunctionParser.parse(c.get("startSpeed"), 1);
        cfg.startSize = NumberFunctionParser.parse3(c.get("startSize"), 0.1, 0.1, 0.1);
        cfg.startRotation = NumberFunctionParser.parse3(c.get("startRotation"), 0, 0, 0);
        cfg.startColor = NumberFunctionParser.parse(c.get("startColor"), -1);

        cfg.shape = ShapeParser.parseShapeSetting(c.getCompound("shape"));
        cfg.emission = parseEmission(c.getCompound("emission"));
        cfg.renderer = parseParticleRenderer(c.getCompound("renderer"));

        NbtTag.CompoundTag col = c.getCompound("colorOverLifetime");
        cfg.colorOverLifetime = new ColorOverLifetime(col.getBoolean("_enable", false),
                NumberFunctionParser.parse(col.get("color"), -1));

        NbtTag.CompoundTag siz = c.getCompound("sizeOverLifetime");
        cfg.sizeOverLifetime = new SizeOverLifetime(siz.getBoolean("_enable", false),
                NumberFunctionParser.parse3(siz.get("size"), 1, 1, 1));

        NbtTag.CompoundTag rot = c.getCompound("rotationOverLifetime");
        cfg.rotationOverLifetime = new RotationOverLifetime(rot.getBoolean("_enable", false),
                NumberFunctionParser.parse(rot.get("yaw"), 0), NumberFunctionParser.parse(rot.get("pitch"), 0),
                NumberFunctionParser.parse(rot.get("roll"), 0),
                ValueSpace.parse(rot.getString("space", "Local"), ValueSpace.LOCAL), rot.getString("customSpace", ""));

        NbtTag.CompoundTag cbs = c.getCompound("colorBySpeed");
        cfg.colorBySpeed = new ColorBySpeed(cbs.getBoolean("_enable", false),
                NumberFunctionParser.parse(cbs.get("color"), -1), parseSpeedRange(cbs));

        NbtTag.CompoundTag sbs = c.getCompound("sizeBySpeed");
        cfg.sizeBySpeed = new SizeBySpeed(sbs.getBoolean("_enable", false),
                NumberFunctionParser.parse3(sbs.get("size"), 1, 1, 1), parseSpeedRange(sbs));

        NbtTag.CompoundTag rbs = c.getCompound("rotationBySpeed");
        cfg.rotationBySpeed = new RotationBySpeed(rbs.getBoolean("_enable", false),
                NumberFunctionParser.parse(rbs.get("yaw"), 0), NumberFunctionParser.parse(rbs.get("pitch"), 0),
                NumberFunctionParser.parse(rbs.get("roll"), 0), parseSpeedRange(rbs));

        NbtTag.CompoundTag vol = c.getCompound("velocityOverLifetime");
        cfg.velocityOverLifetime = new VelocityOverLifetime(vol.getBoolean("_enable", false),
                NumberFunctionParser.parse3(vol.get("linear"), 0, 0, 0),
                parseOrbitalMode(vol.getString("orbitalMode", "AngularVelocity")),
                NumberFunctionParser.parse3(vol.get("orbital"), 0, 0, 0),
                NumberFunctionParser.parse3(vol.get("offset"), 0, 0, 0),
                NumberFunctionParser.parse(vol.get("radial"), 0),
                NumberFunctionParser.parse(vol.get("speedModifier"), 1),
                ValueSpace.parse(vol.getString("space", "Local"), ValueSpace.LOCAL), vol.getString("customSpace", ""));

        NbtTag.CompoundTag lights = c.getCompound("lights");
        cfg.lights = lights.has("_enable")
                ? new LightOverLifetime(lights.getBoolean("_enable", true),
                NumberFunctionParser.parse(lights.get("skyLight"), 15),
                NumberFunctionParser.parse(lights.get("blockLight"), 15))
                : LightOverLifetime.fullBright();

        cfg.lightEmission = parseLightEmission(c.getCompound("lightEmission"));

        NbtTag.CompoundTag uv = c.getCompound("uvAnimation");
        cfg.uvAnimation = parseUvAnimation(uv);

        NbtTag.CompoundTag iv = c.getCompound("inheritVelocity");
        cfg.inheritVelocity = new InheritVelocity(iv.getBoolean("_enable", false),
                "INITIAL".equals(iv.getString("mode", "INITIAL")) ? InheritVelocity.Mode.INITIAL : InheritVelocity.Mode.CURRENT,
                NumberFunctionParser.parse(iv.get("multiply"), 1));

        NbtTag.CompoundTag phys = c.getCompound("physics");
        cfg.physics = new Physics(phys.getBoolean("_enable", false),
                NumberFunctionParser.parse(phys.get("friction"), 1), NumberFunctionParser.parse(phys.get("gravity"), 0));

        NbtTag.CompoundTag fol = c.getCompound("forceOverLifetime");
        cfg.forceOverLifetime = new ForceOverLifetime(fol.getBoolean("_enable", false),
                NumberFunctionParser.parse3(fol.get("force"), 0, 0, 0),
                ValueSpace.parse(fol.getString("simulationSpace", "Local"), ValueSpace.LOCAL),
                fol.getString("customSpace", ""));

        NbtTag.CompoundTag ext = c.getCompound("externalForces");
        cfg.externalForces = new ExternalForces(ext.getBoolean("_enable", false),
                NumberFunctionParser.parse(ext.get("multiplier"), 1),
                "List".equals(ext.getString("influenceFilter", "SceneAll")) ? ExternalForces.Filter.LIST : ExternalForces.Filter.SCENE_ALL,
                parseInfluenceIds(ext.getCompound("influenceList")));

        NbtTag.CompoundTag noise = c.getCompound("noise");
        NbtTag.CompoundTag remap = noise.getCompound("remap");
        cfg.noise = new Noise(noise.getBoolean("_enable", false), noise.getFloat("frequency", 1f),
                parseNoiseQuality(noise.getString("quality", "Noise2D")), remap.getBoolean("_enable", false),
                NumberFunctionParser.parse(remap.get("remapCurve"), 0.5), NumberFunctionParser.parse3(noise.get("position"), 0, 0, 0),
                NumberFunctionParser.parse(noise.get("rotation"), 0), NumberFunctionParser.parse(noise.get("size"), 0));

        NbtTag.CompoundTag lbes = c.getCompound("lifetimeByEmitterSpeed");
        cfg.lifetimeByEmitterSpeed = new LifetimeByEmitterSpeed(lbes.getBoolean("_enable", false),
                NumberFunctionParser.parse(lbes.get("multiplier"), 1), parseSpeedRange(lbes));

        cfg.trails = parseTrailsOnParticle(c.getCompound("trails"));
        cfg.subEmitters = parseSubEmitters(c.getCompound("subEmitters"));
        return cfg;
    }

    private static SubEmittersSetting parseSubEmitters(NbtTag.CompoundTag s) {
        List<SubEmitterDef> defs = new ArrayList<>();

        NbtTag.ListTag emitters = ParseUtil.resolveList(s, "emitters");
        for (int i = 0; i < emitters.size(); i++) {
            if (emitters.get(i) instanceof NbtTag.CompoundTag e) {
                String fxLocation = e.getString("fxLocation", "");
                SubEmitterDef.Event event = switch (e.getString("event", "Birth")) {
                    case "Birth" -> SubEmitterDef.Event.BIRTH;
                    case "Death" -> SubEmitterDef.Event.DEATH;
                    case "Tick" -> SubEmitterDef.Event.TICK;
                    default -> SubEmitterDef.Event.UNSUPPORTED;
                };
                defs.add(new SubEmitterDef(fxLocation.isEmpty() ? null : fxLocation, event,
                        NumberFunctionParser.parse(e.get("emitProbability"), 0), Math.max(1, e.getInt("tickInterval", 1)),
                        e.getBoolean("inheritColor", false), e.getBoolean("inheritSize", false),
                        e.getBoolean("inheritRotation", false), e.getBoolean("inheritLifetime", false),
                        e.getBoolean("inheritDuration", false)));
            }
        }
        return new SubEmittersSetting(s.getBoolean("_enable", false), defs);
    }

    private static UvAnimation parseUvAnimation(NbtTag.CompoundTag uv) {
        boolean enable = uv.getBoolean("_enable", false);
        int[] tiles = parseTiles(uv.get("tiles"));
        UvAnimation.Animation animation =
                "SingleRow".equals(uv.getString("animation", "WholeSheet"))
                        ? UvAnimation.Animation.SINGLE_ROW
                        : UvAnimation.Animation.WHOLE_SHEET;
        NumberFunction frameOverTime = NumberFunctionParser.parse(uv.get("frameOverTime"), 0);
        NumberFunction startFrame = NumberFunctionParser.parse(uv.get("startFrame"), 0);
        float cycle = uv.getFloat("cycle", 1);
        return new UvAnimation(enable, tiles[0], tiles[1], animation, frameOverTime, startFrame, cycle);
    }

    private static int[] parseTiles(NbtTag tag) {
        if (tag instanceof NbtTag.ListTag list && list.size() >= 2) {
            int x = intAt(list, 0);
            int y = intAt(list, 1);
            return new int[]{Math.max(1, x), Math.max(1, y)};
        }
        if (tag instanceof NbtTag.IntArrayTag(int[] value) && value.length >= 2) {
            return new int[]{Math.max(1, value[0]), Math.max(1, value[1])};
        }
        return new int[]{1, 1};
    }

    private static int intAt(NbtTag.ListTag list, int index) {
        return switch (list.get(index)) {
            case NbtTag.IntTag i -> i.value();
            case NbtTag.ShortTag s -> s.value();
            case NbtTag.ByteTag b -> b.value();
            case NbtTag.FloatTag f -> (int) f.value();
            case NbtTag.DoubleTag d -> (int) d.value();
            default -> 1;
        };
    }

    private static Range parseSpeedRange(NbtTag.CompoundTag c) {
        NbtTag.CompoundTag range = c.getCompound("speedRange");
        if (range.has("a") || range.has("b")) {
            return new Range(range.getFloat("a", 0), range.getFloat("b", 1));
        }
        return new Range(c.getFloat("rangeA", 0), c.getFloat("rangeB", 1));
    }

    private static VelocityOverLifetime.OrbitalMode parseOrbitalMode(String s) {
        return switch (s) {
            case "LinearVelocity" -> VelocityOverLifetime.OrbitalMode.LINEAR_VELOCITY;
            case "FixedVelocity" -> VelocityOverLifetime.OrbitalMode.FIXED_VELOCITY;
            default -> VelocityOverLifetime.OrbitalMode.ANGULAR_VELOCITY;
        };
    }

    private static List<String> parseInfluenceIds(NbtTag.CompoundTag tag) {
        List<String> ids = new ArrayList<>();
        NbtTag.ListTag uid = tag.getList("uid");
        for (int i = 0; i < uid.size(); i++) {
            if (uid.get(i) instanceof NbtTag.StringTag(String value) && !value.isEmpty()) {
                ids.add(value);
            }
        }
        return ids;
    }

    private static Noise.Quality parseNoiseQuality(String s) {
        return switch (s) {
            case "Noise1D", "NOISE_1D" -> Noise.Quality.NOISE_1D;
            case "Noise3D", "NOISE_3D" -> Noise.Quality.NOISE_3D;
            default -> Noise.Quality.NOISE_2D;
        };
    }

    private static TrailsOnParticle parseTrailsOnParticle(NbtTag.CompoundTag trailsTag) {
        boolean enable = trailsTag.getBoolean("_enable", false);

        TrailsOnParticle.Type type = "ARA_TRAIL".equals(trailsTag.getString("trailType", "TRAIL"))
                ? TrailsOnParticle.Type.ARA_TRAIL : TrailsOnParticle.Type.TRAIL;
        return new TrailsOnParticle(enable,
                trailsTag.getFloat("ratio", 1f),
                NumberFunctionParser.parse(trailsTag.get("lifetime"), 1),
                trailsTag.getBoolean("dieWithParticles", false),
                trailsTag.getBoolean("sizeAffectsWidth", true),
                trailsTag.getBoolean("sizeAffectsLifetime", false),
                trailsTag.getBoolean("inheritParticleColor", true),
                NumberFunctionParser.parse(trailsTag.get("colorOverLifetime"), -1),
                type,
                parseTrail(trailsTag.getCompound("config"), 0.5),
                parseAraTrailEmitter(trailsTag.getCompound("araConfig")));
    }

    private static EmissionSetting parseEmission(NbtTag.CompoundTag e) {
        NumberFunction rate = NumberFunctionParser.parse(e.get("emissionRate"), 0.5);
        NumberFunction distanceRate = NumberFunctionParser.parse(e.get("distanceRate"), 0);
        EmissionSetting.Mode mode = "Random".equals(e.getString("emissionMode", "Exacting"))
                ? EmissionSetting.Mode.RANDOM : EmissionSetting.Mode.EXACTING;
        NbtTag.ListTag burstsList = ParseUtil.resolveList(e, "bursts");
        List<EmissionSetting.Burst> bursts = new ArrayList<>();
        for (int i = 0; i < burstsList.size(); i++) {
            if (burstsList.get(i) instanceof NbtTag.CompoundTag b) {
                bursts.add(new EmissionSetting.Burst(b.getInt("time", 0), NumberFunctionParser.parse(b.get("count"), 50),
                        b.getInt("cycles", 1), Math.max(1, b.getInt("interval", 1)), b.getFloat("probability", 1)));
            }
        }
        return new EmissionSetting(rate, distanceRate, mode, bursts);
    }

    private static ParticleRendererSetting parseParticleRenderer(NbtTag.CompoundTag r) {
        RenderMode renderMode = switch (r.getString("renderMode", "Billboard")) {
            case "Horizontal" -> RenderMode.HORIZONTAL;
            case "Vertical" -> RenderMode.VERTICAL;
            case "VerticalBillboard" -> RenderMode.VERTICAL_BILLBOARD;
            case "StretchedBillboard" -> RenderMode.STRETCHED_BILLBOARD;
            case "Model" -> RenderMode.MODEL;
            case "None" -> RenderMode.NONE;
            default -> RenderMode.BILLBOARD;
        };
        FacingMode facingMode = switch (r.getString("facingMode", "DEFAULT")) {
            case "ROTATE_Y" -> FacingMode.ROTATE_Y;
            case "LOOKAT_Y" -> FacingMode.LOOKAT_Y;
            case "LOOKAT_XYZ" -> FacingMode.LOOKAT_XYZ;
            case "LOOKAT_DIRECTION" -> FacingMode.LOOKAT_DIRECTION;
            case "EMITTER_TRANSFORM_XY" -> FacingMode.EMITTER_TRANSFORM_XY;
            case "EMITTER_TRANSFORM_XZ" -> FacingMode.EMITTER_TRANSFORM_XZ;
            case "EMITTER_TRANSFORM_YZ" -> FacingMode.EMITTER_TRANSFORM_YZ;
            case "DIRECTION_X" -> FacingMode.DIRECTION_X;
            case "DIRECTION_Y" -> FacingMode.DIRECTION_Y;
            case "DIRECTION_Z" -> FacingMode.DIRECTION_Z;
            default -> FacingMode.DEFAULT;
        };
        NbtTag.CompoundTag fd = r.getCompound("facingDirection");
        FacingDirection facingDirection = new FacingDirection(
                !"CUSTOM_DIRECTION".equals(fd.getString("mode", "DERIVE_FROM_VELOCITY"))
                        ? FacingDirection.Mode.DERIVE_FROM_VELOCITY
                        : FacingDirection.Mode.CUSTOM,
                fd.getFloat("minSpeedThreshold", 0.01f), parseVec3(fd.getList("customDirection"), 0, 1, 0));
        Vec3 pivot = parseVec3(r.getList("modelPivot"), 0, 0, 0);
        ModelSource modelSource = parseModelSource(r.getCompound("model"));
        List<MaterialLayer> materials = MaterialParser.parseMaterials(r);
        return new ParticleRendererSetting(renderMode, facingMode, facingDirection, parseRenderSettings(r),
                r.getFloat("velocityScale", 0), r.getFloat("lengthScale", 2), pivot, modelSource, materials,
                r.getBoolean("shade", true));
    }

    private static RenderSettings parseRenderSettings(NbtTag.CompoundTag r) {
        RenderSettings.Layer layer = "Opaque".equals(r.getString("layer", "Translucent"))
                ? RenderSettings.Layer.OPAQUE : RenderSettings.Layer.TRANSLUCENT;
        RenderSettings.VertexSorting sorting = "DISTANCE".equalsIgnoreCase(r.getString("vertexSortingMode", "NONE"))
                ? RenderSettings.VertexSorting.DISTANCE : RenderSettings.VertexSorting.NONE;
        return new RenderSettings(layer, r.getInt("orderInLayer", 0), sorting);
    }

    public static ModelSource parseModelSource(NbtTag.CompoundTag modelTag) {
        if (modelTag.has("source")) {
            return parseModelSourceWrapper(modelTag.getCompound("source"));
        }
        if (modelTag.has("modelLocation")) {
            return new JsonModelSourceRef(
                    modelTag.getString("modelLocation", ""));
        }
        if (modelTag.has("type")) {
            return parseModelSourceWrapper(modelTag);
        }
        return new UnsupportedModelSource("none");
    }

    private static ModelSource parseModelSourceWrapper(NbtTag.CompoundTag source) {
        String type = source.getString("type", "");
        NbtTag.CompoundTag data = source.getCompound("data");
        if (type.equals("obj_model")) {
            return new ObjModelSourceRef(
                    data.getString("modelLocation", ""), data.getBoolean("flipV", true));
        }
        if (type.equals("json_model")) {
            return new JsonModelSourceRef(
                    data.getString("modelLocation", ""));
        }
        if (type.equals("gltf_model")) {
            return new GltfModelSourceRef(
                    data.getString("modelLocation", ""), data.getBoolean("flipV", false));
        }
        if (type.equals("resource_mesh")) {
            return new LibraryMeshRef(
                    data.getString("resourcePath", ""));
        }
        if (type.equals("animated_gltf_model")) {
            List<String> animationFiles = new ArrayList<>();
            NbtTag.ListTag files = data.getList("animationFiles");
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i) instanceof NbtTag.StringTag(String value)) {
                    animationFiles.add(value);
                }
            }
            AnimatedGltfModelSourceRef.PhaseSource phaseSource = "Lifetime".equals(data.getString("phaseSource", "Random"))
                    ? AnimatedGltfModelSourceRef.PhaseSource.LIFETIME : AnimatedGltfModelSourceRef.PhaseSource.RANDOM;
            return new AnimatedGltfModelSourceRef(data.getString("modelLocation", ""), data.getBoolean("flipV", false),
                    data.getString("animation", ""), data.getFloat("speed", 1f), data.getBoolean("loop", true),
                    data.getBoolean("perParticlePhase", false), phaseSource, animationFiles,
                    Math.max(1, data.getInt("frames", 30)), data.getBoolean("interpolate", true));
        }
        return new UnsupportedModelSource(type.isEmpty() ? "none" : type);
    }

    private static BeamConfig parseBeam(NbtTag.CompoundTag c) {
        BeamConfig cfg = new BeamConfig();
        cfg.duration = c.getInt("duration", 100);
        cfg.looping = c.getBoolean("looping", true);
        cfg.end = parseVec3(c.getList("end"), 0, 0, -3);
        cfg.width = NumberFunctionParser.parse(c.get("width"), 0.2);
        cfg.color = NumberFunctionParser.parse(c.get("color"), -1);
        cfg.emitRate = NumberFunctionParser.parse(c.get("emitRate"), 0);
        cfg.startDelay = c.getInt("startDelay", 0);
        cfg.uvAnimation = parseUvAnimation(c.getCompound("uvAnimation"));
        NbtTag.CompoundTag r = c.getCompound("renderer");
        cfg.materials = MaterialParser.parseMaterials(r);
        cfg.blendMode = cfg.materials.isEmpty() ? BlendMode.standardAlpha() : cfg.materials.get(0).blendMode();
        cfg.renderSettings = parseRenderSettings(r);
        return cfg;
    }

    private static TrailConfig parseTrail(NbtTag.CompoundTag c) {
        return parseTrail(c, 0.2);
    }

    private static TrailConfig parseTrail(NbtTag.CompoundTag c, double defaultWidth) {
        TrailConfig cfg = new TrailConfig();
        cfg.duration = c.getInt("duration", 100);
        cfg.looping = c.getBoolean("looping", true);
        cfg.startDelay = c.getInt("startDelay", 0);
        cfg.time = c.getInt("time", 20);
        cfg.minVertexDistance = c.getFloat("minVertexDistance", 0.05f);
        cfg.smoothInterpolation = c.getBoolean("smoothInterpolation", false);
        cfg.uvMode = "Tile".equalsIgnoreCase(c.getString("uvMode", "Stretch"))
                ? TrailConfig.UvMode.TILE : TrailConfig.UvMode.STRETCH;
        cfg.widthOverTrail = NumberFunctionParser.parse(c.get("widthOverTrail"), defaultWidth);
        cfg.colorOverTrail = NumberFunctionParser.parse(c.get("colorOverTrail"), -1);
        cfg.uvAnimation = parseUvAnimation(c.getCompound("uvAnimation"));
        NbtTag.CompoundTag r = c.getCompound("renderer");
        cfg.materials = MaterialParser.parseMaterials(r);
        cfg.blendMode = cfg.materials.isEmpty() ? BlendMode.standardAlpha() : cfg.materials.get(0).blendMode();
        cfg.renderSettings = parseRenderSettings(r);
        return cfg;
    }

    private static AraTrailConfig parseAraTrailEmitter(NbtTag.CompoundTag c) {
        AraTrailConfig cfg = new AraTrailConfig();
        cfg.duration = c.getInt("duration", 100);
        cfg.looping = c.getBoolean("looping", true);
        cfg.space = switch (c.getString("space", "World")) {
            case "Local" -> AraTrailConfig.Space.LOCAL;
            case "Custom" -> AraTrailConfig.Space.CUSTOM;
            default -> AraTrailConfig.Space.WORLD;
        };
        cfg.customSpaceId = c.getString("customSpace", "");
        cfg.alignment = switch (c.getString("alignment", "View")) {
            case "Velocity" -> AraTrailConfig.Alignment.VELOCITY;
            case "Local" -> AraTrailConfig.Alignment.LOCAL;
            default -> AraTrailConfig.Alignment.VIEW;
        };
        cfg.sorting = "NewerOnTop".equals(c.getString("sorting", "OlderOnTop"))
                ? AraTrailConfig.Sorting.NEWER_ON_TOP : AraTrailConfig.Sorting.OLDER_ON_TOP;
        cfg.smoothness = Math.max(1, c.getInt("smoothness", 1));
        cfg.smoothingDistance = c.getFloat("smoothingDistance", 0.05f);

        cfg.thickness = c.getFloat("thickness", 0.2f);
        cfg.thicknessOverLength = NumberFunctionParser.parse(c.get("thicknessOverLength"), 1);
        cfg.colorOverLength = NumberFunctionParser.parse(c.get("colorOverLength"), -1);
        cfg.thicknessOverTime = NumberFunctionParser.parse(c.get("thicknessOverTime"), 1);
        cfg.thicknessOverSegmentTime = NumberFunctionParser.parse(c.get("thicknessOverSegmentTime"), 1);
        cfg.colorOverTime = NumberFunctionParser.parse(c.get("colorOverTime"), -1);
        cfg.colorOverSegmentTime = NumberFunctionParser.parse(c.get("colorOverSegmentTime"), -1);

        cfg.emit = c.getBoolean("emit", true);
        cfg.initialThickness = c.getFloat("initialThickness", 1);
        cfg.initialColor = c.getInt("initialColor", -1);
        cfg.initialVelocity = parseVec3(c.getList("initialVelocity"), 0, 0, 0);
        cfg.timeInterval = c.getFloat("timeInterval", 0.05f);
        cfg.minDistance = c.getFloat("minDistance", 0.025f);
        cfg.time = c.getFloat("time", 1f);

        NbtTag.CompoundTag ph = c.getCompound("physicsSetting");
        ValueSpace gravitySpace;
        String gravityCustomSpace;
        if (ph.has("gravitySpace")) {
            gravitySpace = ValueSpace.parse(ph.getString("gravitySpace", "World"), ValueSpace.WORLD);
            gravityCustomSpace = ph.getString("gravityCustomSpace", "");
        } else {
            gravitySpace = switch (cfg.space) {
                case LOCAL -> ValueSpace.LOCAL;
                case CUSTOM -> ValueSpace.CUSTOM;
                default -> ValueSpace.WORLD;
            };
            gravityCustomSpace = cfg.space == AraTrailConfig.Space.CUSTOM ? cfg.customSpaceId : "";
        }
        cfg.physics = new AraTrailPhysics(ph.getBoolean("_enable", false), ph.getFloat("warmup", 0),
                parseVec3(ph.getList("gravity"), 0, 0, 0), gravitySpace, gravityCustomSpace, ph.getFloat("inertia", 0),
                ph.getFloat("velocitySmoothing", 0.75f), ph.getFloat("damping", 0.75f));

        cfg.textureMode = switch (c.getString("textureMode", "Stretch")) {
            case "Tile" -> AraTrailConfig.TextureMode.TILE;
            case "WorldTile" -> AraTrailConfig.TextureMode.WORLD_TILE;
            default -> AraTrailConfig.TextureMode.STRETCH;
        };
        cfg.uvFactor = c.getFloat("uvFactor", 1);
        cfg.uvWidthFactor = c.getFloat("uvWidthFactor", 1);
        cfg.tileAnchor = c.getFloat("tileAnchor", 1);

        NbtTag.CompoundTag r = c.getCompound("renderer");
        cfg.materials = MaterialParser.parseMaterials(r);
        cfg.renderSettings = parseRenderSettings(r);
        return cfg;
    }

    private static LightEmission parseLightEmission(NbtTag.CompoundTag le) {
        if (!le.has("_enable")) {
            return LightEmission.DISABLED;
        }
        NbtTag.CompoundTag spot = le.getCompound("spot");
        NbtTag.CompoundTag vol = le.getCompound("volumetric");
        LightEmission.Spot spotCfg = new LightEmission.Spot(spot.getBoolean("_enable", false),
                spot.getFloat("innerAngle", 20f), spot.getFloat("outerAngle", 35f),
                spot.getBoolean("alignToVelocity", true), parseVec3(spot.getList("direction"), 0, -1, 0),
                ValueSpace.parse(spot.getString("space", "Local"), ValueSpace.LOCAL), spot.getString("customSpace", ""));
        return new LightEmission(le.getBoolean("_enable", false), le.getFloat("ratio", 1f), le.getInt("maxLights", 32),
                le.getBoolean("useParticleColor", true), NumberFunctionParser.parse(le.get("color"), -1),
                NumberFunctionParser.parse(le.get("intensity"), 4), NumberFunctionParser.parse(le.get("range"), 4),
                le.getBoolean("alphaAffectsIntensity", true), le.getBoolean("sizeAffectsRange", false),
                le.getBoolean("castShadows", false), le.getFloat("sourceRadius", 0.1f), spotCfg,
                vol.getBoolean("_enable", false), NumberFunctionParser.parse(vol.get("strength"), 1));
    }

    private static LightConfig parseLight(NbtTag.CompoundTag c) {
        LightConfig cfg = new LightConfig();
        cfg.type = "Spot".equals(c.getString("type", "Point")) ? LightConfig.Type.SPOT : LightConfig.Type.POINT;
        cfg.lifetime = Math.max(1, c.getInt("lifetime", 20));
        cfg.looping = c.getBoolean("looping", true);
        cfg.color = NumberFunctionParser.parse(c.get("color"), -1);
        cfg.intensity = NumberFunctionParser.parse(c.get("intensity"), 10);
        cfg.range = NumberFunctionParser.parse(c.get("range"), 10);
        cfg.innerAngle = c.getFloat("innerAngle", 20f);
        cfg.outerAngle = c.getFloat("outerAngle", 35f);
        cfg.sourceRadius = c.getFloat("sourceRadius", 0.15f);
        cfg.castShadows = c.getBoolean("castShadows", true);
        cfg.flicker = c.getFloat("flicker", 0f);
        cfg.flickerSpeed = c.getFloat("flickerSpeed", 8f);
        NbtTag.CompoundTag vol = c.getCompound("volumetric");
        cfg.volumetricEnable = vol.getBoolean("_enable", false);
        cfg.volumetricStrength = NumberFunctionParser.parse(vol.get("strength"), 1);
        return cfg;
    }

    private static FogVolumeConfig parseFogVolume(NbtTag.CompoundTag c) {
        FogVolumeConfig cfg = new FogVolumeConfig();
        cfg.shape = "Sphere".equals(c.getString("shape", "Box")) ? FogVolumeConfig.Shape.SPHERE : FogVolumeConfig.Shape.BOX;
        cfg.lifetime = Math.max(1, c.getInt("lifetime", 100));
        cfg.looping = c.getBoolean("looping", true);
        cfg.density = NumberFunctionParser.parse(c.get("density"), 1);
        cfg.edgeFalloff = c.getFloat("edgeFalloff", 0.3f);
        cfg.color = NumberFunctionParser.parse(c.get("color"), -1);
        cfg.absorption = c.getFloat("absorption", 0.5f);
        cfg.emission = NumberFunctionParser.parse(c.get("emission"), 0xFF000000);
        NbtTag.CompoundTag noise = c.getCompound("noise");
        cfg.noiseEnable = noise.getBoolean("_enable", false);
        cfg.noiseStrength = noise.getFloat("strength", 0.6f);
        cfg.noiseScale = noise.getFloat("scale", 2f);
        cfg.noiseWind = parseVec3(noise.getList("wind"), 0.5f, 0f, 0.2f);
        return cfg;
    }

    private static ForceFieldConfig parseForceField(NbtTag.CompoundTag c) {
        ForceFieldConfig.Shape shape = switch (c.getString("shape", "Sphere")) {
            case "Hemisphere" -> ForceFieldConfig.Shape.HEMISPHERE;
            case "Cylinder" -> ForceFieldConfig.Shape.CYLINDER;
            case "Box" -> ForceFieldConfig.Shape.BOX;
            default -> ForceFieldConfig.Shape.SPHERE;
        };
        return new ForceFieldConfig(shape, c.getFloat("startRange", 0), c.getFloat("endRange", 5),
                NumberFunctionParser.parse(c.get("directionX"), 0), NumberFunctionParser.parse(c.get("directionY"), 0),
                NumberFunctionParser.parse(c.get("directionZ"), 0), NumberFunctionParser.parse(c.get("gravity"), 0),
                c.getFloat("gravityFocus", 0), NumberFunctionParser.parse(c.get("rotationSpeed"), 0),
                c.getFloat("rotationAttraction", 0), c.getFloat("rotationRandomnessX", 0), c.getFloat("rotationRandomnessZ", 0),
                NumberFunctionParser.parse(c.get("drag"), 0), c.getBoolean("multiplyDragByParticleSize", false),
                c.getBoolean("multiplyDragByParticleVelocity", false));
    }
}

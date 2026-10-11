package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.TextureMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.UnsupportedMaterial;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag.CompoundTag;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag.ListTag;
import net.renan.photonplugin.menus.resource.preview.forge.FNum.FNum3;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.*;

import java.util.List;
import java.util.Locale;

public final class ForgeParser {
    private static final String LASER = "photon:textures/particle/laser.png";

    private ForgeParser() {
    }

    public static boolean looksLikeForge(CompoundTag root) {
        if (root == null) {
            return false;
        }
        CompoundTag fx = root.getCompound("fx");
        if (fx.has("mainFX") || fx.has("subFXs")) {
            return true;
        }
        return root.get("emitters") instanceof ListTag && !root.has("fxData") && !root.has("meta");
    }

    public static ForgeScene parseFx(CompoundTag root) {
        return parseRoot(root);
    }

    public static ForgeScene parseProject(CompoundTag root) {
        return parseRoot(root);
    }

    private static ForgeScene parseRoot(CompoundTag root) {
        ForgeScene scene = new ForgeScene();
        scene.version = ForgeNbt.integer(root, "_version", 0);
        CompoundTag fx = root.getCompound("fx");
        readData(fx.getCompound("mainFX"), scene.main, scene);
        CompoundTag subs = fx.getCompound("subFXs");
        for (var entry : subs.entries().entrySet()) {
            if (entry.getValue() instanceof CompoundTag subTag) {
                FxData data = new FxData();
                readData(subTag, data, scene);
                scene.subs.put(entry.getKey(), data);
            }
        }
        if (scene.version < 1 && root.get("emitters") instanceof ListTag emitters) {
            scene.main.objects.clear();
            readObjects(emitters, scene.main, scene);
        }
        return scene;
    }

    private static void readData(CompoundTag data, FxData out, ForgeScene scene) {
        readObjects(data.getList("fxObjects"), out, scene);
    }

    private static void readObjects(ListTag list, FxData out, ForgeScene scene) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag tag) {
                FxObject object = readObject(tag, scene);
                if (object != null) {
                    out.objects.add(object);
                }
            }
        }
    }

    private static FxObject readObject(CompoundTag tag, ForgeScene scene) {
        String type = ForgeNbt.string(tag, "_type", "").toLowerCase(Locale.ROOT);
        Kind kind = switch (type) {
            case "particle", "particleemitter" -> Kind.PARTICLE;
            case "beam", "beamemitter" -> Kind.BEAM;
            case "trail", "trailemitter" -> Kind.TRAIL;
            case "empty", "emptyfxobject" -> Kind.EMPTY;
            default -> null;
        };
        if (kind == null) {
            scene.notes.add("skipped an fx object of unknown type '" + type + "'");
            return null;
        }
        FxObject object = new FxObject();
        object.kind = kind;
        CompoundTag configTag;
        CompoundTag transformTag;
        int version = ForgeNbt.integer(tag, "_version", 0);
        if (kind != Kind.EMPTY && version < 1) {
            configTag = tag;
            transformTag = CompoundTag.EMPTY;
            object.name = ForgeNbt.string(tag, "name", "");
        } else {
            configTag = tag.getCompound("config");
            transformTag = tag.getCompound("transform");
            object.name = ForgeNbt.string(tag, "name", "");
        }
        readTransform(transformTag, object.transform);
        switch (kind) {
            case PARTICLE -> object.particle = readParticle(configTag, scene);
            case BEAM -> object.beam = readBeam(configTag, scene);
            case TRAIL -> {
                TrailCfg trail = new TrailCfg();

                trail.smoothInterpolation = true;
                trail.minVertexDistance = 0.1f;
                trail.material.material = circleMaterial(0.3f, 0.01f, scene);
                readTrailConfig(configTag, trail, scene);
                object.trail = trail;
            }
            default -> {
            }
        }
        return object;
    }

    private static void readTransform(CompoundTag tag, Transform out) {
        out.id = orEmpty(ForgeNbt.uuidKey(tag.get("id")));
        out.parentId = ForgeNbt.uuidKey(tag.get("_parentId"));
        if (out.parentId == null) {
            out.parentId = ForgeNbt.uuidKey(tag.get("parentId"));
        }
        float[] p = ForgeNbt.vec3(tag.get("localPosition"), 0, 0, 0);
        out.localPosition.set(p[0], p[1], p[2]);
        float[] s = ForgeNbt.vec3(tag.get("localScale"), 1, 1, 1);
        out.localScale.set(s[0], s[1], s[2]);
        out.localRotation = quat(tag.get("localRotation"));
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Quat quat(NbtTag tag) {
        float x = 0, y = 0, z = 0, w = 1;
        if (tag instanceof CompoundTag c) {
            x = ForgeNbt.real(c, "x", 0);
            y = ForgeNbt.real(c, "y", 0);
            z = ForgeNbt.real(c, "z", 0);
            w = ForgeNbt.real(c, "w", 1);
        } else {
            List<Float> v = ForgeNbt.floats(tag);
            if (v.size() >= 4) {
                x = v.get(0);
                y = v.get(1);
                z = v.get(2);
                w = v.get(3);
            }
        }
        if (x * x + y * y + z * z + w * w < 1e-12f) {
            return Quat.identity();
        }
        return new Quat(x, y, z, w).normalize();
    }

    private static FNum fn(CompoundTag c, String key, FNum current) {
        return c.get(key) instanceof CompoundTag ct && !ct.entries().isEmpty() ? FNum.parse(ct, 0) : current;
    }

    private static FNum3 fn3(CompoundTag c, String key, FNum3 current) {
        return c.get(key) instanceof CompoundTag ct && !ct.entries().isEmpty() ? FNum.parse3(ct, 0, 0, 0) : current;
    }

    private static void range(CompoundTag c, String key, Range out) {
        NbtTag t = c.get(key);
        if (t instanceof CompoundTag rc) {
            if (ForgeNbt.isNumber(rc.get("a")) || ForgeNbt.isNumber(rc.get("b"))) {
                out.a = ForgeNbt.real(rc, "a", out.a);
                out.b = ForgeNbt.real(rc, "b", out.b);
            } else if (ForgeNbt.isNumber(rc.get("min")) || ForgeNbt.isNumber(rc.get("max"))) {
                out.a = ForgeNbt.real(rc, "min", out.a);
                out.b = ForgeNbt.real(rc, "max", out.b);
            } else if (ForgeNbt.isNumber(rc.get("x")) || ForgeNbt.isNumber(rc.get("y"))) {
                out.a = ForgeNbt.real(rc, "x", out.a);
                out.b = ForgeNbt.real(rc, "y", out.b);
            }
        } else {
            List<Float> v = ForgeNbt.floats(t);
            if (v.size() >= 2) {
                out.a = v.get(0);
                out.b = v.get(1);
            }
        }
    }

    private static void toggle(CompoundTag c, Toggle out) {
        out.enable = ForgeNbt.bool(c, "enable", out.enable);
    }

    private static String normalizeRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        return ref.indexOf(':') >= 0 ? ref : "minecraft:" + ref;
    }

    private static IMaterial circleMaterial(float radius, float discard, ForgeScene scene) {
        String ref = ForgeTextures.circleRef(radius);
        scene.generatedTextures.add(ref);
        return new TextureMaterial(ref, new float[]{0, 0, 0}, true, discard, 0);
    }

    private static IMaterial glowMaterial(float discard, ForgeScene scene) {
        scene.generatedTextures.add(ForgeTextures.GLOW);
        return new TextureMaterial(ForgeTextures.GLOW, new float[]{0, 0, 0}, true, discard, 0);
    }

    private static IMaterial textureMaterial(String ref, float discard, ForgeScene scene) {
        String normalized = normalizeRef(ref);
        if (normalized.isEmpty() || normalized.equals("minecraft:textures/particle/glow.png")) {
            if (normalized.isEmpty() || normalized.contains("glow")) {
                String note = "TextureMaterial 'minecraft:textures/particle/glow.png' (Photon's default) is a vanilla file; "
                        + "a soft radial glow stands in for it";
                if (!scene.notes.contains(note)) {
                    scene.notes.add(note);
                }
            }
            return glowMaterial(discard, scene);
        }
        return new TextureMaterial(normalized, new float[]{0, 0, 0}, true, discard, 0);
    }

    private static IMaterial readMaterial(CompoundTag wrapper, ForgeScene scene, IMaterial fallback) {
        String type = ForgeNbt.string(wrapper, "_type", "");
        int dot = type.lastIndexOf('.');
        String simple = (dot >= 0 ? type.substring(dot + 1) : type).toLowerCase(Locale.ROOT);
        switch (simple) {
            case "texturematerial" -> {
                return textureMaterial(ForgeNbt.string(wrapper, "texture", "textures/particle/glow.png"),
                        ForgeNbt.real(wrapper, "discardThreshold", 0), scene);
            }
            case "customshadermaterial" -> {
                String shader = ForgeNbt.string(wrapper, "shader", "");
                CompoundTag uniform = wrapper.getCompound("uniform");
                if (shader.isEmpty() || shader.equals("photon:circle") || shader.equals("circle")) {
                    float radius = 0.3f;
                    List<Float> r = ForgeNbt.floats(uniform.getCompound("uniforms").get("Radius"));
                    if (!r.isEmpty()) {
                        radius = r.get(0);
                    } else if (!uniform.has("uniforms") && !shader.isEmpty()) {
                        radius = 0.3f;
                    }
                    return circleMaterial(radius <= 0 ? 0.3f : radius, 0.01f, scene);
                }
                CompoundTag samplers = uniform.getCompound("samplers");
                for (var entry : samplers.entries().entrySet()) {
                    if (entry.getValue() instanceof NbtTag.StringTag(String value) && !value.isBlank()) {
                        scene.notes.add("custom shader '" + shader + "' cannot run here; its sampler texture '" + value
                                + "' is drawn as a plain textured particle instead");
                        return new TextureMaterial(normalizeRef(value), new float[]{0, 0, 0}, true, 0.01f, 0);
                    }
                }
                scene.notes.add("custom shader '" + shader + "' cannot run here; a flat tinted quad stands in for it");
                return new UnsupportedMaterial("custom_shader", null, null);
            }
            case "blocktexturesheetmaterial" -> {
                scene.notes.add("BlockTextureSheetMaterial needs the game's block atlas; a flat tinted quad stands in for it");
                return new UnsupportedMaterial("block_atlas", null, null);
            }
            default -> {
                return fallback;
            }
        }
    }

    private static BlendMode.Factor factor(CompoundTag c, String key, BlendMode.Factor fallback) {
        String s = ForgeNbt.string(c, key, null);
        if (s == null) {
            return fallback;
        }
        try {
            return BlendMode.Factor.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static BlendMode.Equation equation(CompoundTag c, BlendMode.Equation fallback) {
        String s = ForgeNbt.string(c, "blendFunc", null);
        if (s == null) {
            return fallback;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        if (u.contains("REVERSE")) {
            return BlendMode.Equation.REVERSE_SUBTRACT;
        }
        if (u.startsWith("SUB")) {
            return BlendMode.Equation.SUBTRACT;
        }
        return switch (u) {
            case "MIN" -> BlendMode.Equation.MIN;
            case "MAX" -> BlendMode.Equation.MAX;
            default -> BlendMode.Equation.ADD;
        };
    }

    private static BlendMode readBlend(CompoundTag c, BlendMode base) {
        if (c.entries().isEmpty()) {
            return base;
        }
        return new BlendMode(ForgeNbt.bool(c, "enableBlend", base.enableBlend()),
                factor(c, "srcColorFactor", base.srcColorFactor()),
                factor(c, "dstColorFactor", base.dstColorFactor()),
                factor(c, "srcAlphaFactor", base.srcAlphaFactor()),
                factor(c, "dstAlphaFactor", base.dstAlphaFactor()),
                equation(c, base.blendFunc()));
    }

    private static void readMaterialSetting(CompoundTag c, MaterialCfg out, ForgeScene scene) {
        out.blendMode = readBlend(c.getCompound("blendMode"), out.blendMode);
        out.cull = ForgeNbt.bool(c, "cull", out.cull);
        out.depthTest = ForgeNbt.bool(c, "depthTest", out.depthTest);
        out.depthMask = ForgeNbt.bool(c, "depthMask", out.depthMask);
        if (c.has("material")) {
            CompoundTag wrapper = c.getCompound("material");
            String type = ForgeNbt.string(wrapper, "_type", "");
            IMaterial parsed = readMaterial(wrapper, scene, null);
            out.material = parsed != null ? parsed : textureMaterial("textures/particle/glow.png", 0.01f, scene);
            if (parsed == null && !type.isEmpty()) {
                scene.notes.add("unknown material type '" + type + "'; default texture used");
            }
        }
    }

    private static void readRenderer(CompoundTag c, RendererCfg out) {
        out.layer = ForgeNbt.enumValue(c, "layer", Layer.class, out.layer);
        out.bloomEffect = ForgeNbt.bool(c, "bloomEffect", out.bloomEffect);
        out.bloomColor = (int) ForgeNbt.number(c.get("bloomColor"), out.bloomColor);
        if (out instanceof ParticleRendererCfg p) {
            p.renderMode = ForgeNbt.enumValue(c, "renderMode", RenderMode.class, p.renderMode);
            p.shade = ForgeNbt.bool(c, "shade", p.shade);
            p.useBlockUV = ForgeNbt.bool(c, "useBlockUV", p.useBlockUV);
            String model = firstString(c.getCompound("model"));
            if (model != null) {
                p.modelLocation = model;
            }
        }
    }

    private static String firstString(NbtTag tag) {
        if (tag instanceof NbtTag.StringTag(String value)) {
            return value;
        }
        if (tag instanceof CompoundTag(java.util.Map<String, NbtTag> entries)) {
            for (NbtTag child : entries.values()) {
                String found = firstString(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void readLight(CompoundTag c, LightCfg out) {
        toggle(c, out);
        out.skyLight = fn(c, "skyLight", out.skyLight);
        out.blockLight = fn(c, "blockLight", out.blockLight);
    }

    private static void readUvAnimation(CompoundTag c, UvAnimationCfg out) {
        toggle(c, out);
        range(c, "tiles", out.tiles);
        out.animation = ForgeNbt.enumValue(c, "animation", UvAnimationMode.class, out.animation);
        out.frameOverTime = fn(c, "frameOverTime", out.frameOverTime);
        out.startFrame = fn(c, "startFrame", out.startFrame);
        out.cycle = ForgeNbt.real(c, "cycle", out.cycle);
    }

    private static ParticleCfg readParticle(CompoundTag c, ForgeScene scene) {
        ParticleCfg p = new ParticleCfg();
        p.material.material = textureMaterial("textures/particle/glow.png", 0.01f, scene);
        p.duration = ForgeNbt.integer(c, "duration", p.duration);
        p.looping = ForgeNbt.bool(c, "looping", p.looping);
        p.startDelay = fn(c, "startDelay", p.startDelay);
        p.startLifetime = fn(c, "startLifetime", p.startLifetime);
        p.startSpeed = fn(c, "startSpeed", p.startSpeed);
        p.startSize = fn3(c, "startSize", p.startSize);
        p.startRotation = fn3(c, "startRotation", p.startRotation);
        p.startColor = fn(c, "startColor", p.startColor);
        p.simulationSpace = ForgeNbt.enumValue(c, "simulationSpace", Space.class, p.simulationSpace);
        p.maxParticles = ForgeNbt.integer(c, "maxParticles", p.maxParticles);

        if (!c.has("simulationSpace")) {
            FNum size = FNum.parse(c.get("startSize"), 0.1);
            p.startSize = new FNum3(size, size, size);
            p.startRotation = new FNum3(p.startRotation.z(), p.startRotation.y(), p.startRotation.x());
            p.simulationSpace = Space.World;
        }

        readEmission(c.getCompound("emission"), p.emission);
        readShape(c.getCompound("shape"), p.shape, scene);
        readMaterialSetting(c.getCompound("material"), p.material, scene);
        readRenderer(c.getCompound("renderer"), p.renderer);
        readPhysics(c.getCompound("physics"), p.physics);
        readLight(c.getCompound("lights"), p.lights);

        CompoundTag vol = c.getCompound("velocityOverLifetime");
        toggle(vol, p.velocityOverLifetime);
        p.velocityOverLifetime.linear = fn3(vol, "linear", p.velocityOverLifetime.linear);
        p.velocityOverLifetime.orbitalMode = ForgeNbt.enumValue(vol, "orbitalMode", OrbitalMode.class, p.velocityOverLifetime.orbitalMode);
        p.velocityOverLifetime.orbital = fn3(vol, "orbital", p.velocityOverLifetime.orbital);
        p.velocityOverLifetime.offset = fn3(vol, "offset", p.velocityOverLifetime.offset);
        p.velocityOverLifetime.speedModifier = fn(vol, "speedModifier", p.velocityOverLifetime.speedModifier);

        CompoundTag inherit = c.getCompound("inheritVelocity");
        toggle(inherit, p.inheritVelocity);
        p.inheritVelocity.mode = ForgeNbt.enumValue(inherit, "mode", InheritMode.class, p.inheritVelocity.mode);
        p.inheritVelocity.multiply = fn(inherit, "multiply", p.inheritVelocity.multiply);

        CompoundTag lbes = c.getCompound("lifetimeByEmitterSpeed");
        toggle(lbes, p.lifetimeByEmitterSpeed);
        p.lifetimeByEmitterSpeed.multiplier = fn(lbes, "multiplier", p.lifetimeByEmitterSpeed.multiplier);
        range(lbes, "speedRange", p.lifetimeByEmitterSpeed.speedRange);

        CompoundTag force = c.getCompound("forceOverLifetime");
        toggle(force, p.forceOverLifetime);
        p.forceOverLifetime.force = fn3(force, "force", p.forceOverLifetime.force);
        p.forceOverLifetime.simulationSpace = ForgeNbt.enumValue(force, "simulationSpace", Space.class, p.forceOverLifetime.simulationSpace);

        CompoundTag col = c.getCompound("colorOverLifetime");
        toggle(col, p.colorOverLifetime);
        p.colorOverLifetime.color = fn(col, "color", p.colorOverLifetime.color);

        CompoundTag cbs = c.getCompound("colorBySpeed");
        toggle(cbs, p.colorBySpeed);
        p.colorBySpeed.color = fn(cbs, "color", p.colorBySpeed.color);
        range(cbs, "speedRange", p.colorBySpeed.speedRange);

        CompoundTag sol = c.getCompound("sizeOverLifetime");
        toggle(sol, p.sizeOverLifetime);
        p.sizeOverLifetime.size = fn3(sol, "size", p.sizeOverLifetime.size);
        if (sol.has("scale")) {
            FNum scale = FNum.parse(sol.get("scale"), 1);
            p.sizeOverLifetime.size = new FNum3(scale, scale, scale);
        }

        CompoundTag sbs = c.getCompound("sizeBySpeed");
        toggle(sbs, p.sizeBySpeed);
        p.sizeBySpeed.size = fn3(sbs, "size", p.sizeBySpeed.size);
        range(sbs, "speedRange", p.sizeBySpeed.speedRange);

        CompoundTag rol = c.getCompound("rotationOverLifetime");
        toggle(rol, p.rotationOverLifetime);
        p.rotationOverLifetime.roll = fn(rol, "roll", p.rotationOverLifetime.roll);
        p.rotationOverLifetime.pitch = fn(rol, "pitch", p.rotationOverLifetime.pitch);
        p.rotationOverLifetime.yaw = fn(rol, "yaw", p.rotationOverLifetime.yaw);

        CompoundTag rbs = c.getCompound("rotationBySpeed");
        toggle(rbs, p.rotationBySpeed);
        p.rotationBySpeed.roll = fn(rbs, "roll", p.rotationBySpeed.roll);
        p.rotationBySpeed.pitch = fn(rbs, "pitch", p.rotationBySpeed.pitch);
        p.rotationBySpeed.yaw = fn(rbs, "yaw", p.rotationBySpeed.yaw);
        range(rbs, "speedRange", p.rotationBySpeed.speedRange);

        readNoise(c.getCompound("noise"), p.noise);
        readUvAnimation(c.getCompound("uvAnimation"), p.uvAnimation);
        readTrails(c.getCompound("trails"), p.trails, scene);
        readSubEmitters(c.getCompound("subEmitters"), p.subEmitters);
        return p;
    }

    private static void readEmission(CompoundTag c, EmissionCfg out) {
        out.rate = fn(c, "emissionRate", out.rate);
        out.mode = ForgeNbt.enumValue(c, "emissionMode", EmissionMode.class, out.mode);
        ListTag bursts = c.getList("bursts");
        for (int i = 0; i < bursts.size(); i++) {
            if (bursts.get(i) instanceof CompoundTag b) {
                Burst burst = new Burst();
                burst.time = ForgeNbt.integer(b, "time", burst.time);
                burst.count = fn(b, "count", burst.count);
                burst.cycles = ForgeNbt.integer(b, "cycles", burst.cycles);
                burst.interval = ForgeNbt.integer(b, "interval", burst.interval);
                burst.probability = ForgeNbt.real(b, "probability", burst.probability);
                out.bursts.add(burst);
            }
        }
    }

    private static void readPhysics(CompoundTag c, PhysicsCfg out) {
        toggle(c, out);
        out.hasCollision = ForgeNbt.bool(c, "hasCollision", out.hasCollision);
        out.removedWhenCollided = ForgeNbt.bool(c, "removedWhenCollided", out.removedWhenCollided);
        out.friction = fn(c, "friction", out.friction);
        out.gravity = fn(c, "gravity", out.gravity);
        out.bounceChance = fn(c, "bounceChance", out.bounceChance);
        out.bounceRate = fn(c, "bounceRate", out.bounceRate);
        out.bounceSpreadRate = fn(c, "bounceSpreadRate", out.bounceSpreadRate);
    }

    private static void readNoise(CompoundTag c, NoiseCfg out) {
        toggle(c, out);
        out.frequency = ForgeNbt.real(c, "frequency", out.frequency);
        out.quality = ForgeNbt.enumValue(c, "quality", NoiseQuality.class, out.quality);
        CompoundTag remap = c.getCompound("remap");
        out.remapEnable = ForgeNbt.bool(remap, "enable", out.remapEnable);
        out.remapCurve = fn(remap, "remapCurve", out.remapCurve);
        out.position = fn3(c, "position", out.position);
        out.rotation = fn(c, "rotation", out.rotation);
        out.size = fn(c, "size", out.size);
    }

    private static void readSubEmitters(CompoundTag c, SubEmittersCfg out) {
        toggle(c, out);
        ListTag list = c.getList("emitters");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag e) {
                SubEmitterEntry entry = new SubEmitterEntry();
                entry.emitter = ForgeNbt.string(e, "emitter", "");
                entry.event = ForgeNbt.enumValue(e, "event", SubEvent.class, entry.event);
                entry.emitProbability = fn(e, "emitProbability", entry.emitProbability);
                entry.tickInterval = Math.max(1, ForgeNbt.integer(e, "tickInterval", entry.tickInterval));
                entry.inheritColor = ForgeNbt.bool(e, "inheritColor", false);
                entry.inheritSize = ForgeNbt.bool(e, "inheritSize", false);
                entry.inheritRotation = ForgeNbt.bool(e, "inheritRotation", false);
                entry.inheritLifetime = ForgeNbt.bool(e, "inheritLifetime", false);
                entry.inheritDuration = ForgeNbt.bool(e, "inheritDuration", false);
                out.emitters.add(entry);
            }
        }
    }

    private static void readShape(CompoundTag c, ShapeCfg out, ForgeScene scene) {
        out.position = fn3(c, "position", out.position);
        out.rotation = fn3(c, "rotation", out.rotation);
        out.scale = fn3(c, "scale", out.scale);
        CompoundTag s = c.getCompound("shape");
        if (s.entries().isEmpty()) {
            return;
        }
        String type = ForgeNbt.string(s, "_type", "cone").toLowerCase(Locale.ROOT);
        ShapeDef def = out.shape;
        switch (type) {
            case "dot", "box", "circle", "cone", "cylinder", "sphere", "function", "mesh" -> def.type = type;
            default -> {
                scene.notes.add("unknown emitter shape '" + type + "'; cone used");
                def.type = "cone";
            }
        }
        def.radius = ForgeNbt.real(s, "radius", def.radius);
        def.radiusThickness = ForgeNbt.real(s, "radiusThickness", def.radiusThickness);
        def.arc = ForgeNbt.real(s, "arc", def.arc);
        def.angle = ForgeNbt.real(s, "angle", def.angle);
        def.boxType = ForgeNbt.enumValue(s, "emitFrom", BoxType.class, def.boxType);
        if (def.type.equals("mesh")) {
            def.meshType = ForgeNbt.enumValue(s, "type", MeshType.class, def.meshType);
            readMesh(s.getCompound("mesh"), def.mesh);
        }
        if (def.type.equals("function")) {
            def.x = ForgeNbt.string(s, "x", def.x);
            def.y = ForgeNbt.string(s, "y", def.y);
            def.z = ForgeNbt.string(s, "z", def.z);
            def.speedX = ForgeNbt.string(s, "speedX", def.speedX);
            def.speedY = ForgeNbt.string(s, "speedY", def.speedY);
            def.speedZ = ForgeNbt.string(s, "speedZ", def.speedZ);
        }
    }

    private static void readMesh(CompoundTag c, MeshData out) {
        List<Float> v = ForgeNbt.floats(c.get("vertices"));
        for (int i = 0; i + 2 < v.size(); i += 3) {
            out.vertices.add(new float[]{v.get(i), v.get(i + 1), v.get(i + 2)});
        }
        List<Float> e = ForgeNbt.floats(c.get("edges"));
        for (int i = 0; i + 5 < e.size(); i += 6) {
            float[] a = {e.get(i), e.get(i + 1), e.get(i + 2)};
            float[] b = {e.get(i + 3), e.get(i + 4), e.get(i + 5)};
            out.edges.add(new float[][]{a, b});
            out.edgeLengths.add(Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])));
        }
        List<Float> t = ForgeNbt.floats(c.get("triangles"));
        for (int i = 0; i + 8 < t.size(); i += 9) {
            float[] a = {t.get(i), t.get(i + 1), t.get(i + 2)};
            float[] b = {t.get(i + 3), t.get(i + 4), t.get(i + 5)};
            float[] d = {t.get(i + 6), t.get(i + 7), t.get(i + 8)};
            out.triangles.add(new float[][]{a, b, d});
            float nx = (b[1] - a[1]) * (d[2] - a[2]) - (b[2] - a[2]) * (d[1] - a[1]);
            float ny = (b[2] - a[2]) * (d[0] - a[0]) - (b[0] - a[0]) * (d[2] - a[2]);
            float nz = (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0]);
            out.triangleAreas.add(0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz));
        }
        out.edgeSumLength = ForgeNbt.real(c, "sl", 0);
        out.triangleSumArea = ForgeNbt.real(c, "sa", 0);
    }

    private static float sq(float v) {
        return v * v;
    }

    private static void readTrails(CompoundTag c, TrailsCfg out, ForgeScene scene) {
        out.config.material.material = circleMaterial(0.3f, 0.01f, scene);
        toggle(c, out);
        out.ratio = ForgeNbt.real(c, "ratio", out.ratio);
        out.lifetime = fn(c, "lifetime", out.lifetime);
        out.dieWithParticles = ForgeNbt.bool(c, "dieWithParticles", out.dieWithParticles);
        out.sizeAffectsWidth = ForgeNbt.bool(c, "sizeAffectsWidth", out.sizeAffectsWidth);
        out.sizeAffectsLifetime = ForgeNbt.bool(c, "sizeAffectsLifetime", out.sizeAffectsLifetime);
        out.inheritParticleColor = ForgeNbt.bool(c, "inheritParticleColor", out.inheritParticleColor);
        out.colorOverLifetime = fn(c, "colorOverLifetime", out.colorOverLifetime);
        readTrailConfig(c.getCompound("config"), out.config, scene);
        if (c.has("material")) {
            readMaterialSetting(c.getCompound("material"), out.config.material, scene);
            out.config.colorOverTrail = fn(c, "colorOverTrail", out.config.colorOverTrail);
            out.config.widthOverTrail = fn(c, "widthOverTrail", out.config.widthOverTrail);
            out.config.uvMode = ForgeNbt.enumValue(c, "uvMode", UvMode.class, out.config.uvMode);
            out.config.minVertexDistance = ForgeNbt.real(c, "minimumVertexDistance", 0);
        }
    }

    private static void readTrailConfig(CompoundTag c, TrailCfg out, ForgeScene scene) {
        out.duration = ForgeNbt.integer(c, "duration", out.duration);
        out.looping = ForgeNbt.bool(c, "looping", out.looping);
        out.startDelay = ForgeNbt.integer(c, "startDelay", out.startDelay);
        out.time = ForgeNbt.integer(c, "time", out.time);
        out.minVertexDistance = ForgeNbt.real(c, "minVertexDistance", out.minVertexDistance);
        out.smoothInterpolation = ForgeNbt.bool(c, "smoothInterpolation", out.smoothInterpolation);
        out.uvMode = ForgeNbt.enumValue(c, "uvMode", UvMode.class, out.uvMode);
        out.widthOverTrail = fn(c, "widthOverTrail", out.widthOverTrail);
        out.colorOverTrail = fn(c, "colorOverTrail", out.colorOverTrail);
        if (!c.has("smoothInterpolation") && !c.entries().isEmpty()
                && out.widthOverTrail instanceof FNum.Const(double value)) {
            out.widthOverTrail = FNum.constant((float) value / 10);
        }
        readMaterialSetting(c.getCompound("material"), out.material, scene);
        readRenderer(c.getCompound("renderer"), out.renderer);
        readLight(c.getCompound("lights"), out.lights);
        readUvAnimation(c.getCompound("uvAnimation"), out.uvAnimation);
    }

    private static BeamCfg readBeam(CompoundTag c, ForgeScene scene) {
        BeamCfg b = new BeamCfg();
        b.material.material = textureMaterial(LASER, 0.01f, scene);
        b.duration = ForgeNbt.integer(c, "duration", b.duration);
        b.looping = ForgeNbt.bool(c, "looping", b.looping);
        b.startDelay = ForgeNbt.integer(c, "startDelay", b.startDelay);
        if (c.has("end")) {
            float[] e = ForgeNbt.vec3(c.get("end"), 3, 0, 0);
            b.end.set(e[0], e[1], e[2]);
        }
        b.width = fn(c, "width", b.width);
        b.emitRate = fn(c, "emitRate", b.emitRate);
        b.color = fn(c, "color", b.color);
        readMaterialSetting(c.getCompound("material"), b.material, scene);
        readRenderer(c.getCompound("renderer"), b.renderer);
        readUvAnimation(c.getCompound("uvAnimation"), b.uvAnimation);
        readLight(c.getCompound("lights"), b.lights);
        return b;
    }
}

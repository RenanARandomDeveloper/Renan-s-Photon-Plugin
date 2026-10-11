package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;

import java.util.Map;

public final class ConfigOverrides {
    private ConfigOverrides() {
    }

    public static ParticleEmitterConfig apply(ParticleEmitterConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        ParticleEmitterConfig c = base.copy();
        c.startDelay = nf(ov, "startDelay", c.startDelay);
        c.startLifetime = nf(ov, "startLifetime", c.startLifetime);
        c.startSpeed = nf(ov, "startSpeed", c.startSpeed);
        c.startSize = nf3(ov, "startSize", c.startSize);
        c.startRotation = nf3(ov, "startRotation", c.startRotation);
        c.startColor = nf(ov, "startColor", c.startColor);
        c.duration = intv(ov, "duration", c.duration);
        c.prewarm = intv(ov, "prewarm", c.prewarm);
        c.maxParticles = intv(ov, "maxParticles", c.maxParticles);
        c.looping = bool(ov, "looping", c.looping);
        c.emission = c.emission.withRates(nf(ov, "emission.emissionRate", null), nf(ov, "emission.distanceRate", null));
        c.shape = c.shape.withTransform(nf3(ov, "shape.rotation", null), nf3(ov, "shape.scale", null),
                nf3(ov, "shape.position", null));

        Physics ph = c.physics;
        c.physics = new Physics(bool(ov, "physics.enable", ph.enable()), nf(ov, "physics.friction", ph.friction()),
                nf(ov, "physics.gravity", ph.gravity()));
        SizeOverLifetime sol = c.sizeOverLifetime;
        c.sizeOverLifetime = new SizeOverLifetime(bool(ov, "sizeOverLifetime.enable", sol.enable()),
                nf3(ov, "sizeOverLifetime.size", sol.size()));
        RotationOverLifetime rol = c.rotationOverLifetime;
        c.rotationOverLifetime = new RotationOverLifetime(bool(ov, "rotationOverLifetime.enable", rol.enable()),
                nf(ov, "rotationOverLifetime.yaw", rol.yaw()), nf(ov, "rotationOverLifetime.pitch", rol.pitch()),
                nf(ov, "rotationOverLifetime.roll", rol.roll()), rol.space(), rol.customSpaceId());
        ForceOverLifetime fol = c.forceOverLifetime;
        c.forceOverLifetime = new ForceOverLifetime(bool(ov, "forceOverLifetime.enable", fol.enable()),
                nf3(ov, "forceOverLifetime.force", fol.force()), fol.space(), fol.customSpaceId());
        ColorOverLifetime col = c.colorOverLifetime;
        c.colorOverLifetime = new ColorOverLifetime(bool(ov, "colorOverLifetime.enable", col.enable()),
                nf(ov, "colorOverLifetime.color", col.color()));
        LightOverLifetime lit = c.lights;
        c.lights = new LightOverLifetime(bool(ov, "lights.enable", lit.enable()),
                nf(ov, "lights.skyLight", lit.skyLight()), nf(ov, "lights.blockLight", lit.blockLight()));
        ExternalForces ef = c.externalForces;
        c.externalForces = new ExternalForces(bool(ov, "externalForces.enable", ef.enable()),
                nf(ov, "externalForces.multiplier", ef.multiplier()), ef.filter(), ef.influenceListIds());
        VelocityOverLifetime vol = c.velocityOverLifetime;
        c.velocityOverLifetime = new VelocityOverLifetime(bool(ov, "velocityOverLifetime.enable", vol.enable()),
                nf3(ov, "velocityOverLifetime.linear", vol.linear()), vol.orbitalMode(),
                nf3(ov, "velocityOverLifetime.orbital", vol.orbital()), nf3(ov, "velocityOverLifetime.offset", vol.offset()),
                nf(ov, "velocityOverLifetime.radial", vol.radial()),
                nf(ov, "velocityOverLifetime.speedModifier", vol.speedModifier()), vol.space(), vol.customSpaceId());
        InheritVelocity iv = c.inheritVelocity;
        c.inheritVelocity = new InheritVelocity(bool(ov, "inheritVelocity.enable", iv.enable()), iv.mode(),
                nf(ov, "inheritVelocity.multiply", iv.multiply()));
        LifetimeByEmitterSpeed lbe = c.lifetimeByEmitterSpeed;
        c.lifetimeByEmitterSpeed = new LifetimeByEmitterSpeed(bool(ov, "lifetimeByEmitterSpeed.enable", lbe.enable()),
                nf(ov, "lifetimeByEmitterSpeed.multiplier", lbe.multiplier()), lbe.speedRange());
        ColorBySpeed cbs = c.colorBySpeed;
        c.colorBySpeed = new ColorBySpeed(bool(ov, "colorBySpeed.enable", cbs.enable()),
                nf(ov, "colorBySpeed.color", cbs.color()), cbs.speedRange());
        SizeBySpeed sbs = c.sizeBySpeed;
        c.sizeBySpeed = new SizeBySpeed(bool(ov, "sizeBySpeed.enable", sbs.enable()),
                nf3(ov, "sizeBySpeed.size", sbs.size()), sbs.speedRange());
        RotationBySpeed rbs = c.rotationBySpeed;
        c.rotationBySpeed = new RotationBySpeed(bool(ov, "rotationBySpeed.enable", rbs.enable()),
                nf(ov, "rotationBySpeed.yaw", rbs.yaw()), nf(ov, "rotationBySpeed.pitch", rbs.pitch()),
                nf(ov, "rotationBySpeed.roll", rbs.roll()), rbs.speedRange());
        UvAnimation uv = c.uvAnimation;
        c.uvAnimation = new UvAnimation(bool(ov, "uvAnimation.enable", uv.enable()), uv.tilesX(), uv.tilesY(),
                uv.animation(), nf(ov, "uvAnimation.frameOverTime", uv.frameOverTime()),
                nf(ov, "uvAnimation.startFrame", uv.startFrame()), flt(ov, "uvAnimation.cycle", uv.cycle()));
        c.noise = c.noise.with(boolOrNull(ov, "noise.enable"), fltOrNull(ov, "noise.frequency"),
                nf3(ov, "noise.position", null), nf(ov, "noise.rotation", null), nf(ov, "noise.size", null));

        LightEmission le = c.lightEmission;
        if (le != null && (ov.containsKey("lightEmission.enable") || ov.containsKey("lightEmission.color")
                || ov.containsKey("lightEmission.intensity") || ov.containsKey("lightEmission.range"))) {
            c.lightEmission = new LightEmission(bool(ov, "lightEmission.enable", le.enable()), le.ratio(),
                    le.maxLights(), le.useParticleColor(), nf(ov, "lightEmission.color", le.color()),
                    nf(ov, "lightEmission.intensity", le.intensity()), nf(ov, "lightEmission.range", le.range()),
                    le.alphaAffectsIntensity(), le.sizeAffectsRange(), le.castShadows(), le.sourceRadius(), le.spot(),
                    le.volumetricEnable(), le.volumetricStrength());
        }

        TrailsOnParticle tr = c.trails;
        c.trails = new TrailsOnParticle(bool(ov, "trails.enable", tr.enable()), flt(ov, "trails.ratio", tr.ratio()),
                nf(ov, "trails.lifetime", tr.lifetime()), bool(ov, "trails.dieWithParticles", tr.dieWithParticles()),
                bool(ov, "trails.sizeAffectsWidth", tr.sizeAffectsWidth()),
                bool(ov, "trails.sizeAffectsLifetime", tr.sizeAffectsLifetime()),
                bool(ov, "trails.inheritParticleColor", tr.inheritParticleColor()),
                nf(ov, "trails.colorOverLifetime", tr.colorOverLifetime()), tr.trailType(), tr.trailConfig(),
                tr.araConfig());

        ParticleRendererSetting r = c.renderer;
        if (r != null && (ov.containsKey("renderer.orderInLayer") || ov.containsKey("renderer.velocityScale")
                || ov.containsKey("renderer.lengthScale"))) {
            RenderSettings rs = r.renderSettings();
            c.renderer = new ParticleRendererSetting(r.renderMode(), r.facingMode(), r.facingDirection(),
                    new RenderSettings(rs.layer(), intv(ov, "renderer.orderInLayer", rs.orderInLayer()),
                            rs.vertexSorting()),
                    flt(ov, "renderer.velocityScale", r.velocityScale()),
                    flt(ov, "renderer.lengthScale", r.lengthScale()), r.modelPivot(), r.modelSource(),
                    r.materials(), r.shade());
        }
        return c;
    }

    public static BeamConfig apply(BeamConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        BeamConfig c = base.copy();
        c.duration = intv(ov, "duration", c.duration);
        c.looping = bool(ov, "looping", c.looping);
        c.startDelay = intv(ov, "startDelay", c.startDelay);
        c.width = nf(ov, "width", c.width);
        c.emitRate = nf(ov, "emitRate", c.emitRate);
        c.color = nf(ov, "color", c.color);
        c.uvAnimation = uv(c.uvAnimation, ov);
        return c;
    }

    public static TrailConfig apply(TrailConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        TrailConfig c = base.copy();
        c.duration = intv(ov, "duration", c.duration);
        c.looping = bool(ov, "looping", c.looping);
        c.startDelay = intv(ov, "startDelay", c.startDelay);
        c.time = intv(ov, "time", c.time);
        c.minVertexDistance = flt(ov, "minVertexDistance", c.minVertexDistance);
        c.widthOverTrail = nf(ov, "widthOverTrail", c.widthOverTrail);
        c.colorOverTrail = nf(ov, "colorOverTrail", c.colorOverTrail);
        c.uvAnimation = uv(c.uvAnimation, ov);
        return c;
    }

    public static AraTrailConfig apply(AraTrailConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        AraTrailConfig c = base.copy();
        c.duration = intv(ov, "duration", c.duration);
        c.looping = bool(ov, "looping", c.looping);
        c.thickness = flt(ov, "thickness", c.thickness);
        c.thicknessOverLength = nf(ov, "thicknessOverLength", c.thicknessOverLength);
        c.colorOverLength = nf(ov, "colorOverLength", c.colorOverLength);
        c.thicknessOverTime = nf(ov, "thicknessOverTime", c.thicknessOverTime);
        c.thicknessOverSegmentTime = nf(ov, "thicknessOverSegmentTime", c.thicknessOverSegmentTime);
        c.colorOverTime = nf(ov, "colorOverTime", c.colorOverTime);
        c.colorOverSegmentTime = nf(ov, "colorOverSegmentTime", c.colorOverSegmentTime);
        c.initialThickness = flt(ov, "initialThickness", c.initialThickness);
        c.time = flt(ov, "time", c.time);
        c.minDistance = flt(ov, "minDistance", c.minDistance);
        c.timeInterval = flt(ov, "timeInterval", c.timeInterval);
        AraTrailPhysics ph = c.physics;
        c.physics = new AraTrailPhysics(bool(ov, "physicsSetting.enable", ph.enable()),
                flt(ov, "physicsSetting.warmup", ph.warmup()), ph.gravity(), ph.gravitySpace(), ph.gravityCustomSpaceId(),
                flt(ov, "physicsSetting.inertia", ph.inertia()),
                flt(ov, "physicsSetting.velocitySmoothing", ph.velocitySmoothing()),
                flt(ov, "physicsSetting.damping", ph.damping()));
        return c;
    }

    public static LightConfig apply(LightConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        LightConfig c = base.copy();
        c.color = nf(ov, "color", c.color);
        c.intensity = nf(ov, "intensity", c.intensity);
        c.range = nf(ov, "range", c.range);
        c.innerAngle = flt(ov, "innerAngle", c.innerAngle);
        c.outerAngle = flt(ov, "outerAngle", c.outerAngle);
        c.sourceRadius = flt(ov, "sourceRadius", c.sourceRadius);
        c.flicker = flt(ov, "flicker", c.flicker);
        c.castShadows = bool(ov, "castShadows", c.castShadows);
        c.volumetricEnable = bool(ov, "volumetric.enable", c.volumetricEnable);
        c.volumetricStrength = nf(ov, "volumetric.strength", c.volumetricStrength);
        return c;
    }

    public static FogVolumeConfig apply(FogVolumeConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        FogVolumeConfig c = base.copy();
        c.density = nf(ov, "density", c.density);
        c.color = nf(ov, "color", c.color);
        c.emission = nf(ov, "emission", c.emission);
        c.absorption = flt(ov, "absorption", c.absorption);
        c.edgeFalloff = flt(ov, "edgeFalloff", c.edgeFalloff);
        return c;
    }

    public static ForceFieldConfig apply(ForceFieldConfig base, Map<String, Object> ov) {
        if (ov.isEmpty()) {
            return base;
        }
        return new ForceFieldConfig(base.shape(), flt(ov, "startRange", base.startRange()),
                flt(ov, "endRange", base.endRange()), nf(ov, "directionX", base.directionX()),
                nf(ov, "directionY", base.directionY()), nf(ov, "directionZ", base.directionZ()),
                nf(ov, "gravity", base.gravity()), flt(ov, "gravityFocus", base.gravityFocus()),
                nf(ov, "rotationSpeed", base.rotationSpeed()),
                flt(ov, "rotationAttraction", base.rotationAttraction()), base.rotationRandomnessX(),
                base.rotationRandomnessZ(), nf(ov, "drag", base.drag()), base.multiplyDragByParticleSize(),
                base.multiplyDragByParticleVelocity());
    }

    private static UvAnimation uv(UvAnimation base, Map<String, Object> ov) {
        if (base == null || !(ov.containsKey("uvAnimation.enable") || ov.containsKey("uvAnimation.frameOverTime")
                || ov.containsKey("uvAnimation.startFrame") || ov.containsKey("uvAnimation.cycle"))) {
            return base;
        }
        return new UvAnimation(bool(ov, "uvAnimation.enable", base.enable()), base.tilesX(), base.tilesY(),
                base.animation(), nf(ov, "uvAnimation.frameOverTime", base.frameOverTime()),
                nf(ov, "uvAnimation.startFrame", base.startFrame()), flt(ov, "uvAnimation.cycle", base.cycle()));
    }

    private static NumberFunction nf(Map<String, Object> ov, String key, NumberFunction fallback) {
        return ov.get(key) instanceof NumberFunction f ? f : fallback;
    }

    private static NumberFunction3 nf3(Map<String, Object> ov, String key, NumberFunction3 fallback) {
        return ov.get(key) instanceof NumberFunction3 f ? f : fallback;
    }

    private static int intv(Map<String, Object> ov, String key, int fallback) {
        return ov.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    private static float flt(Map<String, Object> ov, String key, float fallback) {
        return ov.get(key) instanceof Number n ? n.floatValue() : fallback;
    }

    private static Float fltOrNull(Map<String, Object> ov, String key) {
        return ov.get(key) instanceof Number n ? n.floatValue() : null;
    }

    private static boolean bool(Map<String, Object> ov, String key, boolean fallback) {
        return ov.get(key) instanceof Boolean b ? b : fallback;
    }

    private static Boolean boolOrNull(Map<String, Object> ov, String key) {
        return ov.get(key) instanceof Boolean b ? b : null;
    }
}

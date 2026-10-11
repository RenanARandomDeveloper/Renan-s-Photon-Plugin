package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.model.shape.ShapeSetting;

public final class ParticleEmitterConfig implements FxObjectData {
    public int duration;
    public boolean looping;
    public int prewarm;
    public SimulationSpace simulationSpace = SimulationSpace.LOCAL;
    public String customSpaceId = "";
    public int maxParticles;
    public NumberFunction startDelay;
    public NumberFunction startLifetime;
    public NumberFunction startSpeed;
    public NumberFunction3 startSize;
    public NumberFunction3 startRotation;
    public NumberFunction startColor;
    public ShapeSetting shape;
    public EmissionSetting emission;
    public ParticleRendererSetting renderer;
    public ColorOverLifetime colorOverLifetime;
    public SizeOverLifetime sizeOverLifetime;
    public RotationOverLifetime rotationOverLifetime;
    public ColorBySpeed colorBySpeed;
    public SizeBySpeed sizeBySpeed;
    public RotationBySpeed rotationBySpeed;
    public VelocityOverLifetime velocityOverLifetime;
    public LightOverLifetime lights;
    public LightEmission lightEmission = LightEmission.DISABLED;
    public UvAnimation uvAnimation;
    public InheritVelocity inheritVelocity;
    public Physics physics;
    public ForceOverLifetime forceOverLifetime;
    public ExternalForces externalForces;
    public Noise noise;
    public LifetimeByEmitterSpeed lifetimeByEmitterSpeed;
    public TrailsOnParticle trails;
    public SubEmittersSetting subEmitters = SubEmittersSetting.DISABLED;

    public ParticleEmitterConfig copy() {
        ParticleEmitterConfig c = new ParticleEmitterConfig();
        c.duration = duration;
        c.looping = looping;
        c.prewarm = prewarm;
        c.simulationSpace = simulationSpace;
        c.customSpaceId = customSpaceId;
        c.maxParticles = maxParticles;
        c.startDelay = startDelay;
        c.startLifetime = startLifetime;
        c.startSpeed = startSpeed;
        c.startSize = startSize;
        c.startRotation = startRotation;
        c.startColor = startColor;
        c.shape = shape;
        c.emission = emission;
        c.renderer = renderer;
        c.colorOverLifetime = colorOverLifetime;
        c.sizeOverLifetime = sizeOverLifetime;
        c.rotationOverLifetime = rotationOverLifetime;
        c.colorBySpeed = colorBySpeed;
        c.sizeBySpeed = sizeBySpeed;
        c.rotationBySpeed = rotationBySpeed;
        c.velocityOverLifetime = velocityOverLifetime;
        c.lights = lights;
        c.lightEmission = lightEmission;
        c.uvAnimation = uvAnimation;
        c.inheritVelocity = inheritVelocity;
        c.physics = physics;
        c.forceOverLifetime = forceOverLifetime;
        c.externalForces = externalForces;
        c.noise = noise;
        c.lifetimeByEmitterSpeed = lifetimeByEmitterSpeed;
        c.trails = trails;
        c.subEmitters = subEmitters;
        return c;
    }

    public enum SimulationSpace {LOCAL, WORLD, CUSTOM}
}

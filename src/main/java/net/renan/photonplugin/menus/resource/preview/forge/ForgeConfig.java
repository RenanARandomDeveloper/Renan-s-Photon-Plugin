package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.forge.FNum.FNum3;

import java.util.ArrayList;
import java.util.List;

public final class ForgeConfig {
    private ForgeConfig() {
    }

    static FNum3 zero3() {
        return new FNum3(FNum.constant(0), FNum.constant(0), FNum.constant(0));
    }

    static FNum3 one3() {
        return new FNum3(FNum.constant(1), FNum.constant(1), FNum.constant(1));
    }

    static FNum.FGradient whiteGradient() {
        return FNum.FGradient.white();
    }

    public enum Space {Local, World}

    public enum EmissionMode {Exacting, Random}

    public enum UvAnimationMode {WholeSheet, SingleRow}

    public enum RenderMode {Billboard, Horizontal, Vertical, VerticalBillboard, Model}

    public enum Layer {Opaque, Translucent}

    public enum OrbitalMode {AngularVelocity, LinearVelocity, FixedVelocity}

    public enum NoiseQuality {Noise1D, Noise2D, Noise3D}

    public enum InheritMode {CURRENT, INITIAL}

    public enum SubEvent {Birth, Death, Collision, FirstCollision, Tick}

    public enum UvMode {Stretch, Tile}

    public enum BoxType {Volume, Shell, Edge}

    public enum MeshType {Vertex, Edge, Triangle}

    public enum Kind {PARTICLE, BEAM, TRAIL, EMPTY}

    public static final class Range {
        public float a;
        public float b;

        public Range(float a, float b) {
            this.a = a;
            this.b = b;
        }
    }

    public static class Toggle {
        public boolean enable;
    }

    public static final class Transform {
        public final Vec3 localPosition = new Vec3(0, 0, 0);
        public final Vec3 localScale = new Vec3(1, 1, 1);
        public String id = "";
        public String parentId;
        public Quat localRotation = Quat.identity();
    }

    public static final class MaterialCfg {
        public BlendMode blendMode = BlendMode.standardAlpha();
        public boolean cull = true;
        public boolean depthTest = true;
        public boolean depthMask = false;
        public IMaterial material;
    }

    public static class RendererCfg {
        public Layer layer = Layer.Translucent;
        public boolean bloomEffect = false;
        public int bloomColor = -1;
    }

    public static final class ParticleRendererCfg extends RendererCfg {
        public RenderMode renderMode = RenderMode.Billboard;
        public boolean shade = true;
        public boolean useBlockUV = true;
        public String modelLocation = "minecraft:block/dirt";
    }

    public static final class Burst {
        public int time = 0;
        public FNum count = FNum.constant(50);
        public int cycles = 1;
        public int interval = 1;
        public float probability = 1;
    }

    public static final class EmissionCfg {
        public final List<Burst> bursts = new ArrayList<>();
        public FNum rate = FNum.constant(0.5f);
        public EmissionMode mode = EmissionMode.Exacting;
    }

    public static final class MeshData {
        public final List<float[]> vertices = new ArrayList<>();
        public final List<float[][]> edges = new ArrayList<>();
        public final List<float[][]> triangles = new ArrayList<>();
        public final List<Double> edgeLengths = new ArrayList<>();
        public final List<Double> triangleAreas = new ArrayList<>();
        public double edgeSumLength;
        public double triangleSumArea;
    }

    public static final class ShapeDef {
        public String type = "cone";
        public float radius = .5f;
        public float radiusThickness = 1;
        public float arc = 360;
        public float angle = 25;
        public BoxType boxType = BoxType.Volume;
        public MeshType meshType = MeshType.Triangle;
        public MeshData mesh = new MeshData();
        public String x = "0", y = "0", z = "0", speedX = "0", speedY = "0", speedZ = "0";
    }

    public static final class ShapeCfg {
        public ShapeDef shape = new ShapeDef();
        public FNum3 position = new FNum3(FNum.constant(0), FNum.constant(0), FNum.constant(0));
        public FNum3 rotation = new FNum3(FNum.constant(0), FNum.constant(0), FNum.constant(0));
        public FNum3 scale = new FNum3(FNum.constant(1), FNum.constant(1), FNum.constant(1));
    }

    public static final class PhysicsCfg extends Toggle {
        public boolean hasCollision = true;
        public boolean removedWhenCollided = false;
        public FNum friction = FNum.constant(1);
        public FNum gravity = FNum.constant(0);
        public FNum bounceChance = FNum.constant(1);
        public FNum bounceRate = FNum.constant(1);
        public FNum bounceSpreadRate = FNum.constant(0);
    }

    public static final class LightCfg extends Toggle {
        public FNum skyLight = FNum.constant(15);
        public FNum blockLight = FNum.constant(15);

        public LightCfg() {
            enable = true;
        }
    }

    public static final class VelocityOverLifetimeCfg extends Toggle {
        public FNum3 linear = zero3();
        public OrbitalMode orbitalMode = OrbitalMode.AngularVelocity;
        public FNum3 orbital = zero3();
        public FNum3 offset = zero3();
        public FNum speedModifier = FNum.constant(1);
    }

    public static final class InheritVelocityCfg extends Toggle {
        public InheritMode mode = InheritMode.INITIAL;
        public FNum multiply = FNum.constant(1);
    }

    public static final class LifetimeByEmitterSpeedCfg extends Toggle {
        public FNum multiplier = FNum.constant(1);
        public Range speedRange = new Range(0, 1);
    }

    public static final class ForceOverLifetimeCfg extends Toggle {
        public FNum3 force = zero3();
        public Space simulationSpace = Space.Local;
    }

    public static final class ColorOverLifetimeCfg extends Toggle {
        public FNum color = new FNum.GradientFn(whiteGradient());
    }

    public static final class ColorBySpeedCfg extends Toggle {
        public FNum color = new FNum.GradientFn(whiteGradient());
        public Range speedRange = new Range(0, 1);
    }

    public static final class SizeOverLifetimeCfg extends Toggle {
        public FNum3 size = one3();
    }

    public static final class SizeBySpeedCfg extends Toggle {
        public FNum3 size = one3();
        public Range speedRange = new Range(0, 1);
    }

    public static final class RotationOverLifetimeCfg extends Toggle {
        public FNum roll = FNum.constant(0);
        public FNum pitch = FNum.constant(0);
        public FNum yaw = FNum.constant(0);
    }

    public static final class RotationBySpeedCfg extends Toggle {
        public FNum roll = FNum.constant(0);
        public FNum pitch = FNum.constant(0);
        public FNum yaw = FNum.constant(0);
        public Range speedRange = new Range(0, 1);
    }

    public static final class NoiseCfg extends Toggle {
        public float frequency = 1;
        public NoiseQuality quality = NoiseQuality.Noise2D;
        public boolean remapEnable = false;

        public FNum remapCurve = new FNum.Curve(-1, 1, List.of(new net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment(0, 1, 0.1f, 1, 0.9f, 1, 1, 1)));
        public FNum3 position = new FNum3(FNum.constant(0.1), FNum.constant(0.1), FNum.constant(0.1));
        public FNum rotation = FNum.constant(0);
        public FNum size = FNum.constant(0);
    }

    public static final class UvAnimationCfg extends Toggle {
        public Range tiles = new Range(1, 1);
        public UvAnimationMode animation = UvAnimationMode.WholeSheet;
        public FNum frameOverTime = FNum.constant(0);
        public FNum startFrame = FNum.constant(0);
        public float cycle = 1;
    }

    public static final class SubEmitterEntry {
        public String emitter = "";
        public SubEvent event = SubEvent.Birth;
        public FNum emitProbability = FNum.constant(0);
        public int tickInterval = 1;
        public boolean inheritColor, inheritSize, inheritRotation, inheritLifetime, inheritDuration;
    }

    public static final class SubEmittersCfg extends Toggle {
        public final List<SubEmitterEntry> emitters = new ArrayList<>();
    }

    public static final class TrailCfg {
        public final MaterialCfg material = new MaterialCfg();
        public final RendererCfg renderer = new RendererCfg();
        public final LightCfg lights = new LightCfg();
        public final UvAnimationCfg uvAnimation = new UvAnimationCfg();
        public int duration = 100;
        public boolean looping = true;
        public int startDelay = 0;
        public int time = 20;
        public float minVertexDistance = 0.05f;
        public boolean smoothInterpolation = false;
        public UvMode uvMode = UvMode.Stretch;
        public FNum widthOverTrail = FNum.constant(0.2f);
        public FNum colorOverTrail = new FNum.GradientFn(whiteGradient());
    }

    public static final class TrailsCfg extends Toggle {
        public final TrailCfg config = new TrailCfg();
        public float ratio = 1f;
        public FNum lifetime = FNum.constant(1);
        public boolean dieWithParticles = false;
        public boolean sizeAffectsWidth = true;
        public boolean sizeAffectsLifetime = false;
        public boolean inheritParticleColor = true;
        public FNum colorOverLifetime = new FNum.GradientFn(whiteGradient());

        public TrailsCfg() {
            config.widthOverTrail = FNum.constant(0.5f);
        }
    }

    public static final class ParticleCfg {
        public final EmissionCfg emission = new EmissionCfg();
        public final ShapeCfg shape = new ShapeCfg();
        public final MaterialCfg material = new MaterialCfg();
        public final ParticleRendererCfg renderer = new ParticleRendererCfg();
        public final PhysicsCfg physics = new PhysicsCfg();
        public final LightCfg lights = new LightCfg();
        public final VelocityOverLifetimeCfg velocityOverLifetime = new VelocityOverLifetimeCfg();
        public final InheritVelocityCfg inheritVelocity = new InheritVelocityCfg();
        public final LifetimeByEmitterSpeedCfg lifetimeByEmitterSpeed = new LifetimeByEmitterSpeedCfg();
        public final ForceOverLifetimeCfg forceOverLifetime = new ForceOverLifetimeCfg();
        public final ColorOverLifetimeCfg colorOverLifetime = new ColorOverLifetimeCfg();
        public final ColorBySpeedCfg colorBySpeed = new ColorBySpeedCfg();
        public final SizeOverLifetimeCfg sizeOverLifetime = new SizeOverLifetimeCfg();
        public final SizeBySpeedCfg sizeBySpeed = new SizeBySpeedCfg();
        public final RotationOverLifetimeCfg rotationOverLifetime = new RotationOverLifetimeCfg();
        public final RotationBySpeedCfg rotationBySpeed = new RotationBySpeedCfg();
        public final NoiseCfg noise = new NoiseCfg();
        public final UvAnimationCfg uvAnimation = new UvAnimationCfg();
        public final TrailsCfg trails = new TrailsCfg();
        public final SubEmittersCfg subEmitters = new SubEmittersCfg();
        public int duration = 100;
        public boolean looping = true;
        public FNum startDelay = FNum.constant(0);
        public FNum startLifetime = FNum.constant(100);
        public FNum startSpeed = FNum.constant(1);
        public FNum3 startSize = new FNum3(FNum.constant(0.1), FNum.constant(0.1), FNum.constant(0.1));
        public FNum3 startRotation = zero3();
        public FNum startColor = FNum.constant(-1);
        public Space simulationSpace = Space.Local;
        public int maxParticles = 2000;
    }

    public static final class BeamCfg {
        public final Vec3 end = new Vec3(3, 0, 0);
        public final MaterialCfg material = new MaterialCfg();
        public final RendererCfg renderer = new RendererCfg();
        public final UvAnimationCfg uvAnimation = new UvAnimationCfg();
        public final LightCfg lights = new LightCfg();
        public int duration = 100;
        public boolean looping = true;
        public int startDelay = 0;
        public FNum width = FNum.constant(0.2);
        public FNum emitRate = FNum.constant(0);
        public FNum color = FNum.constant(-1);
    }

    public static final class FxObject {
        public final Transform transform = new Transform();
        public Kind kind = Kind.EMPTY;
        public String name = "";
        public ParticleCfg particle;
        public BeamCfg beam;
        public TrailCfg trail;
    }

    public static final class FxData {
        public final List<FxObject> objects = new ArrayList<>();
    }

    public static final class ForgeScene {
        public final FxData main = new FxData();
        public final java.util.Map<String, FxData> subs = new java.util.LinkedHashMap<>();

        public final List<String> notes = new ArrayList<>();

        public final java.util.Set<String> generatedTextures = new java.util.LinkedHashSet<>();
        public int version;
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.ColorMath;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.TextureMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.HdrSampler;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.AnimationClip;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinDeformer;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinnedModel;
import net.renan.photonplugin.menus.resource.preview.neo.sim.*;

import java.util.ArrayList;
import java.util.List;

public final class NeoItemGatherer {
    private final MeshCache meshes;

    public NeoItemGatherer(MeshCache meshes) {
        this.meshes = meshes;
    }

    private static Vec3 toWorld(Vec3 meshLocalPos, ParticleState p, Vec3 pivot, Quat rotation, Vec3 scale) {
        Vec3 local = new Vec3(meshLocalPos).add(pivot).mul(scale);
        return rotation.rotate(local).add(p.position);
    }

    private static float vatPhase(AnimatedGltfModelSourceRef ref, AnimationClip clip, ParticleState p,
                                  float sharedClockSeconds) {
        boolean clamp = !ref.loop();
        float raw;
        if (ref.phaseSource() == AnimatedGltfModelSourceRef.PhaseSource.LIFETIME) {
            float rate = Float.isFinite(ref.speed()) ? ref.speed() : 0f;
            float base = clamp && rate < 0f ? 1f : 0f;
            raw = base + p.t() * rate;
        } else {
            float duration = clip.duration();
            float seconds = sharedClockSeconds * ref.speed();
            float base = 0f;
            if (duration > 0f && Float.isFinite(seconds)) {
                float clipTime;
                if (ref.loop()) {
                    float wrapped = seconds % duration;
                    clipTime = wrapped < 0f ? wrapped + duration : wrapped;
                } else {
                    clipTime = Math.max(0f, Math.min(seconds, duration));
                }
                base = clipTime / duration;
            }
            raw = base + (float) p.mem.get("anim-phase");
        }
        if (!Float.isFinite(raw)) {
            return 0f;
        }
        return clamp ? Math.max(0f, Math.min(1f, raw)) : raw - (float) Math.floor(raw);
    }

    private static float animationTime(AnimatedGltfModelSourceRef ref, AnimationClip clip, ParticleState p,
                                       float sharedClockSeconds) {
        float duration = clip.duration();
        if (duration <= 0f) {
            return 0f;
        }
        if (ref.perParticlePhase()) {
            return vatPhase(ref, clip, p, sharedClockSeconds) * duration;
        }
        float raw = sharedClockSeconds * ref.speed();
        if (ref.loop()) {
            float wrapped = raw % duration;
            return wrapped < 0 ? wrapped + duration : wrapped;
        }
        return Math.max(0f, Math.min(duration, raw));
    }

    private static int gizmoChannel(float v) {
        return Math.max(60, Math.min(255, Math.round(v * 255f)));
    }

    public List<RenderItem> gather(FxSceneSimulation sim, PreviewCamera camera) {
        List<RenderItem> items = new ArrayList<>();
        Vec3 camPos = camera.position();

        for (ParticleEmitterSim pe : sim.allParticleEmittersIncludingSubInstances()) {
            if (!pe.isVisible()) {
                continue;
            }
            var renderer = pe.config().renderer;
            if (renderer.materials().isEmpty() || renderer.renderMode() == RenderMode.NONE) {
                continue;
            }
            for (ParticleState p : pe.particles()) {
                if (p.delay > 0) {
                    continue;
                }
                boolean drewAsMesh = renderer.renderMode() == RenderMode.MODEL
                        && addMeshTriangles(items, p, renderer, renderer.materials().get(0), camera, pe,
                        sim.playbackTime() / 20f);
                if (!drewAsMesh) {
                    float depth = new Vec3(p.position).sub(camPos).length();
                    for (MaterialLayer layer : renderer.materials()) {
                        items.add(new NeoParticleItem(p.position.copy(), p.velocity.copy(), p.currentSize.copy(),
                                p.currentRotationRad.copy(), p.currentColor, p.blockLight, p.skyLight, p.currentUv,
                                layer.material(), layer.blendMode(), renderer.renderMode(), renderer.facingMode(),
                                renderer.velocityScale(), renderer.lengthScale(), renderer.facingDirection(),
                                pe.simSpaceScale(), pe.simSpaceRotation(), layer.cull(), renderer.renderSettings(), depth,
                                pe.outerRotationOverLifetime(p)));
                    }
                }
            }
        }

        for (BeamSim beam : sim.allBeamsIncludingSubInstances()) {
            if (beam.config().materials.isEmpty() || !beam.isVisible()) {
                continue;
            }
            Vec3 from = beam.from();
            Vec3 to = beam.to();
            float depth = (new Vec3(from).add(to).mul(0.5f)).sub(camPos).length();
            int color = beam.color();
            for (MaterialLayer layer : beam.config().materials) {
                items.add(new RenderItem.RibbonSegmentItem(from, to, beam.width() * 2f, beam.width() * 2f, color, color,
                        layer.material(), layer.blendMode(), beam.uv(), beam.config().renderSettings, null, null, depth));
            }
        }

        for (TrailEmitterSim trail : sim.allTrailsIncludingSubInstances()) {
            if (trail.config().materials.isEmpty() || !trail.isVisible()) {
                continue;
            }
            for (TrailEmitterSim.Segment seg : trail.buildSegments(camPos)) {
                float depth = new Vec3(seg.a()).add(seg.b()).mul(0.5f).sub(camPos).length();
                for (MaterialLayer layer : trail.config().materials) {
                    items.add(new RenderItem.RibbonSegmentItem(seg.a(), seg.b(), seg.widthA(), seg.widthB(),
                            seg.colorA(), seg.colorB(), layer.material(), layer.blendMode(), seg.uv(),
                            trail.config().renderSettings, seg.sideA(), seg.sideB(), depth));
                }
            }
        }

        for (AraTrailSim araTrail : sim.allAraTrailsIncludingSubInstances()) {
            if (araTrail.config().materials.isEmpty() || !araTrail.isVisible()) {
                continue;
            }
            for (List<AraTrailSim.RenderVertex> span : araTrail.buildRenderSpans(camPos)) {
                for (int i = 0; i < span.size() - 1; i++) {
                    AraTrailSim.RenderVertex v0 = span.get(i);
                    AraTrailSim.RenderVertex v1 = span.get(i + 1);
                    float depth = new Vec3(v0.position()).add(v1.position()).mul(0.5f).sub(camPos).length();

                    boolean newerOnTop = araTrail.config().sorting == AraTrailConfig.Sorting.NEWER_ON_TOP;
                    float vA = newerOnTop ? araTrail.config().uvWidthFactor : 0f;
                    float vB = newerOnTop ? 0f : araTrail.config().uvWidthFactor;
                    UvRect uv = new UvRect(v0.u(), vA, v1.u(), vB);
                    for (MaterialLayer layer : araTrail.config().materials) {
                        items.add(new RenderItem.RibbonSegmentItem(v0.position(), v1.position(),
                                v0.thickness() * 2f, v1.thickness() * 2f, v0.color(), v1.color(), layer.material(),
                                layer.blendMode(), uv, araTrail.config().renderSettings, v0.bitangent(),
                                v1.bitangent(), depth));
                    }
                }
            }
        }

        for (var field : sim.allForceFieldsIncludingSubInstances()) {
            if (!field.isVisible()) {
                continue;
            }
            float depth = new Vec3(field.transform().position()).sub(camPos).length();
            items.add(new NeoForceFieldItem(field.transform().position().copy(), field.config().endRange(), depth));
        }
        for (LightSim light : sim.allLightsIncludingSubInstances()) {
            if (!light.isShowing()) {
                continue;
            }
            var cfg = light.config();
            float[] rgba = new float[4];
            HdrSampler.sample(cfg.color, 0, () -> 0.5, rgba);
            int rgb = ColorMath.pack(255, gizmoChannel(rgba[0]), gizmoChannel(rgba[1]), gizmoChannel(rgba[2]));
            Vec3 position = light.node().localToWorld().transformPosition(new Vec3());
            Vec3 axis = light.node().localToWorld().transformDirection(new Vec3(0, -1, 0));
            float range = (float) cfg.range.get(0, () -> 0.5);
            items.add(new NeoLightGizmoItem(position, axis, Math.max(0.05f, range),
                    cfg.type == LightConfig.Type.SPOT, cfg.outerAngle, rgb & 0xFFFFFF,
                    new Vec3(position).sub(camPos).length()));
        }
        for (FogVolumeSim fog : sim.allFogVolumesIncludingSubInstances()) {
            if (!fog.isShowing()) {
                continue;
            }
            Vec3 center = fog.node().localToWorld().transformPosition(new Vec3());
            items.add(new NeoFogGizmoItem(fog.node().localToWorld(), fog.config().shape == FogVolumeConfig.Shape.SPHERE,
                    0x9fd8ff, new Vec3(center).sub(camPos).length()));
        }
        return items;
    }

    private boolean addMeshTriangles(List<RenderItem> items, ParticleState p, ParticleRendererSetting renderer,
                                     MaterialLayer layer, PreviewCamera camera, ParticleEmitterSim pe,
                                     float sharedClockSeconds) {
        Vec3 camPos = camera.position();
        ModelSource source = renderer.modelSource();
        if (source instanceof UnsupportedModelSource) {
            return false;
        }
        List<MeshTriangle> triangles;
        if (source instanceof AnimatedGltfModelSourceRef animRef) {
            SkinnedModel model = meshes.skinnedModels().get(animRef);
            if (!model.isAnimated()) {
                ObjMesh bindPose = meshes.get(source);
                if (bindPose.isEmpty()) {
                    return false;
                }
                triangles = bindPose.triangles();
            } else {
                AnimationClip clip = animRef.animation() == null || animRef.animation().isBlank()
                        ? model.clipAt(0) : model.clip(animRef.animation());
                if (clip == null) {
                    clip = model.clipAt(0);
                }
                if (animRef.perParticlePhase()) {
                    triangles = meshes.skinnedModels().baked(model, clip, animRef.frames())
                            .pose(vatPhase(animRef, clip, p, sharedClockSeconds), animRef.interpolate());
                } else {
                    float time = animationTime(animRef, clip, p, sharedClockSeconds);
                    SkinDeformer deformer = new SkinDeformer(model.skeleton());
                    deformer.pose(clip, time);
                    triangles = deformer.deform(model.triangles());
                }
            }
        } else {
            ObjMesh mesh = meshes.get(source);
            if (mesh.isEmpty()) {
                return false;
            }
            triangles = mesh.triangles();
        }
        Vec3 pivot = renderer.modelPivot();

        Quat outer = renderer.facingMode() == FacingMode.DEFAULT
                ? pe.simSpaceRotation().copy()
                : NeoFacing.facingQuat(camera, p.position, p.velocity, renderer.facingMode(), renderer.facingDirection(), pe.simSpaceRotation());
        Quat modelRotation = outer.multiply(Quat.euler(p.currentRotationRad));
        Quat spin = pe.outerRotationOverLifetime(p);
        if (spin != null) {
            modelRotation = spin.multiply(modelRotation);
        }
        Vec3 scale = new Vec3(p.currentSize).mul(pe.simSpaceScale());
        for (MeshTriangle tri : triangles) {
            Vec3 worldA = toWorld(tri.a().position(), p, pivot, modelRotation, scale);
            Vec3 worldB = toWorld(tri.b().position(), p, pivot, modelRotation, scale);
            Vec3 worldC = toWorld(tri.c().position(), p, pivot, modelRotation, scale);

            Vec3 faceNormal = Vec3.cross(new Vec3(worldB).sub(worldA), new Vec3(worldC).sub(worldA));
            Vec3 centroid = new Vec3(worldA).add(worldB).add(worldC).mul(1f / 3f);
            Vec3 toCamera = new Vec3(camPos).sub(centroid);
            if (layer.cull() && faceNormal.dot(toCamera) <= 0) {
                continue;
            }

            float depth = new Vec3(centroid).sub(camPos).length();
            IMaterial material = tri.textureOverride() != null
                    ? new TextureMaterial(tri.textureOverride(), new float[]{0, 0, 0}, true, 0.1f, false)
                    : layer.material();
            BlendMode blendMode = tri.textureOverride() != null ? BlendMode.standardAlpha() : layer.blendMode();
            int shadedColor = !renderer.shade() || tri.shade() >= 0.999f ? p.currentColor
                    : ColorMath.multiplyArgb(p.currentColor, ColorMath.pack(255,
                    Math.round(tri.shade() * 255), Math.round(tri.shade() * 255), Math.round(tri.shade() * 255)));
            items.add(new RenderItem.MeshTriangleItem(worldA, worldB, worldC,
                    tri.a().u(), tri.a().v(), tri.b().u(), tri.b().v(), tri.c().u(), tri.c().v(),
                    shadedColor, p.blockLight, p.skyLight, material, blendMode, renderer.renderSettings(), depth));
        }
        return true;
    }
}

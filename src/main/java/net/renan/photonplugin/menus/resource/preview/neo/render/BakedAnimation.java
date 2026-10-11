package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.AnimationClip;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinDeformer;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinnedModel;

import java.util.ArrayList;
import java.util.List;

final class BakedAnimation {
    private final SkinnedModel model;
    private final AnimationClip clip;
    private final int frames;
    private final float duration;
    private final SkinDeformer deformer;
    private final List<MeshTriangle>[] poses;

    @SuppressWarnings("unchecked")
    BakedAnimation(SkinnedModel model, AnimationClip clip, int frames) {
        this.model = model;
        this.clip = clip;
        this.frames = Math.max(1, frames);
        this.duration = clip == null ? 0f : clip.duration();
        this.deformer = new SkinDeformer(model.skeleton());
        this.poses = new List[this.frames + 1];
    }

    private static MeshVertex mix(MeshVertex a, MeshVertex b, float t) {
        Vec3 position = new Vec3(a.position()).lerp(b.position(), t);
        Vec3 normal = new Vec3(a.normal()).lerp(b.normal(), t);
        if (normal.lengthSquared() > 1e-12f) {
            normal.normalize();
        } else {
            normal = new Vec3(a.normal());
        }
        return new MeshVertex(position, a.u(), a.v(), normal);
    }

    private List<MeshTriangle> frame(int index) {
        List<MeshTriangle> pose = poses[index];
        if (pose == null) {
            deformer.pose(clip, duration * index / frames);
            pose = deformer.deform(model.triangles());
            poses[index] = pose;
        }
        return pose;
    }

    List<MeshTriangle> pose(float phase, boolean interpolate) {
        float cursor = Math.max(0f, Math.min(1f, phase)) * frames;
        int frame = Math.max(0, Math.min((int) cursor, frames - 1));
        float blend = cursor - frame;
        if (!interpolate) {
            blend = blend >= 1f ? 1f : 0f;
        }
        List<MeshTriangle> base = frame(frame);
        if (blend <= 0f) {
            return base;
        }
        List<MeshTriangle> next = frame(frame + 1);
        if (blend >= 1f) {
            return next;
        }
        List<MeshTriangle> out = new ArrayList<>(base.size());
        for (int i = 0; i < base.size(); i++) {
            MeshTriangle a = base.get(i);
            MeshTriangle b = next.get(i);
            out.add(new MeshTriangle(mix(a.a(), b.a(), blend), mix(a.b(), b.b(), blend), mix(a.c(), b.c(), blend)));
        }
        return out;
    }
}

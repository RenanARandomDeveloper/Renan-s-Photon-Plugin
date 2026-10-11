package net.renan.photonplugin.menus.resource.preview.neo.model.skin;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.SkinnedTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.SkinnedVertex;

import java.util.ArrayList;
import java.util.List;

public final class SkinDeformer {
    private final Skeleton skeleton;
    private final float[] trs;
    private final Mat4[] skinMatrices;

    public SkinDeformer(Skeleton skeleton) {
        this.skeleton = skeleton;
        int joints = skeleton.jointCount();
        this.trs = new float[joints * Skeleton.FLOATS_PER_TRS];
        this.skinMatrices = new Mat4[joints];
    }

    public void pose(AnimationClip clip, float time) {
        System.arraycopy(skeleton.restTrs(), 0, trs, 0, trs.length);
        if (clip != null) {
            clip.sample(time, trs);
        }
        int joints = skeleton.jointCount();
        Mat4[] world = new Mat4[joints];
        for (int joint = 0; joint < joints; joint++) {
            Mat4 local = Skeleton.localFromTrs(trs, joint * Skeleton.FLOATS_PER_TRS);
            int parent = skeleton.parent(joint);
            world[joint] = parent >= 0 ? world[parent].multiply(local) : local;
            skinMatrices[joint] = world[joint].multiply(skeleton.inverseBind(joint));
        }
    }

    public List<MeshTriangle> deform(List<SkinnedTriangle> rest) {
        List<MeshTriangle> out = new ArrayList<>(rest.size());
        for (SkinnedTriangle triangle : rest) {
            out.add(new MeshTriangle(deformVertex(triangle.a()), deformVertex(triangle.b()),
                    deformVertex(triangle.c())));
        }
        return out;
    }

    private MeshVertex deformVertex(SkinnedVertex vertex) {
        int[] joints = vertex.joints();
        float[] weights = vertex.weights();
        float totalWeight = 0;
        for (float weight : weights) {
            totalWeight += weight;
        }
        if (totalWeight <= 0f) {
            return new MeshVertex(vertex.position(), vertex.u(), vertex.v(), vertex.normal());
        }
        Vec3 outPos = new Vec3(0, 0, 0);
        Vec3 outNormal = new Vec3(0, 0, 0);
        for (int i = 0; i < joints.length; i++) {
            float weight = weights[i];
            if (weight == 0f) {
                continue;
            }
            int joint = joints[i];
            if (joint < 0 || joint >= skinMatrices.length) {
                continue;
            }
            Mat4 m = skinMatrices[joint];
            outPos.add(m.transformPosition(vertex.position()).mul(weight));
            outNormal.add(m.transformDirection(vertex.normal()).mul(weight));
        }
        Vec3 normal = outNormal.lengthSquared() > 1.0e-12f ? outNormal.normalize() : vertex.normal();
        return new MeshVertex(outPos, vertex.u(), vertex.v(), normal);
    }
}

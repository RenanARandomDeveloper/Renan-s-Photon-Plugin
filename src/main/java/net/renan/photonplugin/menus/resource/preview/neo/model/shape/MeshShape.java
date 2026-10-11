package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.ModelSource;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshSampler;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.util.Random;

public final class MeshShape implements EmitterShape {
    private final Type type;
    private final ModelSource modelSource;
    private ObjMesh cachedMesh;
    private MeshSampler cachedSampler = MeshSampler.EMPTY;

    public MeshShape(Type type, ModelSource modelSource) {
        this.type = type;
        this.modelSource = modelSource;
    }

    @Override
    public ModelSource meshSourceIfAny() {
        return modelSource;
    }

    @Override
    public void sample(Random random, Vec3 scale, EmissionContext context, ObjMesh mesh, Vec3 outLocalPos,
                       Vec3 outLocalVelocity) {
        if (mesh != cachedMesh) {
            cachedMesh = mesh;
            cachedSampler = MeshSampler.of(mesh);
        }
        float t = random.nextFloat();
        Vec3 point = switch (type) {
            case VERTEX -> cachedSampler.randomVertex(t);
            case EDGE -> sampleEdge(random, t);
            case TRIANGLE -> sampleTriangle(random, t);
        };
        if (point == null) {
            outLocalPos.set(0, 0, 0);
        } else {
            outLocalPos.set(point).mul(scale);
        }
        outLocalVelocity.set(0, 0, 0);
    }

    private Vec3 sampleEdge(Random random, float t) {
        MeshSampler.Edge edge = cachedSampler.randomEdge(t);
        if (edge == null) {
            return null;
        }
        return new Vec3(edge.b()).sub(edge.a()).mul(random.nextFloat()).add(edge.a());
    }

    private Vec3 sampleTriangle(Random random, float t) {
        MeshSampler.Triangle triangle = cachedSampler.randomTriangle(t);
        if (triangle == null) {
            return null;
        }
        float sqrtR = (float) Math.sqrt(random.nextFloat());
        float a = 1 - sqrtR;
        float r2 = random.nextFloat();
        float b = sqrtR * (1 - r2);
        float c = sqrtR * r2;
        return new Vec3(
                a * triangle.a().x + b * triangle.b().x + c * triangle.c().x,
                a * triangle.a().y + b * triangle.b().y + c * triangle.c().y,
                a * triangle.a().z + b * triangle.b().z + c * triangle.c().z);
    }

    public enum Type {VERTEX, EDGE, TRIANGLE}
}

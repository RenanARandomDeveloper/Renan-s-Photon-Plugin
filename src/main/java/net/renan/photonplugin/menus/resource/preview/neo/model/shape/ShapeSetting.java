package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.ModelSource;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction3;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

import java.util.Random;

public final class ShapeSetting {
    private final EmitterShape shape;
    private final NumberFunction3 rotation;
    private final NumberFunction3 scale;
    private final NumberFunction3 position;

    public ShapeSetting(EmitterShape shape, NumberFunction3 rotation, NumberFunction3 scale, NumberFunction3 position) {
        this.shape = shape;
        this.rotation = rotation;
        this.scale = scale;
        this.position = position;
    }

    public ShapeSetting withTransform(NumberFunction3 rotation, NumberFunction3 scale, NumberFunction3 position) {
        return new ShapeSetting(shape, rotation != null ? rotation : this.rotation, scale != null ? scale : this.scale,
                position != null ? position : this.position);
    }

    public void sample(Random random, EmissionContext context, MemRandom emitterMem, ObjMesh mesh, Vec3 outPos,
                       Vec3 outVel) {
        Vec3 rot = rotation.get(context.emitterT(), emitterMem.of("shape-rotation"));
        Vec3 scl = scale.get(context.emitterT(), emitterMem.of("shape-scale"));
        Vec3 pos = position.get(context.emitterT(), emitterMem.of("shape-position"));

        shape.sample(random, scl, context, mesh, outPos, outVel);

        outPos.rotateYXYDegrees(rot).add(pos);
        outVel.rotateYXYDegrees(rot);
    }

    public ModelSource meshSourceIfAny() {
        return shape.meshSourceIfAny();
    }
}

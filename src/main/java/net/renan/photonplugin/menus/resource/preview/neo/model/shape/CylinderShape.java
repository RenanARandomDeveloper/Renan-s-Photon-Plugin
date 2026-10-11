package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.util.Random;

public final class CylinderShape implements EmitterShape {
    private final float radius;
    private final float radiusThickness;
    private final float arcDeg;
    private final ShapeArcSetting shapeArc;

    public CylinderShape(float radius, float radiusThickness, float arcDeg, ShapeArcSetting shapeArc) {
        this.radius = radius;
        this.radiusThickness = radiusThickness;
        this.arcDeg = arcDeg;
        this.shapeArc = shapeArc;
    }

    @Override
    public void sample(Random random, Vec3 scale, EmissionContext context, ObjMesh mesh, Vec3 outLocalPos,
                       Vec3 outLocalVelocity) {
        float outer = radius;
        float inner = (1 - radiusThickness) * radius;
        double origin = (double) inner * inner;
        double bound = (double) outer * outer;
        double r = outer == inner ? outer : Math.sqrt(origin + random.nextDouble() * (bound - origin));

        double theta = arcDeg * Math.PI * 2 * shapeArc.sampleArcFraction(random, context) / 360.0;

        outLocalPos.set((float) (r * Math.cos(theta)), random.nextFloat() - 0.5f, (float) (r * Math.sin(theta)))
                .mul(scale);

        outLocalVelocity.set((float) Math.cos(theta), 0f, (float) Math.sin(theta)).normalize(0.05f);
    }
}

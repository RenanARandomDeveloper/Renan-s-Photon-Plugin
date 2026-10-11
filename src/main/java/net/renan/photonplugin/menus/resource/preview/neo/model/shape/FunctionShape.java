package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.util.Random;

public final class FunctionShape implements EmitterShape {
    private final ShapeExpression x, y, z, speedX, speedY, speedZ;

    public FunctionShape(String x, String y, String z, String speedX, String speedY, String speedZ) {
        this.x = ShapeExpression.parse(x);
        this.y = ShapeExpression.parse(y);
        this.z = ShapeExpression.parse(z);
        this.speedX = ShapeExpression.parse(speedX);
        this.speedY = ShapeExpression.parse(speedY);
        this.speedZ = ShapeExpression.parse(speedZ);
    }

    @Override
    public void sample(Random random, Vec3 scale, EmissionContext context, ObjMesh mesh, Vec3 outLocalPos,
                       Vec3 outLocalVelocity) {
        double[] vars = new double[7];
        vars[0] = context.emitterT();
        vars[1] = Math.PI;
        for (int i = 0; i < 5; i++) {
            vars[2 + i] = random.nextFloat();
        }

        outLocalPos.set((float) x.eval(vars), (float) y.eval(vars), (float) z.eval(vars));
        outLocalVelocity.set((float) speedX.eval(vars), (float) speedY.eval(vars), (float) speedZ.eval(vars));
        if (!outLocalVelocity.isZero()) {
            outLocalVelocity.normalize(0.05f);
        }
    }
}

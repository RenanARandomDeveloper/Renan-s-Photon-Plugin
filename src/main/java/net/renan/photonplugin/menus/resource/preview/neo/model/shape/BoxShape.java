package net.renan.photonplugin.menus.resource.preview.neo.model.shape;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.util.Random;

public final class BoxShape implements EmitterShape {
    private final EmitFrom emitFrom;

    public BoxShape(EmitFrom emitFrom) {
        this.emitFrom = emitFrom == null ? EmitFrom.VOLUME : emitFrom;
    }

    @Override
    public void sample(Random random, Vec3 scaleIn, EmissionContext context, ObjMesh mesh, Vec3 outLocalPos,
                       Vec3 outLocalVelocity) {
        Vec3 half = new Vec3(Math.abs(scaleIn.x), Math.abs(scaleIn.y), Math.abs(scaleIn.z)).mul(0.5f);
        outLocalPos.set(
                random.nextFloat() * 2 * half.x - half.x,
                random.nextFloat() * 2 * half.y - half.y,
                random.nextFloat() * 2 * half.z - half.z);

        if (emitFrom == EmitFrom.SHELL) {
            double xy = (double) half.x * half.y;
            double yz = (double) half.y * half.z;
            double xz = (double) half.x * half.z;
            double randomValue = random.nextDouble() * (xy + yz + xz);
            if (randomValue < xy) {
                outLocalPos.z = random.nextFloat() > 0.5f ? half.z : -half.z;
            } else if (randomValue < yz + xy) {
                outLocalPos.x = random.nextFloat() > 0.5f ? half.x : -half.x;
            } else {
                outLocalPos.y = random.nextFloat() > 0.5f ? half.y : -half.y;
            }
        } else if (emitFrom == EmitFrom.EDGE) {
            double randomValue = random.nextDouble() * (half.x + half.y + half.z);
            if (randomValue < half.x) {
                outLocalPos.z = random.nextFloat() > 0.5f ? half.z : -half.z;
                outLocalPos.y = random.nextFloat() > 0.5f ? half.y : -half.y;
            } else if (randomValue < half.x + half.y) {
                outLocalPos.z = random.nextFloat() > 0.5f ? half.z : -half.z;
                outLocalPos.x = random.nextFloat() > 0.5f ? half.x : -half.x;
            } else {
                outLocalPos.x = random.nextFloat() > 0.5f ? half.x : -half.x;
                outLocalPos.y = random.nextFloat() > 0.5f ? half.y : -half.y;
            }
        }

        outLocalVelocity.set(0, 0.05f, 0);
    }

    public enum EmitFrom {VOLUME, SHELL, EDGE}
}

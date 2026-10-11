package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public final class SpawnFrame {
    private final Mat4 emitterToSim;
    private final Mat4 simToEmitter;

    public SpawnFrame(Mat4 emitterToWorld, Mat4 worldToEmitter, Mat4 worldToSim, Mat4 simToWorld) {
        this.emitterToSim = worldToSim.multiply(emitterToWorld);
        this.simToEmitter = worldToEmitter.multiply(simToWorld);
    }

    public Vec3 origin() {
        return new Vec3(emitterToSim.m[12], emitterToSim.m[13], emitterToSim.m[14]);
    }

    public Vec3 emitterDirToSim(Vec3 direction) {
        return emitterToSim.transformDirection(direction);
    }

    public Vec3 simDirToEmitter(Vec3 direction) {
        return simToEmitter.transformDirection(direction);
    }
}

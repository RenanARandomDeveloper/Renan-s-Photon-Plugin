package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;

import java.util.Random;

public final class ParticleState {
    public final Vec3 basePos = new Vec3();
    public final Vec3 storedVelocity = new Vec3();
    public final Vec3 position = new Vec3();
    public final Vec3 velocity = new Vec3();
    public final Vec3 initialSize = new Vec3(1, 1, 1);
    public final Vec3 currentSize = new Vec3(1, 1, 1);
    public final Vec3 initialRotationRad = new Vec3();
    public final Vec3 currentRotationRad = new Vec3();
    public final MemRandom mem;
    public SpawnFrame frame;
    public float age;
    public float lifetime;
    public float delay;
    public int initialColor = 0xFFFFFFFF;
    public int currentColor = 0xFFFFFFFF;
    public boolean removed;
    public UvRect currentUv = UvRect.FULL;
    public int blockLight = 15;
    public int skyLight = 15;

    public ParticleState(Random random) {
        this.mem = new MemRandom(random);
    }

    public float t() {
        return lifetime > 0 ? Math.min(age / lifetime, 1f) : 0f;
    }

    public float speedBlocksPerSecond() {
        return velocity.length() * 20f;
    }

    public float maxSizeComponent() {
        return Math.max(currentSize.x, Math.max(currentSize.y, currentSize.z));
    }
}

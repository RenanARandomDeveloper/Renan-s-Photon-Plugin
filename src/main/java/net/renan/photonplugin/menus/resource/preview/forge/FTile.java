package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.NoiseCfg;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.ParticleCfg;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.SubEmitterEntry;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.SubEvent;

import java.util.Random;

final class FTile extends FParticle {
    private static final double MAXIMUM_COLLISION_VELOCITY_SQUARED = 100.0 * 100.0;

    final FParticleEmitter emitter;
    final ParticleCfg config;

    float localX, localY, localZ;
    float localXo, localYo, localZo;
    float rotationX, rotationY, rotationZ;
    float rotationXo, rotationYo, rotationZo;
    float sizeX = 1, sizeY = 1, sizeZ = 1;
    float sizeXo = 1, sizeYo = 1, sizeZo = 1;
    float r = 1, g = 1, b = 1, a = 1;
    float ro = 1, go = 1, bo = 1, ao = 1;
    float velocityX, velocityY, velocityZ;
    int light = FUtil.FULL_BRIGHT;

    boolean collided;
    private boolean firstCollision;

    private Mat4 initialTransform;
    private Mat4 initialTransformInverse;
    private Vec3 initialScale;
    private Vec3 initialSize;
    private Vec3 initialRotation;
    private float[] initialColor;

    FTile(FParticleEmitter emitter, ParticleCfg config, Random random) {
        super(random);
        this.emitter = emitter;
        this.config = config;
        setup();
    }

    private static float safe(float t) {
        if (Float.isNaN(t)) {
            return 0;
        }
        return Math.max(-1e6f, Math.min(1e6f, t));
    }

    private static Vec3 rotateAbout(Vec3 v, int axis, float angle) {
        Vec3 copy = v.copy();
        return switch (axis) {
            case 0 -> FUtil.rotateX(copy, angle);
            case 1 -> FUtil.rotateY(copy, angle);
            default -> FUtil.rotateZ(copy, angle);
        };
    }

    private void setup() {
        initialTransform = emitter.transform.localToWorld();
        initialTransformInverse = emitter.transform.worldToLocal();
        initialScale = emitter.transform.scale();
        float emitterT = emitter.t;

        delay = (int) config.startDelay.get(emitterT, random::nextFloat);
        int startLifetime = (int) config.startLifetime.get(emitterT, random::nextFloat);
        if (config.lifetimeByEmitterSpeed.enable) {
            lifetime = lifetimeByEmitterSpeed(startLifetime);
        } else {
            lifetime = startLifetime;
        }

        FShapes.setup(this, emitter);
        if (config.inheritVelocity.enable && config.inheritVelocity.mode == ForgeConfig.InheritMode.INITIAL) {
            addInternalVelocity(spaceTransformInverse().transformDirection(emitter.velocity));
        }
        float speed = (float) config.startSpeed.get(emitterT, random::nextFloat);
        velocityX *= speed;
        velocityY *= speed;
        velocityZ *= speed;
        initialSize = config.startSize.get(emitterT, FUtil.once(random));
        initialRotation = config.startRotation.get(emitterT, FUtil.once(random)).mul(FUtil.TWO_PI / 360);
        int color = (int) config.startColor.get(emitterT, random::nextFloat);
        initialColor = new float[]{FUtil.red(color), FUtil.green(color), FUtil.blue(color), FUtil.alpha(color)};
        setSize(initialSize);
        setRotation(initialRotation);
        setColor(initialColor);
        update();
        updateOrigin();

        if (config.trails.enable) {
            FTrail.setup(emitter, this);
        }
    }

    private int lifetimeByEmitterSpeed(int initialLifetime) {
        ForgeConfig.LifetimeByEmitterSpeedCfg c = config.lifetimeByEmitterSpeed;
        float value = emitter.velocity.length() * 20;
        float t = (value - c.speedRange.a) / (c.speedRange.b - c.speedRange.a);
        return (int) ((float) c.multiplier.get(safe(t), () -> getMemRandom(c)) * initialLifetime);
    }

    void setLocalPos(Vec3 pos, boolean origin) {
        localX = pos.x;
        localY = pos.y;
        localZ = pos.z;
        if (origin) {
            localXo = pos.x;
            localYo = pos.y;
            localZo = pos.z;
        }
    }

    void addInternalVelocity(Vec3 v) {
        velocityX += v.x;
        velocityY += v.y;
        velocityZ += v.z;
    }

    private void setRotation(Vec3 rotation) {
        rotationX = rotation.x;
        rotationY = rotation.y;
        rotationZ = rotation.z;
    }

    private void setSize(Vec3 size) {
        sizeX = size.x;
        sizeY = size.y;
        sizeZ = size.z;
    }

    private void setColor(float[] c) {
        r = c[0];
        g = c[1];
        b = c[2];
        a = c[3];
    }

    float getT(float partialTicks) {
        if (lifetime == 0) {
            return t;
        }
        return t + partialTicks / lifetime;
    }

    Mat4 spaceTransform() {
        return config.simulationSpace == ForgeConfig.Space.Local ? emitter.transform.localToWorld() : initialTransform;
    }

    Mat4 spaceTransformInverse() {
        return config.simulationSpace == ForgeConfig.Space.Local ? emitter.transform.worldToLocal() : initialTransformInverse;
    }

    Vec3 spaceScale() {
        return config.simulationSpace == ForgeConfig.Space.Local ? emitter.transform.scale() : initialScale;
    }

    Vec3 getRealRotation(float partial) {
        return new Vec3(FUtil.lerp(partial, rotationXo, rotationX), FUtil.lerp(partial, rotationYo, rotationY),
                FUtil.lerp(partial, rotationZo, rotationZ));
    }

    Vec3 getRealSize(float partial) {
        return new Vec3(FUtil.lerp(partial, sizeXo, sizeX), FUtil.lerp(partial, sizeYo, sizeY),
                FUtil.lerp(partial, sizeZo, sizeZ));
    }

    Vec3 getLocalPos(float partial) {
        if (removed) {
            return new Vec3(localX, localY, localZ);
        }
        Vec3 pos = new Vec3(FUtil.lerp(partial, localXo, localX), FUtil.lerp(partial, localYo, localY),
                FUtil.lerp(partial, localZo, localZ));
        if (config.noise.enable) {
            pos.add(noisePosition(partial));
        }
        return pos;
    }

    Vec3 getWorldPos(float partial) {
        return spaceTransform().transformPosition(getLocalPos(partial));
    }

    float[] getRealColor(float partial) {
        float[] e = emitter.rgba();
        return new float[]{e[0] * FUtil.lerp(partial, ro, r), e[1] * FUtil.lerp(partial, go, g),
                e[2] * FUtil.lerp(partial, bo, b), e[3] * FUtil.lerp(partial, ao, a)};
    }

    UvRect getRealUVs(float partial) {
        if (config.uvAnimation.enable) {
            return uvs(partial);
        }
        return UvRect.FULL;
    }

    private UvRect uvs(float partial) {
        ForgeConfig.UvAnimationCfg c = config.uvAnimation;
        float t = getT(partial);
        int tilesA = (int) c.tiles.a;
        int tilesB = (int) c.tiles.b;
        float cellU = 1f / tilesA;
        float cellV = 1f / tilesB;
        float frame = (float) c.startFrame.get(t, () -> getMemRandom("startFrame"));
        frame += c.cycle * (float) c.frameOverTime.get(t, () -> getMemRandom("frameOverTime"));
        float u0, v0;
        if (c.animation == ForgeConfig.UvAnimationMode.WholeSheet) {
            int x = (int) (frame % tilesA);
            int y = (int) (frame / tilesA);
            u0 = x * cellU;
            v0 = y * cellV;
        } else {
            int x = (int) (frame % tilesA);
            int y = (int) (getMemRandom("randomRow") * tilesB);
            u0 = x * cellU;
            v0 = y * cellV;
        }
        if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(cellU) || !Float.isFinite(cellV)) {
            return UvRect.FULL;
        }
        return new UvRect(u0, v0, u0 + cellU, v0 + cellV);
    }

    int getRealLight(float partial) {
        if (config.renderer.bloomEffect) {
            return FUtil.FULL_BRIGHT;
        }
        if (config.lights.enable) {
            float t = getT(partial);
            int sky = (int) config.lights.skyLight.get(t, () -> getMemRandom("sky-light"));
            int block = (int) config.lights.blockLight.get(t, () -> getMemRandom("block-light"));
            return sky << 20 | block << 4;
        }
        return light;
    }

    @Override
    void tick() {
        if (delay > 0) {
            delay--;
            return;
        }
        updateOrigin();

        if (age == 0 && config.subEmitters.enable) {
            triggerEvent(SubEvent.Birth);
        }

        if (age++ >= lifetime && lifetime > 0) {
            removed = true;
            if (config.subEmitters.enable) {
                triggerEvent(SubEvent.Death);
            }
        }

        update();

        if (config.subEmitters.enable) {
            triggerEvent(SubEvent.Tick);
        }

        if (lifetime > 0) {
            t = 1.0f * age / lifetime;
        }
    }

    private void updateOrigin() {
        localXo = localX;
        localYo = localY;
        localZo = localZ;
        rotationXo = rotationX;
        rotationYo = rotationY;
        rotationZo = rotationZ;
        sizeXo = sizeX;
        sizeYo = sizeY;
        sizeZo = sizeZ;
        ro = r;
        go = g;
        bo = b;
        ao = a;
    }

    private void update() {
        updatePositionAndInternalVelocity();
        updateColor();
        updateSize();
        updateRotation();
        updateLight();
    }

    private void updatePositionAndInternalVelocity() {
        Vec3 velocity = getRealVelocity();
        float moveX = velocity.x;
        float moveY = velocity.y;
        float moveZ = velocity.z;

        FCollision world = emitter.sim.collision();
        if (config.physics.enable && config.physics.hasCollision && world != null
                && (moveX != 0.0 || moveY != 0.0 || moveZ != 0.0)
                && moveX * moveX + moveY * moveY + moveZ * moveZ < MAXIMUM_COLLISION_VELOCITY_SQUARED) {
            Vec3 center = getWorldPos(0);
            FCollision.Box box = new FCollision.Box(center.x - sizeX / 2.0, center.y - sizeY / 2.0, center.z - sizeZ / 2.0,
                    center.x + sizeX / 2.0, center.y + sizeY / 2.0, center.z + sizeZ / 2.0);
            Vec3 moved = world.collide(new Vec3(moveX, moveY, moveZ), box);
            moveX = moved.x;
            moveY = moved.y;
            moveZ = moved.z;
        }

        if (moveX != 0.0 || moveY != 0.0 || moveZ != 0.0) {
            Vec3 moveLocal = spaceTransformInverse().transformDirection(new Vec3(moveX, moveY, moveZ));
            localX += moveLocal.x;
            localY += moveLocal.y;
            localZ += moveLocal.z;
        }

        if (!config.physics.enable) {
            return;
        }
        if (config.physics.hasCollision && !collided) {
            float bounceChance = (float) config.physics.bounceChance.get(t, () -> getMemRandom("bounceChance"));
            float bounceRate = (float) config.physics.bounceRate.get(t, () -> getMemRandom("bounceRate"));
            float bounceSpreadRate = (float) config.physics.bounceSpreadRate.get(t, () -> getMemRandom("bounceSpreadRate"));
            if (Math.abs(velocity.x) / Math.abs(moveX) > 1.001) {
                updateCollisionBounce(bounceChance, velocity, bounceRate, bounceSpreadRate, 0);
            } else if (Math.abs(velocity.y) / Math.abs(moveY) > 1.001) {
                updateCollisionBounce(bounceChance, velocity, bounceRate, bounceSpreadRate, 1);
            } else if (Math.abs(velocity.z) / Math.abs(moveZ) > 1.001) {
                updateCollisionBounce(bounceChance, velocity, bounceRate, bounceSpreadRate, 2);
            }
        }

        float gravity = (float) config.physics.gravity.get(t, () -> getMemRandom("gravity"));
        if (gravity != 0) {
            addInternalVelocity(spaceTransformInverse().transformDirection(new Vec3(0, -gravity * 0.04f, 0)));
        }

        float friction = (float) config.physics.friction.get(t, () -> getMemRandom("friction"));
        velocityX *= friction;
        velocityY *= friction;
        velocityZ *= friction;

        if (collided && friction != 1.0) {
            velocityX *= 0.7F;
            velocityY *= 0.7F;
            velocityZ *= 0.7F;
        }
    }

    private void updateCollisionBounce(float bounceChance, Vec3 velocity, float bounceRate, float bounceSpreadRate, int axis) {
        if (bounceChance < 1 && bounceChance < random.nextFloat()) {
            collided = true;
        } else {
            float nx = axis == 0 ? -velocity.x * bounceRate
                    : (velocity.x + (bounceSpreadRate > 0 ? (float) (bounceSpreadRate * random.nextGaussian()) : 0));
            float ny = axis == 1 ? -velocity.y * bounceRate
                    : (velocity.y + (bounceSpreadRate > 0 ? (float) (bounceSpreadRate * random.nextGaussian()) : 0));
            float nz = axis == 2 ? -velocity.z * bounceRate
                    : (velocity.z + (bounceSpreadRate > 0 ? (float) (bounceSpreadRate * random.nextGaussian()) : 0));
            setInternalVelocity(spaceTransformInverse().transformDirection(new Vec3(nx, ny, nz)));
        }
        if (config.physics.enable && config.physics.removedWhenCollided) {
            removed = true;
            if (config.subEmitters.enable) {
                triggerEvent(SubEvent.Death);
            }
        }
        if (config.subEmitters.enable) {
            triggerEvent(SubEvent.Collision);
            if (!firstCollision) {
                firstCollision = true;
                triggerEvent(SubEvent.FirstCollision);
            }
        }
    }

    Vec3 getInternalVelocity() {
        Vec3 velocity = new Vec3(velocityX, velocityY, velocityZ);
        if (config.velocityOverLifetime.enable) {
            velocity.add(velocityAddition());
        }
        if (config.forceOverLifetime.enable && config.forceOverLifetime.simulationSpace == ForgeConfig.Space.Local) {
            velocity.add(force());
        }
        return velocity;
    }

    void setInternalVelocity(Vec3 v) {
        velocityX = v.x;
        velocityY = v.y;
        velocityZ = v.z;
    }

    Vec3 getRealVelocity() {
        Vec3 velocity = spaceTransform().transformDirection(getInternalVelocity());
        if (config.forceOverLifetime.enable && config.forceOverLifetime.simulationSpace == ForgeConfig.Space.World) {
            velocity.add(force());
        }
        if (config.inheritVelocity.enable && config.inheritVelocity.mode == ForgeConfig.InheritMode.CURRENT) {
            velocity.add(emitter.velocity);
        }
        if (config.velocityOverLifetime.enable) {
            float multiplier = (float) config.velocityOverLifetime.speedModifier.get(t,
                    () -> getMemRandom(config.velocityOverLifetime));
            velocity.mul(multiplier);
        }
        return velocity;
    }

    private Vec3 force() {
        ForgeConfig.ForceOverLifetimeCfg c = config.forceOverLifetime;
        return c.force.get(t, () -> getMemRandom(c)).mul(0.05f);
    }

    private Vec3 velocityAddition() {
        ForgeConfig.VelocityOverLifetimeCfg c = config.velocityOverLifetime;
        float lifetimeT = t;
        Vec3 addition = c.linear.get(lifetimeT, () -> getMemRandom("vol0")).mul(0.05f);
        Vec3 orbital = c.orbital.get(lifetimeT, () -> getMemRandom("vol1"));
        Vec3 center = c.offset.get(lifetimeT, () -> getMemRandom("vol2"));
        if (orbital.x == 0 && orbital.y == 0 && orbital.z == 0) {
            return addition;
        }
        Vec3[] axes = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        float[] speeds = {orbital.x, orbital.y, orbital.z};
        switch (c.orbitalMode) {
            case AngularVelocity -> {
                Vec3 toPoint = getLocalPos(0).sub(center);
                for (int i = 0; i < 3; i++) {
                    if (speeds[i] != 0) {
                        Vec3 radius = toPoint.copy().sub(FUtil.project(toPoint.copy(), axes[i]));
                        addition.add(rotateAbout(radius, i, speeds[i] * 0.05f).sub(radius));
                    }
                }
            }
            case LinearVelocity -> {
                Vec3 toPoint = getLocalPos(0).sub(center);
                for (int i = 0; i < 3; i++) {
                    if (speeds[i] != 0) {
                        Vec3 radius = toPoint.copy().sub(FUtil.project(toPoint.copy(), axes[i]));
                        float length = radius.length();
                        if (length > 1e-8f) {
                            addition.add(rotateAbout(radius, i, speeds[i] * 0.05f / length).sub(radius));
                        }
                    }
                }
            }
            case FixedVelocity -> {
                Vec3 toCenter = center.sub(getLocalPos(0));
                for (int i = 0; i < 3; i++) {
                    if (speeds[i] != 0) {
                        addition.add(toCenter.copy().cross(axes[i]).normalize().mul(speeds[i] * 0.05f));
                    }
                }
            }
        }
        return addition;
    }

    private void updateSize() {
        if (config.sizeBySpeed.enable || config.sizeOverLifetime.enable || config.noise.enable) {
            Vec3 size = initialSize.copy();
            Vec3 mul = new Vec3(1, 1, 1);
            if (config.noise.enable) {
                size.add(noiseSize(0));
            }
            if (config.sizeBySpeed.enable) {
                ForgeConfig.SizeBySpeedCfg c = config.sizeBySpeed;
                float value = getRealVelocity().length() * 20;
                float st = (value - c.speedRange.a) / (c.speedRange.b - c.speedRange.a);
                mul.mul(c.size.get(safe(st), () -> getMemRandom("sbs0")));
            }
            if (config.sizeOverLifetime.enable) {
                mul.mul(config.sizeOverLifetime.size.get(getT(0), () -> getMemRandom("sol0")));
            }
            setSize(size.mul(mul));
        }
    }

    private void updateRotation() {
        if (config.rotationOverLifetime.enable || config.rotationBySpeed.enable || config.noise.enable) {
            Vec3 rotation = initialRotation.copy();
            if (config.rotationOverLifetime.enable) {
                ForgeConfig.RotationOverLifetimeCfg c = config.rotationOverLifetime;
                float rt = getT(0);
                rotation.add(new Vec3((float) c.yaw.get(rt, () -> getMemRandom("rol2")),
                        (float) c.pitch.get(rt, () -> getMemRandom("rol1")),
                        (float) c.roll.get(rt, () -> getMemRandom("rol0"))).mul(FUtil.TWO_PI / 360));
            }
            if (config.rotationBySpeed.enable) {
                ForgeConfig.RotationBySpeedCfg c = config.rotationBySpeed;
                float value = getRealVelocity().length() * 20;
                float st = safe((value - c.speedRange.a) / (c.speedRange.b - c.speedRange.a));
                rotation.add(new Vec3((float) c.roll.get(st, () -> getMemRandom("rbs0")),
                        (float) c.pitch.get(st, () -> getMemRandom("rbs1")),
                        (float) c.yaw.get(st, () -> getMemRandom("rbs2"))).mul(FUtil.TWO_PI / 360));
            }
            if (config.noise.enable) {
                rotation.add(noiseRotation(0));
            }
            setRotation(rotation);
        }
    }

    private void updateColor() {
        if (config.colorOverLifetime.enable || config.colorBySpeed.enable) {
            float[] color = initialColor.clone();
            if (config.colorOverLifetime.enable) {
                ForgeConfig.ColorOverLifetimeCfg c = config.colorOverLifetime;
                int packed = (int) c.color.get(getT(0), () -> getMemRandom(c));
                color[0] *= FUtil.red(packed);
                color[1] *= FUtil.green(packed);
                color[2] *= FUtil.blue(packed);
                color[3] *= FUtil.alpha(packed);
            }
            if (config.colorBySpeed.enable) {
                ForgeConfig.ColorBySpeedCfg c = config.colorBySpeed;
                float value = getRealVelocity().length() * 20;
                float st = safe((value - c.speedRange.a) / (c.speedRange.b - c.speedRange.a));
                int packed = (int) c.color.get(st, () -> getMemRandom(c));
                color[0] *= FUtil.red(packed);
                color[1] *= FUtil.green(packed);
                color[2] *= FUtil.blue(packed);
                color[3] *= FUtil.alpha(packed);
            }
            setColor(color);
        }
    }

    private void updateLight() {
        if (config.lights.enable || config.renderer.bloomEffect) {
            return;
        }

        light = FUtil.FULL_BRIGHT;
    }

    private float noise(float t) {
        NoiseCfg n = config.noise;
        double input = t * n.frequency;
        float value = (float) switch (n.quality) {
            case Noise1D -> emitter.noiseGen.noise(input);
            case Noise2D -> emitter.noiseGen.noise(input, input);
            case Noise3D -> emitter.noiseGen.noise(input, input, input);
        };
        if (n.remapEnable && n.remapCurve != null) {
            value = (float) n.remapCurve.get((value + 1) / 2, () -> 0f);
        }
        return value;
    }

    private void setupSeed() {
        emitter.noiseGen.setSeed(getMemRandom("noise-seed", rnd -> (float) rnd.nextGaussian()) * 255);
    }

    private Vec3 noiseRotation(float partial) {
        setupSeed();
        float t = getT(partial);
        float degree = (float) config.noise.rotation.get(t, () -> getMemRandom("noise-rotation"));
        if (degree != 0) {
            return new Vec3(degree, 0, 0).mul(noise((t + 10 * getMemRandom("noise-rotation-degree")) * 100)
                    * FUtil.TWO_PI / 360);
        }
        return new Vec3(0, 0, 0);
    }

    private Vec3 noiseSize(float partial) {
        setupSeed();
        float t = getT(partial);
        float scale = (float) config.noise.size.get(t, () -> getMemRandom("noise-size"));
        if (scale != 0) {
            return new Vec3(scale, scale, scale).mul(noise((t + 10 * getMemRandom("noise-size-scale")) * 100));
        }
        return new Vec3(0, 0, 0);
    }

    private Vec3 noisePosition(float partial) {
        setupSeed();
        float t = getT(partial);
        Vec3 offset = config.noise.position.get(t, () -> getMemRandom("noise-position"));
        if (!(offset.x == 0 && offset.y == 0 && offset.z == 0)) {
            offset.mul(noise((t + 10 * getMemRandom("noise-position-x")) * 100),
                    noise((t + 10 * getMemRandom("noise-position-y")) * 100),
                    noise((t + 10 * getMemRandom("noise-position-z")) * 100));
            return offset;
        }
        return new Vec3(0, 0, 0);
    }

    private void triggerEvent(SubEvent event) {
        for (SubEmitterEntry entry : config.subEmitters.emitters) {
            if (entry.event == event) {
                spawn(entry);
            }
        }
    }

    private void spawn(SubEmitterEntry entry) {
        int interval = Math.max(1, entry.tickInterval);
        if (age % interval != 0) {
            return;
        }
        float roll = random.nextFloat();
        float probability = (float) entry.emitProbability.get(getT(0), () -> getMemRandom("sub_emitter_probability"));
        if (!(roll < probability)) {
            return;
        }
        FRuntime sub = emitter.sim.createSubRuntime(entry.emitter, emitter.runtime.depth + 1);
        if (sub == null) {
            return;
        }
        sub.root.transform.setPosition(getWorldPos(0));
        for (FObject object : sub.objects()) {
            if (object instanceof FEmitter child) {
                if (entry.inheritColor) {
                    float[] c = getRealColor(0);
                    child.rCol = c[0];
                    child.gCol = c[1];
                    child.bCol = c[2];
                    child.alpha = c[3];
                }
                if (entry.inheritSize) {
                    child.transform.setScale(getRealSize(0));
                }
                if (entry.inheritRotation) {
                    Vec3 xyz = getRealRotation(0);
                    child.transform.setRotation(Quat.rotationXYZ(xyz.x, xyz.y, xyz.z));
                }
            }
        }
        sub.emmit();
    }

    private Quat modeQuaternion(FCtx ctx) {
        return switch (config.renderer.renderMode) {
            case Billboard -> ctx.cameraRotation.copy();
            case Horizontal -> Quat.rotationX((float) Math.toRadians(90));
            case Vertical, Model -> Quat.identity();
            case VerticalBillboard -> Quat.rotationY((float) (ctx.yaw + Math.PI));
        };
    }

    @Override
    Object renderKey() {
        return config;
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !emitter.isVisible()) {
            return;
        }
        IMaterial material = config.material.material;
        if (material == null) {
            return;
        }
        float partial = ctx.partial;
        Vec3 center = getLocalPos(partial);
        center = spaceTransform().transformPosition(center);

        float[] color = getRealColor(partial);
        int light = getRealLight(partial);
        int sky = FUtil.skyOf(light);
        int block = FUtil.blockOf(light);
        Vec3 rotation = getRealRotation(partial);
        Vec3 size = getRealSize(partial);
        UvRect uv = getRealUVs(partial);
        ForgeConfig.RenderMode mode = config.renderer.renderMode;
        var settings = FCtx.settings(config.renderer, config.material);
        float depth = ctx.distance(center);

        if (mode == ForgeConfig.RenderMode.Model) {
            gatherCube(ctx, center, rotation, size, color, sky, block, uv, material, settings, depth);
            return;
        }

        Quat quaternion = modeQuaternion(ctx);
        if (rotation.x != 0 || rotation.y != 0 || rotation.z != 0) {
            quaternion = quaternion.multiply(Quat.rotationXYZ(rotation.x, rotation.y, rotation.z));
        }
        Vec3 scale = spaceScale();
        Vec3[] corners = {new Vec3(-1, -1, 0), new Vec3(-1, 1, 0), new Vec3(1, 1, 0), new Vec3(1, -1, 0)};
        for (Vec3 corner : corners) {
            corner.mul(size.x, size.y, size.z);
            Vec3 rotated = quaternion.rotate(corner);
            rotated.mul(scale).add(center);
            corner.set(rotated);
        }
        ctx.items.add(new RenderItem.QuadItem(corners[0], corners[1], corners[2], corners[3], uv,
                FUtil.argb(color[0], color[1], color[2], color[3]), block, sky, material, config.material.blendMode,
                config.material.cull, settings, depth));
    }

    private void gatherCube(FCtx ctx, Vec3 center, Vec3 rotation, Vec3 size, float[] color, int sky, int block, UvRect uv,
                            IMaterial material, net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings settings,
                            float depth) {
        Quat q = Quat.rotationXYZ(rotation.x, rotation.y, rotation.z);
        boolean shade = config.renderer.shade;

        float[][][] faces = {
                {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
                {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
                {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
                {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
                {{1, 1, 1}, {1, 1, 0}, {0, 1, 0}, {0, 1, 1}},
                {{1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, 0, 0}},
        };
        float[] brightness = {0.8f, 0.8f, 0.6f, 0.6f, 0.9f, 0.9f};
        for (int f = 0; f < faces.length; f++) {
            Vec3[] v = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                float[] p = faces[f][i];
                Vec3 local = new Vec3((p[0] - 0.5f) * size.x, (p[1] - 0.5f) * size.y, (p[2] - 0.5f) * size.z);
                v[i] = q.rotate(local).add(center);
            }
            float shadeFactor = shade ? brightness[f] : 1f;
            ctx.items.add(new RenderItem.QuadItem(v[0], v[1], v[2], v[3], uv,
                    FUtil.argb(color[0] * shadeFactor, color[1] * shadeFactor, color[2] * shadeFactor, color[3]),
                    block, sky, material, config.material.blendMode, config.material.cull, settings, depth));
        }
    }
}

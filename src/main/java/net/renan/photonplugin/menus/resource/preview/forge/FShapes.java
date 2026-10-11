package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.MeshData;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.ShapeCfg;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.ShapeDef;

import java.util.Random;

final class FShapes {
    private static final float TWO_PI_F = (float) (Math.PI * 2);

    private FShapes() {
    }

    static void setup(FTile particle, FParticleEmitter emitter) {
        ShapeCfg cfg = emitter.config.shape;
        float t = emitter.t;
        Vec3 position = cfg.position.get(t, () -> emitter.getMemRandom("shape_position"));
        Vec3 rotation = cfg.rotation.get(t, () -> emitter.getMemRandom("shape_rotation")).mul(FUtil.TWO_PI / 360f);
        Vec3 scale = cfg.scale.get(t, () -> emitter.getMemRandom("shape_scale"));
        nextPosVel(cfg.shape, particle, emitter, position, rotation, scale);
    }

    private static void place(FTile particle, Vec3 pos, Vec3 velocity, Vec3 position, Vec3 rotation) {
        Vec3 local = FUtil.rotateYXY(pos, rotation).add(position).add(particle.getLocalPos(0));
        particle.setLocalPos(local, true);
        particle.setInternalVelocity(FUtil.rotateYXY(velocity, rotation));
    }

    private static void nextPosVel(ShapeDef shape, FTile particle, FParticleEmitter emitter, Vec3 position,
                                   Vec3 rotation, Vec3 scale) {
        Random random = particle.random;
        switch (shape.type) {
            case "dot" -> {
                particle.setLocalPos(position.add(particle.getLocalPos(0)), true);
                particle.setInternalVelocity(new Vec3(0, 0, 0));
            }
            case "box" -> box(shape, particle, random, position, rotation, scale);
            case "circle", "cone", "cylinder" -> disc(shape, particle, random, position, rotation, scale);
            case "sphere" -> sphere(shape, particle, random, position, rotation, scale);
            case "function" -> function(shape, particle, emitter, position, rotation);
            case "mesh" -> mesh(shape, particle, random, position, rotation, scale);
            default -> {
                particle.setLocalPos(position.add(particle.getLocalPos(0)), true);
                particle.setInternalVelocity(new Vec3(0, 0, 0));
            }
        }
    }

    private static void box(ShapeDef shape, FTile particle, Random random, Vec3 position, Vec3 rotation, Vec3 scale) {
        Vec3 s = new Vec3(Math.abs(scale.x), Math.abs(scale.y), Math.abs(scale.z)).mul(0.5f);
        Vec3 pos = new Vec3(random.nextFloat() * 2 * s.x - s.x, random.nextFloat() * 2 * s.y - s.y,
                random.nextFloat() * 2 * s.z - s.z);
        if (shape.boxType == ForgeConfig.BoxType.Shell) {
            double xy = s.x * s.y;
            double yz = s.y * s.z;
            double xz = s.x * s.z;
            double value = random.nextDouble() * (xy + yz + xz);
            if (value < xy) {
                pos.z = random.nextFloat() > 0.5 ? s.z : -s.z;
            } else if (value < yz + xy) {
                pos.x = random.nextFloat() > 0.5 ? s.x : -s.x;
            } else {
                pos.y = random.nextFloat() > 0.5 ? s.y : -s.y;
            }
        } else if (shape.boxType == ForgeConfig.BoxType.Edge) {
            double value = random.nextDouble() * (s.x + s.y + s.z);
            if (value < s.x) {
                pos.z = random.nextFloat() > 0.5 ? s.z : -s.z;
                pos.y = random.nextFloat() > 0.5 ? s.y : -s.y;
            } else if (value < s.x + s.y) {
                pos.z = random.nextFloat() > 0.5 ? s.z : -s.z;
                pos.x = random.nextFloat() > 0.5 ? s.x : -s.x;
            } else {
                pos.x = random.nextFloat() > 0.5 ? s.x : -s.x;
                pos.y = random.nextFloat() > 0.5 ? s.y : -s.y;
            }
        }
        place(particle, pos, new Vec3(0, 0.05f, 0), position, rotation);
    }

    private static void disc(ShapeDef shape, FTile particle, Random random, Vec3 position, Vec3 rotation, Vec3 scale) {
        float radius = shape.radius;
        double outer = radius;
        double inner = (1 - shape.radiusThickness) * radius;
        double origin = inner * inner;
        double bound = outer * outer;
        double r = outer == inner ? outer : Math.sqrt(origin + random.nextDouble() * (bound - origin));
        double theta = shape.arc * TWO_PI_F * random.nextDouble() / 360;
        switch (shape.type) {
            case "circle" -> {
                Vec3 pos = new Vec3((float) (r * Math.cos(theta)), 0f, (float) (r * Math.sin(theta))).mul(scale);
                place(particle, pos, pos.copy().normalize().mul(0.05f), position, rotation);
            }
            case "cone" -> {
                Vec3 pos = new Vec3((float) (r * Math.cos(theta)), 0f, (float) (r * Math.sin(theta))).mul(scale);

                Vec3 speed = new Vec3(0, 1, 0);
                speed.rotateAroundAxis(new Vec3(0, 0, -1), (float) ((r / radius) * Math.toRadians(shape.angle)));
                speed.rotateAroundAxis(new Vec3(0, -1, 0), (float) theta);
                place(particle, pos, speed.normalize().mul(0.05f), position, rotation);
            }
            default -> {
                Vec3 pos = new Vec3((float) (r * Math.cos(theta)), random.nextFloat() * scale.y - scale.y / 2,
                        (float) (r * Math.sin(theta))).mul(scale);
                place(particle, pos, pos.copy().normalize().mul(0.05f), position, rotation);
            }
        }
    }

    private static void sphere(ShapeDef shape, FTile particle, Random random, Vec3 position, Vec3 rotation, Vec3 scale) {
        float radius = shape.radius;
        double outer = radius;
        double inner = (1 - shape.radiusThickness) * radius;
        double origin = inner * inner * inner;
        double bound = outer * outer * outer;
        double r = outer == inner ? outer : Math.cbrt(origin + random.nextDouble() * (bound - origin));
        double theta = Math.acos(2 * random.nextDouble() - 1);
        double phi = shape.arc * TWO_PI_F * random.nextDouble() / 360;
        Vec3 pos = new Vec3((float) (r * Math.sin(theta) * Math.cos(phi)), (float) (r * Math.sin(theta) * Math.sin(phi)),
                (float) (r * Math.cos(theta))).mul(scale);
        place(particle, pos, pos.copy().normalize().mul(0.05f), position, rotation);
    }

    private static void function(ShapeDef shape, FTile particle, FParticleEmitter emitter, Vec3 position, Vec3 rotation) {
        FExpr.Env env = new FExpr.Env();
        env.t = emitter.t;
        for (int i = 0; i < 5; i++) {
            env.random[i] = particle.random.nextFloat();
        }
        Vec3 pos = new Vec3(eval(emitter.expr(shape.x), env), eval(emitter.expr(shape.y), env), eval(emitter.expr(shape.z), env));
        Vec3 speed = new Vec3(eval(emitter.expr(shape.speedX), env), eval(emitter.expr(shape.speedY), env),
                eval(emitter.expr(shape.speedZ), env));

        place(particle, pos, speed.normalize().mul(0.05f), position, rotation);
    }

    private static float eval(FExpr expr, FExpr.Env env) {
        if (expr == null) {
            return 0;
        }
        double v = expr.value(env);
        return Double.isFinite(v) ? (float) v : 0;
    }

    private static void mesh(ShapeDef shape, FTile particle, Random random, Vec3 position, Vec3 rotation, Vec3 scale) {
        MeshData data = shape.mesh;
        float t = random.nextFloat();
        Vec3 pos = null;
        switch (shape.meshType) {
            case Vertex -> {
                if (!data.vertices.isEmpty()) {
                    int index = Math.min(data.vertices.size() - 1, (int) (data.vertices.size() * t));
                    float[] v = data.vertices.get(index);
                    pos = new Vec3(v[0], v[1], v[2]);
                }
            }
            case Edge -> {
                if (!data.edges.isEmpty()) {
                    float[][] edge = data.edges.get(pick(data.edgeLengths, data.edgeSumLength * t, data.edges.size()));
                    float f = random.nextFloat();
                    pos = new Vec3(edge[1][0] - edge[0][0], edge[1][1] - edge[0][1], edge[1][2] - edge[0][2]).mul(f)
                            .add(edge[0][0], edge[0][1], edge[0][2]);
                }
            }
            case Triangle -> {
                if (!data.triangles.isEmpty()) {
                    float[][] tri = data.triangles.get(pick(data.triangleAreas, data.triangleSumArea * t,
                            data.triangles.size()));
                    float sqrtR = (float) Math.sqrt(random.nextFloat());
                    float a = 1 - sqrtR;
                    float r2 = random.nextFloat();
                    float b = sqrtR * (1 - r2);
                    float c = sqrtR * r2;
                    pos = new Vec3(a * tri[0][0] + b * tri[1][0] + c * tri[2][0],
                            a * tri[0][1] + b * tri[1][1] + c * tri[2][1],
                            a * tri[0][2] + b * tri[1][2] + c * tri[2][2]);
                }
            }
        }
        if (pos != null) {
            pos.mul(scale);
            place(particle, pos, new Vec3(0, 0, 0), position, rotation);
        }
    }

    private static int pick(java.util.List<Double> weights, double target, int count) {
        double sum = 0;
        for (int i = 0; i < weights.size() && i < count; i++) {
            sum += weights.get(i);
            if (target <= sum) {
                return i;
            }
        }
        return Math.min(count, Math.max(1, weights.size())) - 1;
    }
}

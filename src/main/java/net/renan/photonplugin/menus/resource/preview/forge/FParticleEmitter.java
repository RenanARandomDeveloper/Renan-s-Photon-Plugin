package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.PerlinNoise;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.Burst;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.ParticleCfg;

import java.util.*;

final class FParticleEmitter extends FEmitter {
    final ParticleCfg config;
    final PerlinNoise noiseGen = new PerlinNoise(0);

    private final Map<Object, ArrayDeque<FParticle>> particles = new LinkedHashMap<>();
    private final ArrayDeque<FParticle> waitToAdded = new ArrayDeque<>();
    private final Map<String, FExpr> expressions = new HashMap<>();

    FParticleEmitter(ForgeSimulation sim, FRuntime runtime, ParticleCfg config) {
        super(sim, runtime);
        this.config = config;
    }

    FExpr expr(String text) {
        if (!expressions.containsKey(text)) {
            expressions.put(text, FExpr.parse(text));
        }
        return expressions.get(text);
    }

    @Override
    int getLifetime() {
        return config.duration;
    }

    @Override
    boolean isLooping() {
        return config.looping;
    }

    void emitParticle(FParticle particle) {
        waitToAdded.add(particle);
    }

    @Override
    int getParticleAmount() {
        int sum = waitToAdded.size();
        for (ArrayDeque<FParticle> queue : particles.values()) {
            sum += queue.size();
        }
        return sum;
    }

    @Override
    void update() {
        int available = config.maxParticles - getParticleAmount();
        if (!removed && getParticleAmount() < config.maxParticles) {
            available = Math.min(emissionCount(age, t), available);
            for (int i = 0; i < available; i++) {
                emitParticle(new FTile(this, config, particleRandom));
            }
        }
        if (!waitToAdded.isEmpty()) {
            for (FParticle p : waitToAdded) {
                particles.computeIfAbsent(p.renderKey(), key -> new ArrayDeque<>()).add(p);
            }
            waitToAdded.clear();
        }
        for (ArrayDeque<FParticle> queue : particles.values()) {
            Iterator<FParticle> iterator = queue.iterator();
            while (iterator.hasNext()) {
                FParticle particle = iterator.next();
                if (!particle.isAlive()) {
                    iterator.remove();
                } else {
                    particle.tick();
                }
            }
        }
        baseUpdate();
    }

    private int emissionCount(int emitterAge, float t) {
        ForgeConfig.EmissionCfg e = config.emission;
        double result = e.rate.get(t, particleRandom::nextFloat);
        int number = (int) result;
        float decimals = (float) result - (int) result;
        if (e.mode == ForgeConfig.EmissionMode.Exacting) {
            if (decimals > 0 && emitterAge % ((int) (1 / decimals)) == 0) {
                number += 1;
            }
        } else if (particleRandom.nextFloat() < decimals) {
            number += 1;
        }
        for (Burst burst : e.bursts) {
            int realAge = emitterAge - burst.time;
            if (realAge >= 0) {
                int count = (int) burst.count.get(t, particleRandom::nextFloat);
                int interval = Math.max(1, burst.interval);
                if (realAge % interval == 0) {
                    if (burst.cycles == 0) {
                        if (particleRandom.nextFloat() < burst.probability) {
                            number += count;
                        }
                    } else if (realAge / interval < burst.cycles) {
                        if (particleRandom.nextFloat() < burst.probability) {
                            number += count;
                        }
                    }
                }
            }
        }
        return number;
    }

    @Override
    void reset() {
        super.reset();
        particles.clear();
        waitToAdded.clear();
    }

    @Override
    void remove(boolean force) {
        super.remove(force);
        if (force) {
            particles.clear();
        }
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !isVisible()) {
            return;
        }
        for (ArrayDeque<FParticle> queue : particles.values()) {
            for (FParticle particle : queue) {
                particle.gather(ctx);
            }
        }
    }

    int tileCount() {
        int n = 0;
        for (ArrayDeque<FParticle> queue : particles.values()) {
            for (FParticle p : queue) {
                if (p instanceof FTile) {
                    n++;
                }
            }
        }
        return n;
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public final class EmissionSetting {
    private final NumberFunction emissionRate;
    private final NumberFunction distanceRate;
    private final Mode mode;
    private final List<Burst> bursts;

    public EmissionSetting(NumberFunction emissionRate, NumberFunction distanceRate, Mode mode, List<Burst> bursts) {
        this.emissionRate = emissionRate;
        this.distanceRate = distanceRate;
        this.mode = mode;
        this.bursts = bursts;
    }

    public EmissionSetting withRates(NumberFunction rate, NumberFunction distance) {
        return new EmissionSetting(rate != null ? rate : emissionRate, distance != null ? distance : distanceRate, mode, bursts);
    }

    public int getEmissionCount(State state, Random random, Supplier<Double> lerp, double t, float ageStart,
                                float dt, float loopDuration, boolean looping) {
        float ageEnd = ageStart + dt;
        double timeValue = emissionRate.get(t, lerp);
        double distanceValue = distanceRate.get(t, lerp);

        double acc = state.emissionRateAccum + timeValue * dt;
        int fromRate;
        if (mode == Mode.RANDOM) {
            int whole = (int) acc;
            double frac = acc - whole;
            acc -= whole;
            if (random.nextFloat() < frac) {
                whole += 1;
                acc -= 1;
            }
            state.emissionRateAccum = acc;
            fromRate = whole;
        } else {
            int number = (int) acc;
            state.emissionRateAccum = acc - number;
            fromRate = number;
        }
        return fromRate + distanceAndBursts(state, random, t, lerp, distanceValue, ageStart, ageEnd, loopDuration, looping);
    }

    private int distanceAndBursts(State state, Random random, double t, Supplier<Double> lerp, double distanceValue,
                                  float ageStart, float ageEnd, float loopDuration, boolean looping) {
        int number = 0;
        if (distanceValue > 0) {
            int emitDistance = (int) (state.accumulatedDistance / distanceValue);
            number += emitDistance;
            state.accumulatedDistance -= emitDistance * distanceValue;
        }

        boolean loopingWithDuration = looping && loopDuration > 0;
        for (Burst burst : bursts) {
            int count = (int) burst.count().get(t, lerp);
            if (loopingWithDuration) {
                float sliceDt = Math.min(ageEnd - ageStart, loopDuration);
                float loopedStart = ageStart % loopDuration;
                float loopedEnd = loopedStart + sliceDt;
                number += burstTriggers(burst, count, random, loopedStart, Math.min(loopedEnd, loopDuration));
                if (loopedEnd > loopDuration) {
                    number += burstTriggers(burst, count, random, 0, loopedEnd - loopDuration);
                }
            } else {
                number += burstTriggers(burst, count, random, ageStart, ageEnd);
            }
        }
        return number;
    }

    private int burstTriggers(Burst burst, int count, Random random, float start, float end) {
        int number = 0;
        int firstK = (int) Math.ceil((start - burst.time()) / (double) burst.interval());
        if (firstK < 0) {
            firstK = 0;
        }
        for (int k = firstK; ; k++) {
            if (burst.cycles() > 0 && k >= burst.cycles()) {
                break;
            }
            float triggerAge = burst.time() + (long) k * burst.interval();
            if (triggerAge < start) {
                continue;
            }
            if (triggerAge >= end) {
                break;
            }
            if (random.nextFloat() < burst.probability()) {
                number += count;
            }
        }
        return number;
    }

    public enum Mode {EXACTING, RANDOM}

    public record Burst(int time, NumberFunction count, int cycles, int interval, float probability) {
    }

    public static final class State {
        public double emissionRateAccum;
        public double accumulatedDistance;
    }
}

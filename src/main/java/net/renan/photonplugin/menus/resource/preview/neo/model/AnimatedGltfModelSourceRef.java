package net.renan.photonplugin.menus.resource.preview.neo.model;

import java.util.List;

public record AnimatedGltfModelSourceRef(String modelLocation, boolean flipV, String animation, float speed,
                                         boolean loop, boolean perParticlePhase, PhaseSource phaseSource,
                                         List<String> animationFiles, int frames,
                                         boolean interpolate) implements ModelSource {
    public enum PhaseSource {RANDOM, LIFETIME}
}

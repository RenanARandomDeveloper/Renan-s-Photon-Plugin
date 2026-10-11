package net.renan.photonplugin.menus.resource.preview.neo.model.timeline;

public record Clip(double start, double duration, float speed, String targetId, long seed, boolean randomSeed) {
    public double end() {
        return start + duration;
    }

    public boolean contains(double time) {
        return time >= start && time < end();
    }
}

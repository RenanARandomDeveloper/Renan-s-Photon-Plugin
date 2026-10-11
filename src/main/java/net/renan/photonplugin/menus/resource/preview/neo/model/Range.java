package net.renan.photonplugin.menus.resource.preview.neo.model;

public record Range(float a, float b) {
    public static Range of(float a, float b) {
        return new Range(a, b);
    }

    public float fractionOf(float speedBlocksPerSecond) {
        if (b > a) {
            return Math.max(0f, Math.min(1f, (speedBlocksPerSecond - a) / (b - a)));
        }
        return speedBlocksPerSecond >= a ? 1f : 0f;
    }

    public float fractionOfSorted(float speedBlocksPerSecond) {
        return new Range(Math.min(a, b), Math.max(a, b)).fractionOf(speedBlocksPerSecond);
    }
}

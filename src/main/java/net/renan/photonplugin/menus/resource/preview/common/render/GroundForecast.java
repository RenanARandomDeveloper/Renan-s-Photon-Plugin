package net.renan.photonplugin.menus.resource.preview.common.render;

public record GroundForecast(float reach, int peakTick, float[] bounds) {
    public static final GroundForecast NONE = new GroundForecast(0f, -1, null);
}

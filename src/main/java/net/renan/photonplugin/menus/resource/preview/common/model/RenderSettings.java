package net.renan.photonplugin.menus.resource.preview.common.model;

public record RenderSettings(Layer layer, int orderInLayer, VertexSorting vertexSorting) {
    public static final RenderSettings DEFAULT = new RenderSettings(Layer.TRANSLUCENT, 0, VertexSorting.NONE);

    public boolean translucent() {
        return layer == Layer.TRANSLUCENT;
    }

    public enum Layer {OPAQUE, TRANSLUCENT}

    public enum VertexSorting {NONE, DISTANCE}
}

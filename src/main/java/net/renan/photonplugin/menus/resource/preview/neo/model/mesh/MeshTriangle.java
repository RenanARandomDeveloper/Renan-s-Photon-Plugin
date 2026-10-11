package net.renan.photonplugin.menus.resource.preview.neo.model.mesh;

public record MeshTriangle(MeshVertex a, MeshVertex b, MeshVertex c, String textureOverride, float shade) {
    public MeshTriangle(MeshVertex a, MeshVertex b, MeshVertex c) {
        this(a, b, c, null, 1f);
    }
}

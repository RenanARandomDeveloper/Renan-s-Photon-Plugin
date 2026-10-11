package net.renan.photonplugin.menus.resource.preview.neo.model.mesh;

import java.util.List;

public record ObjMesh(List<MeshTriangle> triangles) {
    public static final ObjMesh EMPTY = new ObjMesh(List.of());

    public boolean isEmpty() {
        return triangles.isEmpty();
    }
}

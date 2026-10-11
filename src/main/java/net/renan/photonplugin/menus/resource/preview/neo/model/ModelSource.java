package net.renan.photonplugin.menus.resource.preview.neo.model;

public sealed interface ModelSource permits ObjModelSourceRef, JsonModelSourceRef, GltfModelSourceRef,
        AnimatedGltfModelSourceRef, UnsupportedModelSource, LibraryMeshRef {
}

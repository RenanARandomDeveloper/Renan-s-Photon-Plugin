package net.renan.photonplugin.menus.resource.preview.neo.model.mesh;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public record SkinnedVertex(Vec3 position, Vec3 normal, float u, float v, int[] joints, float[] weights) {
}

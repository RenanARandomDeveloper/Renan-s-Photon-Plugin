package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.SubEmitterDef;

public record SubEmitterSpawnRequest(String fxId, Vec3 worldPosition, SubEmitterDef def, float fatherAge,
                                     float fatherLifetime, int fatherColor, Vec3 fatherSize,
                                     Vec3 fatherRotationRad) {
}

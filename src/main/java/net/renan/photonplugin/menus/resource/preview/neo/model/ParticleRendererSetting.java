package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.RenderSettings;

import java.util.List;

public record ParticleRendererSetting(RenderMode renderMode, FacingMode facingMode, FacingDirection facingDirection,
                                      RenderSettings renderSettings, float velocityScale,
                                      float lengthScale, Vec3 modelPivot, ModelSource modelSource,
                                      List<MaterialLayer> materials, boolean shade) {
}

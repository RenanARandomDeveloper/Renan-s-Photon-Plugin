package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.ConstantFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

public record LightEmission(boolean enable, float ratio, int maxLights, boolean useParticleColor, NumberFunction color,
                            NumberFunction intensity, NumberFunction range, boolean alphaAffectsIntensity,
                            boolean sizeAffectsRange, boolean castShadows, float sourceRadius, Spot spot,
                            boolean volumetricEnable, NumberFunction volumetricStrength) {
    public static final LightEmission DISABLED = new LightEmission(false, 1f, 32, true, new ConstantFunction(-1),
            new ConstantFunction(4), new ConstantFunction(4), true, false, false, 0.1f, Spot.DEFAULT, false,
            new ConstantFunction(1));

    public record Spot(boolean enable, float innerAngle, float outerAngle, boolean alignToVelocity, Vec3 direction,
                       ValueSpace space, String customSpaceId) {
        public static final Spot DEFAULT = new Spot(false, 20f, 35f, true, new Vec3(0, -1, 0), ValueSpace.LOCAL, "");
    }
}

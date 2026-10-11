package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;

public record TrailsOnParticle(boolean enable, float ratio, NumberFunction lifetime, boolean dieWithParticles,
                               boolean sizeAffectsWidth, boolean sizeAffectsLifetime,
                               boolean inheritParticleColor, NumberFunction colorOverLifetime,
                               Type trailType, TrailConfig trailConfig, AraTrailConfig araConfig) {
    public enum Type {TRAIL, ARA_TRAIL}
}

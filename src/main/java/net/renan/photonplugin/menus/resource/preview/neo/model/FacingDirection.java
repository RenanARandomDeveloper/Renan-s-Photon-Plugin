package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

public record FacingDirection(Mode mode, float minSpeedThreshold, Vec3 customDirection) {
    public enum Mode {CUSTOM, DERIVE_FROM_VELOCITY}
}

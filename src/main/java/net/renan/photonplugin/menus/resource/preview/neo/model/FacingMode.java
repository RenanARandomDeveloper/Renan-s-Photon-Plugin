package net.renan.photonplugin.menus.resource.preview.neo.model;

public enum FacingMode {
    DEFAULT, ROTATE_Y, LOOKAT_XYZ, LOOKAT_Y, LOOKAT_DIRECTION, DIRECTION_X, DIRECTION_Y, DIRECTION_Z,
    EMITTER_TRANSFORM_XY, EMITTER_TRANSFORM_XZ, EMITTER_TRANSFORM_YZ;

    public boolean requiresDirection() {
        return this == LOOKAT_DIRECTION || this == DIRECTION_X || this == DIRECTION_Y || this == DIRECTION_Z;
    }
}

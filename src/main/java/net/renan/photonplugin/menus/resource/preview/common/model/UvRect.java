package net.renan.photonplugin.menus.resource.preview.common.model;

public record UvRect(float u0, float v0, float u1, float v1) {
    public static final UvRect FULL = new UvRect(0, 0, 1, 1);
}

package net.renan.photonplugin.menus.resource.preview.neo.model;

public enum ValueSpace {
    LOCAL, WORLD, CUSTOM;

    public static ValueSpace parse(String name, ValueSpace fallback) {
        if (name == null) {
            return fallback;
        }
        return switch (name) {
            case "Local" -> LOCAL;
            case "World" -> WORLD;
            case "Custom" -> CUSTOM;
            default -> fallback;
        };
    }
}

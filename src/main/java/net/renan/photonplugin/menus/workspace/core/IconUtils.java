package net.renan.photonplugin.menus.workspace.core;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IconUtils {

    private static final Map<String, Icon> SCALED_ICON_CACHE = new ConcurrentHashMap<>();

    private IconUtils() {
    }

    public static Icon scaleTo(String resourceKey, Icon icon, int size) {
        if (!(icon instanceof ImageIcon imageIcon)) {
            return icon;
        }

        String cacheKey = resourceKey + "@" + size;
        return SCALED_ICON_CACHE.computeIfAbsent(cacheKey, key -> {
            Image scaled = imageIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        });
    }
}

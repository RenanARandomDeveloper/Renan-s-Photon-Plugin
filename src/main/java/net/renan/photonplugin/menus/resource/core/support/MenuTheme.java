package net.renan.photonplugin.menus.resource.core.support;

import net.mcreator.ui.laf.themes.Theme;

import java.awt.*;

public final class MenuTheme {

    private MenuTheme() {
    }

    public static Color accentColor() {
        try {
            Color c = Theme.current().getInterfaceAccentColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(90, 165, 70);
    }

    public static Color accentColor(int alpha) {
        Color c = accentColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color backgroundColor() {
        try {
            Color c = Theme.current().getBackgroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(45, 45, 45);
    }

    public static Color backgroundColor(int alpha) {
        Color c = backgroundColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color altBackgroundColor() {
        try {
            Color c = Theme.current().getSecondAltBackgroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(55, 55, 55);
    }

    public static Color foregroundColor() {
        try {
            Color c = Theme.current().getForegroundColor();
            if (c != null) return c;
        } catch (Exception ignored) {
        }
        return new Color(210, 210, 210);
    }

    public static Color foregroundColor(int alpha) {
        Color c = foregroundColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color blend(Color base, Color overlay, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                clampChannel(Math.round(base.getRed() + (overlay.getRed() - base.getRed()) * t)),
                clampChannel(Math.round(base.getGreen() + (overlay.getGreen() - base.getGreen()) * t)),
                clampChannel(Math.round(base.getBlue() + (overlay.getBlue() - base.getBlue()) * t)));
    }

    public static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static Color shade(Color c, int amount) {
        return new Color(clampChannel(c.getRed() + amount), clampChannel(c.getGreen() + amount),
                clampChannel(c.getBlue() + amount), c.getAlpha());
    }
}

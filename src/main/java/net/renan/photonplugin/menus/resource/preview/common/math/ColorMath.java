package net.renan.photonplugin.menus.resource.preview.common.math;

public final class ColorMath {
    private ColorMath() {
    }

    public static int alpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int pack(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int blendArgb(int argbA, int argbB, float f) {
        int a = Math.round(alpha(argbA) + (alpha(argbB) - alpha(argbA)) * f);
        int r = Math.round(red(argbA) + (red(argbB) - red(argbA)) * f);
        int g = Math.round(green(argbA) + (green(argbB) - green(argbA)) * f);
        int b = Math.round(blue(argbA) + (blue(argbB) - blue(argbA)) * f);
        return pack(a, r, g, b);
    }

    public static int multiplyArgb(int argbA, int argbB) {
        int a = alpha(argbA) * alpha(argbB) / 255;
        int r = red(argbA) * red(argbB) / 255;
        int g = green(argbA) * green(argbB) / 255;
        int b = blue(argbA) * blue(argbB) / 255;
        return pack(a, r, g, b);
    }
}

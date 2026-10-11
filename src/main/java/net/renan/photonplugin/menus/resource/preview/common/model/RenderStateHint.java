package net.renan.photonplugin.menus.resource.preview.common.model;

public record RenderStateHint(BlendMode blend, boolean cull, boolean depthTest) {
    public static BlendMode blendOf(String presetName, float additive) {
        return switch (presetName == null ? "ALPHA" : presetName) {
            case "ADDITIVE" -> additiveBlend();
            case "PREMULTIPLIED" -> additive >= 0.5f ? additiveBlend() : BlendMode.standardAlpha();
            case "MULTIPLY" -> new BlendMode(true, BlendMode.Factor.DST_COLOR, BlendMode.Factor.ZERO,
                    BlendMode.Factor.ONE, BlendMode.Factor.ZERO, BlendMode.Equation.ADD);
            case "OPAQUE" -> new BlendMode(false, BlendMode.Factor.ONE, BlendMode.Factor.ZERO, BlendMode.Factor.ONE,
                    BlendMode.Factor.ZERO, BlendMode.Equation.ADD);
            default -> BlendMode.standardAlpha();
        };
    }

    private static BlendMode additiveBlend() {
        return new BlendMode(true, BlendMode.Factor.SRC_ALPHA, BlendMode.Factor.ONE, BlendMode.Factor.ONE,
                BlendMode.Factor.ZERO, BlendMode.Equation.ADD);
    }
}

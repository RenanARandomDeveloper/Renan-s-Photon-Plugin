package net.renan.photonplugin.menus.resource.preview.common.model;

public record BlendMode(boolean enableBlend, Factor srcColorFactor, Factor dstColorFactor, Factor srcAlphaFactor,
                        Factor dstAlphaFactor, Equation blendFunc) {
    public static BlendMode standardAlpha() {
        return new BlendMode(true, Factor.SRC_ALPHA, Factor.ONE_MINUS_SRC_ALPHA, Factor.ONE, Factor.ZERO, Equation.ADD);
    }

    public boolean isStandardAlpha() {
        return enableBlend && blendFunc == Equation.ADD && srcColorFactor == Factor.SRC_ALPHA
                && dstColorFactor == Factor.ONE_MINUS_SRC_ALPHA;
    }

    public boolean isAdditive() {
        return enableBlend && blendFunc == Equation.ADD && dstColorFactor == Factor.ONE
                && (srcColorFactor == Factor.SRC_ALPHA || srcColorFactor == Factor.ONE);
    }

    public enum Factor {
        ZERO, ONE, SRC_COLOR, ONE_MINUS_SRC_COLOR, DST_COLOR, ONE_MINUS_DST_COLOR, SRC_ALPHA, ONE_MINUS_SRC_ALPHA,
        DST_ALPHA, ONE_MINUS_DST_ALPHA, CONSTANT_COLOR, ONE_MINUS_CONSTANT_COLOR, CONSTANT_ALPHA,
        ONE_MINUS_CONSTANT_ALPHA, SRC_ALPHA_SATURATE
    }

    public enum Equation {ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX}
}

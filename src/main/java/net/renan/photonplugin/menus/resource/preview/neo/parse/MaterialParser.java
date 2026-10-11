package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.model.*;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.neo.model.MaterialLayer;

import java.util.ArrayList;
import java.util.List;

public final class MaterialParser {
    public static final String CIRCLE_TEXTURE = "photon:textures/particle/circle.png";
    private static final String[] KILA_OPTIONAL_MODULES = {"layer", "layer2", "mask1", "mask2", "dissolve",
            "distortion", "uvEffects", "projection", "screenDistortion", "ramp", "fresnel", "shading", "matcap",
            "vertexOffset", "depth", "volume", "cameraFade", "colorAdjust", "pixelate"};

    private MaterialParser() {
    }

    public static List<MaterialLayer> parseMaterials(NbtTag.CompoundTag rendererTag) {
        NbtTag.ListTag payload = ParseUtil.resolveList(rendererTag, "materials");
        List<MaterialLayer> layers = new ArrayList<>();
        for (int i = 0; i < payload.size(); i++) {
            if (payload.get(i) instanceof NbtTag.CompoundTag entry) {
                layers.add(parseLayer(entry));
            }
        }
        return layers;
    }

    public static MaterialLayer parseLayer(NbtTag.CompoundTag entry) {
        IMaterial material = parseMaterial(entry.getCompound("material"));
        BlendMode blendMode = parseBlendMode(entry.getCompound("blendMode"));
        boolean depthTest = entry.getBoolean("depthTest", true);
        boolean cull = entry.getBoolean("cull", true);
        if (!"CUSTOM".equals(entry.getString("renderStateSource", "MATERIAL"))) {
            RenderStateHint followed = preferredRenderState(material);
            if (followed != null) {
                blendMode = followed.blend();
                cull = followed.cull();
                depthTest = followed.depthTest();
            }
        }
        return new MaterialLayer(material, blendMode, depthTest, cull);
    }

    public static RenderStateHint preferredRenderState(IMaterial material) {
        return switch (material) {
            case KilaMaterial k -> k.renderState();
            case UnsupportedMaterial u -> u.renderState();
            case null, default -> null;
        };
    }

    public static IMaterial parseMaterial(NbtTag.CompoundTag wrapper) {
        String type = wrapper.getString("type", "texture");
        NbtTag.CompoundTag data = wrapper.getCompound("data");
        if (type.equals("ui_resource_material")) {
            return new LibraryMaterialRef(data.getString("resourcePath", ""));
        }
        if (type.equals("kila_material")) {
            return parseKila(data);
        }
        if (!type.equals("texture")) {
            String texture = findFirstTextureReference(data);
            if (texture == null && type.equals("custom_shader")
                    && "photon:circle".equals(data.getString("shaderLocation", "photon:circle"))) {
                texture = CIRCLE_TEXTURE;
            }
            String graphPath = type.equals("shader_graph") ? data.getString("graphPath", "") : null;
            RenderStateHint renderState = null;
            if (type.equals("shader_graph") && data.getCompound("render_state") != NbtTag.CompoundTag.EMPTY) {
                NbtTag.CompoundTag rs = data.getCompound("render_state");
                renderState = new RenderStateHint(RenderStateHint.blendOf(rs.getString("blend", "ALPHA"), 0f),
                        !rs.getBoolean("double_sided", false), rs.getBoolean("depth_test", true));
            }
            return new UnsupportedMaterial(type, texture, graphPath, renderState);
        }
        String texture = data.getString("texture", "");

        float[] hdrPremultiplied = NumberFunctionParser.parseHdrColor(data.get("hdr"));
        float[] hdrColor = {hdrPremultiplied[0], hdrPremultiplied[1], hdrPremultiplied[2]};
        if (!data.has("hdr")) {
            hdrColor = new float[]{0f, 0f, 0f};
        }
        boolean hdrAdditive = !"MULTIPLICATIVE".equals(data.getString("hdrMode", "ADDITIVE"));
        float discardThreshold = data.getFloat("discardThreshold", 0.1f);
        NbtTag.CompoundTag pixelArt = data.getCompound("pixelArt");
        int pixelArtBits = pixelArt.getBoolean("_enable", false) ? Math.max(1, pixelArt.getInt("bits", 8)) : 0;
        NbtTag.CompoundTag lit = data.getCompound("litParticles");
        float litParticles = lit.getBoolean("_enable", false) ? Math.max(0f, lit.getFloat("intensity", 1f)) : 0f;
        return new TextureMaterial(texture, hdrColor, hdrAdditive, discardThreshold, pixelArtBits, litParticles);
    }

    static KilaMaterial parseKila(NbtTag.CompoundTag d) {
        NbtTag.CompoundTag main = d.getCompound("main");
        boolean mainOn = main.getBoolean("_enable", true);
        NbtTag.CompoundTag mainTexture = main.getCompound("texture");
        String texture = !mainOn ? null : mainTexture.getString("texture", "photon:textures/kila/soft_circle.png");
        if (texture != null && texture.isBlank()) {
            texture = null;
        }
        int colorMode = enumOrdinal(mainTexture.getString("colorMode", "RGBA"),
                "RGBA", "RED_AS_ALPHA", "ALPHA_ONLY", "LUMINANCE_AS_ALPHA");
        float[] tint = d.has("color") ? NumberFunctionParser.parseHdrColor(d.get("color")) : new float[]{1, 1, 1, 1};
        float[] glow = d.has("emission") ? NumberFunctionParser.parseHdrColor(d.get("emission")) : new float[]{0, 0, 0, 1};
        List<String> unsupported = new ArrayList<>();
        float alpha = driver(d.getCompound("alpha"), 1f, "alpha", unsupported);
        float intensity = driver(d.getCompound("intensity"), 1f, "intensity", unsupported);
        int flipColumns = 0;
        int flipRows = 0;
        int flipStart = 0;
        if (mainOn && main.getBoolean("flipbook", false)) {
            flipColumns = Math.max(1, main.getInt("columns", 4));
            flipRows = Math.max(1, main.getInt("rows", 4));
            flipStart = Math.max(0, (int) main.getFloat("startFrame", 0f));
        }
        for (String module : KILA_OPTIONAL_MODULES) {
            if (d.getCompound(module).getBoolean("_enable", false)) {
                unsupported.add(module);
            }
        }
        String vertexColor = d.getString("vertexColor", "MULTIPLY");
        if (!"MULTIPLY".equals(vertexColor)) {
            unsupported.add("vertexColor=" + vertexColor);
        }
        NbtTag.CompoundTag lit = d.getCompound("lit");
        float litIntensity = lit.getBoolean("_enable", false) ? Math.max(0f, lit.getFloat("intensity", 1f)) : 0f;
        return new KilaMaterial(texture, colorMode, tint, glow, intensity, alpha,
                Math.max(1e-3f, d.getFloat("alphaSharpen", 1f)), d.getFloat("alphaClip", 0.01f),
                d.getString("blend", "ALPHA"), d.getFloat("additive", 0f), d.getBoolean("slotRenderState", false),
                d.getBoolean("doubleSided", false), d.getBoolean("depthTest", true),
                d.getCompound("projection").getBoolean("_enable", false), litIntensity, flipColumns, flipRows, flipStart,
                List.copyOf(unsupported));
    }

    private static int enumOrdinal(String name, String... names) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private static float driver(NbtTag.CompoundTag c, float fallback, String name, List<String> notes) {
        if (c == NbtTag.CompoundTag.EMPTY) {
            return fallback;
        }
        float value = c.getFloat("value", fallback);
        String source = c.getString("source", "CONSTANT");
        if ("CONSTANT".equals(source) || "CUSTOM".equals(source)) {
            return value;
        }
        notes.add(name + " driven by " + source.toLowerCase(java.util.Locale.ROOT));
        float sample = source.startsWith("VERTEX") ? 1f : 0.5f;
        return value + c.getFloat("scale", 1f) * sample;
    }

    public static String findFirstTextureReference(NbtTag tag) {
        return switch (tag) {
            case NbtTag.StringTag s -> looksLikeTextureReference(s.value()) ? s.value() : null;
            case NbtTag.CompoundTag c -> {
                for (NbtTag child : c.entries().values()) {
                    String found = findFirstTextureReference(child);
                    if (found != null) {
                        yield found;
                    }
                }
                yield null;
            }
            case NbtTag.ListTag l -> {
                for (int i = 0; i < l.size(); i++) {
                    String found = findFirstTextureReference(l.get(i));
                    if (found != null) {
                        yield found;
                    }
                }
                yield null;
            }
            case null, default -> null;
        };
    }

    private static boolean looksLikeTextureReference(String s) {
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) {
            return false;
        }
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static float asFloat(NbtTag tag, float fallback) {
        return switch (tag) {
            case NbtTag.FloatTag f -> f.value();
            case NbtTag.DoubleTag d -> (float) d.value();
            case NbtTag.IntTag i -> (float) i.value();
            case null, default -> fallback;
        };
    }

    public static BlendMode parseBlendMode(NbtTag.CompoundTag tag) {
        if (tag == NbtTag.CompoundTag.EMPTY) {
            return BlendMode.standardAlpha();
        }
        boolean enable = tag.getBoolean("enableBlend", true);
        BlendMode.Factor srcColor = parseFactor(tag.getString("srcColorFactor", "SRC_ALPHA"), BlendMode.Factor.SRC_ALPHA);
        BlendMode.Factor dstColor = parseFactor(tag.getString("dstColorFactor", "ONE_MINUS_SRC_ALPHA"), BlendMode.Factor.ONE_MINUS_SRC_ALPHA);
        BlendMode.Factor srcAlpha = parseFactor(tag.getString("srcAlphaFactor", "ONE"), BlendMode.Factor.ONE);
        BlendMode.Factor dstAlpha = parseFactor(tag.getString("dstAlphaFactor", "ZERO"), BlendMode.Factor.ZERO);
        BlendMode.Equation eq = parseEquation(tag.getString("blendFunc", "ADD"));
        return new BlendMode(enable, srcColor, dstColor, srcAlpha, dstAlpha, eq);
    }

    private static BlendMode.Factor parseFactor(String s, BlendMode.Factor fallback) {
        try {
            return BlendMode.Factor.valueOf(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static BlendMode.Equation parseEquation(String s) {
        return switch (s) {
            case "SUB", "SUBTRACT" -> BlendMode.Equation.SUBTRACT;
            case "REVERSE_SUB", "REVERSE_SUBTRACT" -> BlendMode.Equation.REVERSE_SUBTRACT;
            case "MIN" -> BlendMode.Equation.MIN;
            case "MAX" -> BlendMode.Equation.MAX;
            default -> BlendMode.Equation.ADD;
        };
    }
}

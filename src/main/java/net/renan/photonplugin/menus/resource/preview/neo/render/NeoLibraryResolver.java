package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.TextureMaterial;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtReader;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.render.AssetResolver;
import net.renan.photonplugin.menus.resource.preview.common.render.LibraryResolver;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewDiagnostics;
import net.renan.photonplugin.menus.resource.preview.neo.parse.MaterialParser;

import java.util.Map;

public final class NeoLibraryResolver implements LibraryResolver {
    private static final Map<String, String> BUILTIN_MATERIAL_TEXTURES = Map.of(
            "circle", "photon:textures/particle/circle.png",
            "kila_tail", "photon:textures/particle/kila_tail.png",
            "laser", "photon:textures/particle/laser.png",
            "smoke", "photon:textures/particle/smoke.png",
            "thaumcraft", "photon:textures/particle/thaumcraft.png",
            "ring", "photon:textures/particle/ring.png");
    private final AssetResolver resolver;
    private final PreviewDiagnostics diagnostics;

    public NeoLibraryResolver(AssetResolver resolver, PreviewDiagnostics diagnostics) {
        this.resolver = resolver;
        this.diagnostics = diagnostics;
    }

    public String graphTexture(String graphPathString) {
        AssetResolver.LibraryPath path = resolver.parseLibraryPath(graphPathString);
        if (path == null) {
            diagnostics.warn("FX preview: shader graph reference \"" + graphPathString
                    + "\" isn't a recognizable resource path — drawing the material flat");
            return null;
        }
        if (path.typeName().equals("built-in")) {
            if (path.value().equals("default_particle")) {
                return MaterialParser.CIRCLE_TEXTURE;
            }
            diagnostics.warn("FX preview: built-in shader graph \"" + path.value()
                    + "\" is unknown to this preview — drawing the material flat");
            return null;
        }
        if (!path.typeName().equals("file")) {
            diagnostics.warn("FX preview: shader graph reference of unsupported kind \"" + path.typeName()
                    + "\": \"" + graphPathString + "\"");
            return null;
        }
        AssetResolver.Resolved resolved = resolver.resolveLibraryFile(path.value());
        if (resolved == null) {
            diagnostics.warn("FX preview: shader graph file not found: \"" + path.value()
                    + "\" — checked: " + resolver.describeLibraryLookup(path.value()));
            return null;
        }
        try {
            String texture = MaterialParser.findFirstTextureReference(NbtReader.readCompressed(resolved.bytes()));
            if (texture == null) {
                diagnostics.warn("FX preview: shader graph \"" + path.value()
                        + "\" references no texture — the graph's own logic is not interpreted, drawing it flat");
            } else {
                diagnostics.warn("FX preview: shader graph \"" + path.value() + "\" is not interpreted; showing its first "
                        + "texture (" + texture + ") as a stand-in");
            }
            return texture;
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to read shader graph \"" + path.value() + "\": " + e.getMessage());
            return null;
        }
    }

    public IMaterial material(String resourcePathString) {
        AssetResolver.LibraryPath path = resolver.parseLibraryPath(resourcePathString);
        if (path == null) {
            diagnostics.warn("FX preview: material reference \"" + resourcePathString + "\" isn't a recognizable "
                    + "resource path (expected \"built-in(name)\" or \"file(path)\")");
            return null;
        }
        if (path.typeName().equals("built-in")) {
            String texture = BUILTIN_MATERIAL_TEXTURES.get(path.value());
            if (texture == null) {
                diagnostics.warn("FX preview: builtin material \"" + path.value() + "\" has no CPU-renderable "
                        + "equivalent in this preview — falling back to a flat color swatch");
                return null;
            }
            return new TextureMaterial(texture, new float[]{0, 0, 0}, true, 0.1f, false);
        }
        if (!path.typeName().equals("file")) {
            diagnostics.warn("FX preview: material reference of unsupported kind \"" + path.typeName()
                    + "\": \"" + resourcePathString + "\"");
            return null;
        }
        AssetResolver.Resolved resolved = resolver.resolveLibraryFile(path.value());
        if (resolved == null) {
            diagnostics.warn("FX preview: referenced material resource not found: \"" + path.value()
                    + "\" — checked: " + resolver.describeLibraryLookup(path.value()));
            return null;
        }
        return parseLibraryMaterial(resolved.bytes(), resourcePathString);
    }

    private IMaterial parseLibraryMaterial(byte[] bytes, String sourceDescription) {
        try {
            NbtTag.CompoundTag root = NbtReader.readCompressed(bytes);
            if (!"material".equals(root.getString("type", ""))) {
                diagnostics.warn("FX preview: \"" + sourceDescription + "\" doesn't look like a material resource file");
                return null;
            }
            return MaterialParser.parseMaterial(root.getCompound("data"));
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to read material resource \"" + sourceDescription + "\": "
                    + e.getMessage());
            return null;
        }
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtReader;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.render.AssetResolver;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewDiagnostics;
import net.renan.photonplugin.menus.resource.preview.neo.model.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.SkinnedVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinnedModel;
import net.renan.photonplugin.menus.resource.preview.neo.parse.FxNbtParser;
import net.renan.photonplugin.menus.resource.preview.neo.parse.GltfMeshParser;
import net.renan.photonplugin.menus.resource.preview.neo.parse.JsonModelMeshParser;
import net.renan.photonplugin.menus.resource.preview.neo.parse.ObjMeshParser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MeshCache {
    private static final Map<String, String> BUILTIN_MESH_MODELS = Map.of(
            "cube", "photon:models/cube.obj",
            "sphere", "photon:models/sphere.obj",
            "plane", "photon:models/plane.obj",
            "quad", "photon:models/quad.obj",
            "cylinder", "photon:models/cylinder.obj",
            "capsule", "photon:models/capsule.obj");

    private static final int MAX_REFERENCE_DEPTH = 8;

    private final AssetResolver resolver;
    private final Map<String, ObjMesh> cache = new HashMap<>();
    private final PreviewDiagnostics diagnostics;
    private final SkinnedModelCache skinnedModelCache;

    public MeshCache(List<File> assetRoots, PreviewDiagnostics diagnostics) {
        this(new AssetResolver(assetRoots, diagnostics), diagnostics);
    }

    public MeshCache(AssetResolver resolver, PreviewDiagnostics diagnostics) {
        this.resolver = resolver;
        this.diagnostics = diagnostics;
        this.skinnedModelCache = new SkinnedModelCache(resolver, diagnostics);
    }

    private static ObjMesh bindPoseOf(SkinnedModel model) {
        List<MeshTriangle> triangles = new java.util.ArrayList<>();
        for (var t : model.triangles()) {
            triangles.add(new MeshTriangle(toPlain(t.a()), toPlain(t.b()), toPlain(t.c())));
        }
        return new ObjMesh(triangles);
    }

    private static MeshVertex toPlain(SkinnedVertex v) {
        return new MeshVertex(v.position(), v.u(), v.v(), v.normal());
    }

    public SkinnedModelCache skinnedModels() {
        return skinnedModelCache;
    }

    public ObjMesh get(ModelSource source) {
        return switch (source) {
            case ObjModelSourceRef ref -> getObj(ref.modelLocation(), ref.flipV());
            case JsonModelSourceRef ref -> getJson(ref.modelLocation());
            case GltfModelSourceRef ref -> getGltf(ref.modelLocation(), ref.flipV());
            case LibraryMeshRef ref -> getLibrary(ref.resourcePath(), 0);
            case AnimatedGltfModelSourceRef ref -> bindPoseOf(skinnedModelCache.get(ref));
            default -> ObjMesh.EMPTY;
        };
    }

    private ObjMesh getObj(String reference, boolean flipV) {
        if (reference == null || reference.isBlank()) {
            return ObjMesh.EMPTY;
        }
        return getOrCompute("obj|" + flipV + "|" + reference, () -> loadObj(reference, flipV));
    }

    private ObjMesh getJson(String reference) {
        if (reference == null || reference.isBlank()) {
            return ObjMesh.EMPTY;
        }
        return getOrCompute("json|" + reference, () -> loadJson(reference));
    }

    private ObjMesh getGltf(String reference, boolean flipV) {
        if (reference == null || reference.isBlank()) {
            return ObjMesh.EMPTY;
        }
        return getOrCompute("gltf|" + flipV + "|" + reference, () -> loadGltf(reference, flipV));
    }

    private ObjMesh getLibrary(String resourcePathString, int depth) {
        if (resourcePathString == null || resourcePathString.isBlank()) {
            return ObjMesh.EMPTY;
        }
        return getOrCompute("lib|" + resourcePathString, () -> loadLibrary(resourcePathString, depth));
    }

    private ObjMesh getOrCompute(String key, java.util.function.Supplier<ObjMesh> loader) {
        ObjMesh cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ObjMesh computed = loader.get();
        cache.put(key, computed);
        return computed;
    }

    private ObjMesh loadObj(String reference, boolean flipV) {
        AssetResolver.Resolved resolved = resolver.resolve(reference);
        if (resolved == null) {
            diagnostics.warn("FX preview: model not found: \"" + reference + "\" "
                    + "(checked " + resolver.describeAssetLookup() + ")");
            return ObjMesh.EMPTY;
        }
        try {
            ObjMesh mesh = ObjMeshParser.parse(resolved.bytes(), flipV);
            if (mesh.isEmpty()) {
                diagnostics.warn("FX preview: \"" + reference + "\" parsed to zero triangles");
            }
            return mesh;
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to parse OBJ model \"" + reference + "\": " + e.getMessage());
            return ObjMesh.EMPTY;
        }
    }

    private AssetResolver.Resolved resolveJsonModel(String reference) {
        int colon = reference.indexOf(':');
        String namespace = colon >= 0 ? reference.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? reference.substring(colon + 1) : reference;
        if (!path.startsWith("models/") && !path.endsWith(".json")) {
            AssetResolver.Resolved byModelId = resolver.resolve(namespace + ":models/" + path + ".json");
            if (byModelId != null) {
                return byModelId;
            }
        }
        return resolver.resolve(reference);
    }

    private ObjMesh loadJson(String reference) {
        AssetResolver.Resolved resolved = resolveJsonModel(reference);
        if (resolved == null) {
            diagnostics.warn("FX preview: model not found: \"" + reference + "\" "
                    + "(checked " + resolver.describeAssetLookup() + ")");
            return ObjMesh.EMPTY;
        }
        try {
            ObjMesh mesh = JsonModelMeshParser.parse(resolved.bytes(), parent -> {
                AssetResolver.Resolved resolvedParent = resolveJsonModel(parent);
                return resolvedParent == null ? null : resolvedParent.bytes();
            });
            if (mesh.isEmpty()) {
                diagnostics.warn("FX preview: \"" + reference + "\" has no self-contained \"elements\" this preview "
                        + "can use (likely a \"parent\"-only model inheriting a vanilla block shape, which needs "
                        + "Minecraft's own model bakery — see JsonModelMeshParser)");
            }
            return mesh;
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to parse JSON model \"" + reference + "\": " + e.getMessage());
            return ObjMesh.EMPTY;
        }
    }

    private ObjMesh loadGltf(String reference, boolean flipV) {
        AssetResolver.Resolved resolved = resolver.resolve(reference);
        if (resolved == null) {
            diagnostics.warn("FX preview: model not found: \"" + reference + "\" "
                    + "(checked " + resolver.describeAssetLookup() + ")");
            return ObjMesh.EMPTY;
        }
        try {
            ObjMesh mesh = GltfMeshParser.parse(resolved.bytes(), flipV);
            if (mesh.isEmpty()) {
                diagnostics.warn("FX preview: \"" + reference + "\" parsed to zero triangles "
                        + "(no TRIANGLES-mode primitive with a POSITION attribute found)");
            }
            return mesh;
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to parse glTF model \"" + reference + "\": " + e.getMessage());
            return ObjMesh.EMPTY;
        }
    }

    private ObjMesh loadLibrary(String resourcePathString, int depth) {
        if (depth > MAX_REFERENCE_DEPTH) {
            diagnostics.warn("FX preview: mesh reference chain is too deep (possible cycle) — giving up "
                    + "(\"" + resourcePathString + "\")");
            return ObjMesh.EMPTY;
        }
        AssetResolver.LibraryPath path = resolver.parseLibraryPath(resourcePathString);
        if (path == null) {
            diagnostics.warn("FX preview: mesh reference \"" + resourcePathString + "\" isn't a recognizable "
                    + "resource path (expected \"built-in(name)\" or \"file(path)\")");
            return ObjMesh.EMPTY;
        }
        if (path.typeName().equals("built-in")) {
            String modelLocation = BUILTIN_MESH_MODELS.get(path.value());

            return modelLocation == null ? ObjMesh.EMPTY : getObj(modelLocation, true);
        }
        if (!path.typeName().equals("file")) {
            diagnostics.warn("FX preview: mesh reference of unsupported kind \"" + path.typeName()
                    + "\": \"" + resourcePathString + "\"");
            return ObjMesh.EMPTY;
        }
        AssetResolver.Resolved resolved = resolver.resolveLibraryFile(path.value());
        if (resolved == null) {
            diagnostics.warn("FX preview: referenced mesh resource not found: \"" + path.value()
                    + "\" — checked: " + resolver.describeLibraryLookup(path.value()));
            return ObjMesh.EMPTY;
        }
        return parseLibraryMesh(resolved.bytes(), resourcePathString, depth);
    }

    private ObjMesh parseLibraryMesh(byte[] bytes, String sourceDescription, int depth) {
        try {
            NbtTag.CompoundTag root = NbtReader.readCompressed(bytes);
            if (!"mesh".equals(root.getString("type", ""))) {
                diagnostics.warn("FX preview: \"" + sourceDescription + "\" doesn't look like a mesh resource file");
                return ObjMesh.EMPTY;
            }
            ModelSource inner = FxNbtParser.parseModelSource(root.getCompound("data"));
            return switch (inner) {
                case ObjModelSourceRef ref -> getObj(ref.modelLocation(), ref.flipV());
                case JsonModelSourceRef ref -> getJson(ref.modelLocation());
                case GltfModelSourceRef ref -> getGltf(ref.modelLocation(), ref.flipV());
                case LibraryMeshRef ref -> getLibrary(ref.resourcePath(), depth + 1);
                default -> ObjMesh.EMPTY;
            };
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to read mesh resource \"" + sourceDescription + "\": "
                    + e.getMessage());
            return ObjMesh.EMPTY;
        }
    }
}

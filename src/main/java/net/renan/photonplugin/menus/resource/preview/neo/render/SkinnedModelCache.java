package net.renan.photonplugin.menus.resource.preview.neo.render;

import net.renan.photonplugin.menus.resource.preview.common.render.AssetResolver;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewDiagnostics;
import net.renan.photonplugin.menus.resource.preview.neo.model.AnimatedGltfModelSourceRef;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.AnimationClip;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.ClipRetarget;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinnedModel;
import net.renan.photonplugin.menus.resource.preview.neo.parse.GltfMeshParser;

import java.util.*;

public final class SkinnedModelCache {
    private final AssetResolver resolver;
    private final PreviewDiagnostics diagnostics;
    private final Map<String, SkinnedModel> cache = new HashMap<>();

    private final Map<Object, Map<AnimationClip, Map<Integer, BakedAnimation>>> baked = new IdentityHashMap<>();

    public SkinnedModelCache(AssetResolver resolver, PreviewDiagnostics diagnostics) {
        this.resolver = resolver;
        this.diagnostics = diagnostics;
    }

    BakedAnimation baked(SkinnedModel model, AnimationClip clip, int frames) {
        return baked.computeIfAbsent(model, m -> new IdentityHashMap<>())
                .computeIfAbsent(clip, c -> new LinkedHashMap<>())
                .computeIfAbsent(frames, f -> new BakedAnimation(model, clip, f));
    }

    public SkinnedModel get(AnimatedGltfModelSourceRef ref) {
        if (ref.modelLocation() == null || ref.modelLocation().isBlank()) {
            return SkinnedModel.EMPTY;
        }
        String key = "animated_gltf|" + ref.flipV() + "|" + ref.modelLocation() + "|" + String.join(",", ref.animationFiles());
        SkinnedModel cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SkinnedModel computed = load(ref);
        cache.put(key, computed);
        return computed;
    }

    private SkinnedModel load(AnimatedGltfModelSourceRef ref) {
        AssetResolver.Resolved resolved = resolver.resolve(ref.modelLocation());
        if (resolved == null) {
            diagnostics.warn("FX preview: animated model not found: \"" + ref.modelLocation() + "\" "
                    + "(checked " + resolver.describeAssetLookup() + ")");
            return SkinnedModel.EMPTY;
        }
        SkinnedModel base;
        try {
            base = GltfMeshParser.parseAnimated(resolved.bytes(), ref.flipV());
        } catch (Exception e) {
            diagnostics.warn("FX preview: failed to parse animated glTF \"" + ref.modelLocation() + "\": " + e.getMessage());
            return SkinnedModel.EMPTY;
        }
        if (!base.isAnimated()) {
            diagnostics.warn("FX preview: \"" + ref.modelLocation() + "\" has no skin/animations this preview "
                    + "could read — it will render as its static (bind pose) mesh");
        }
        if (ref.animationFiles().isEmpty() || base.skeleton() == null) {
            return base;
        }

        List<AnimationClip> retargeted = new ArrayList<>(base.clips());
        for (String fileRef : ref.animationFiles()) {
            AssetResolver.Resolved clipResolved = resolver.resolve(fileRef);
            if (clipResolved == null) {
                diagnostics.warn("FX preview: animation file not found: \"" + fileRef + "\"");
                continue;
            }
            try {
                SkinnedModel clipSource = GltfMeshParser.parseAnimated(clipResolved.bytes(), ref.flipV());
                if (clipSource.skeleton() == null) {
                    continue;
                }
                ClipRetarget.Result result = ClipRetarget.onto(base.skeleton(), clipSource.skeleton(),
                        clipSource.clips(), fileRef);
                retargeted.addAll(result.clips());
            } catch (Exception e) {
                diagnostics.warn("FX preview: failed to parse animation file \"" + fileRef + "\": " + e.getMessage());
            }
        }
        return new SkinnedModel(base.triangles(), base.skeleton(), retargeted);
    }
}

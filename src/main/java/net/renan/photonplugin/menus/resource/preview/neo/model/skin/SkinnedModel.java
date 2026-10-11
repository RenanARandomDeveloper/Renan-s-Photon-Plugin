package net.renan.photonplugin.menus.resource.preview.neo.model.skin;

import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.SkinnedTriangle;

import java.util.List;

public record SkinnedModel(List<SkinnedTriangle> triangles, Skeleton skeleton, List<AnimationClip> clips) {
    public static final SkinnedModel EMPTY = new SkinnedModel(List.of(), null, List.of());

    public boolean isAnimated() {
        return skeleton != null && skeleton.jointCount() > 0 && !clips.isEmpty();
    }

    public AnimationClip clip(String name) {
        for (AnimationClip clip : clips) {
            if (clip.name().equals(name)) {
                return clip;
            }
        }
        return null;
    }

    public AnimationClip clipAt(int index) {
        if (clips.isEmpty()) {
            return null;
        }
        return clips.get(Math.max(0, Math.min(index, clips.size() - 1)));
    }

    public List<String> clipNames() {
        return clips.stream().map(AnimationClip::name).toList();
    }
}

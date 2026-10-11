package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Timeline;

import java.util.List;

public record FxScene(int version, List<FxObject> objects, Timeline timeline) {
    public FxScene(int version, List<FxObject> objects) {
        this(version, objects, new Timeline());
    }
}

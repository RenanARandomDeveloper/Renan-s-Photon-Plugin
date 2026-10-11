package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.neo.model.ForceFieldConfig;
import net.renan.photonplugin.menus.resource.preview.neo.model.Transform;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ForceFieldSim {
    private final String objectId;
    private final ForceFieldConfig authored;
    private final Transform transform;
    private final SceneNode node;
    private final Map<String, Object> overrides = new HashMap<>();
    private ForceFieldConfig config;

    public ForceFieldSim(String objectId, ForceFieldConfig config, Transform transform, SceneNode node) {
        this.objectId = objectId;
        this.authored = config;
        this.config = config;
        this.transform = transform;
        this.node = node;
    }

    public String objectId() {
        return objectId;
    }

    public ForceFieldConfig config() {
        return config;
    }

    public Transform transform() {
        return transform;
    }

    public SceneNode node() {
        return node;
    }

    public void setOverride(String path, Object value) {
        if (!Objects.equals(overrides.put(path, value), value)) {
            config = ConfigOverrides.apply(authored, overrides);
        }
    }

    public boolean isActive() {
        return node == null || node.isActive();
    }

    public boolean isVisible() {
        return node == null || node.isVisible();
    }
}

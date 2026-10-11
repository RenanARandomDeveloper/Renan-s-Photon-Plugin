package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

import java.util.List;

public record ExternalForces(boolean enable, NumberFunction multiplier, Filter filter, List<String> influenceListIds) {
    public boolean isInfluencedBy(String forceFieldObjectId) {
        return filter == Filter.SCENE_ALL || influenceListIds.contains(forceFieldObjectId);
    }

    public float getMultiplier(double t, MemRandom mem) {
        return (float) multiplier.get(t, mem.of("external-forces"));
    }

    public enum Filter {SCENE_ALL, LIST}
}

package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.FxData;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.FxObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class FRuntime {
    final ForgeSimulation sim;
    final FEmpty root;

    final int depth;
    private final Map<String, FObject> objects = new LinkedHashMap<>();

    FRuntime(ForgeSimulation sim, FxData data, int depth) {
        this.sim = sim;
        this.depth = depth;
        this.root = new FEmpty(sim, this);
        root.name = "root";
        objects.put("root#" + System.identityHashCode(root), root);

        Map<FObject, FxObject> definitions = new LinkedHashMap<>();
        int index = 0;
        for (FxObject def : data.objects) {
            FObject object = create(def);
            if (object == null) {
                continue;
            }
            object.name = def.name;
            object.transform.setLocal(def.transform.localPosition, def.transform.localRotation, def.transform.localScale);
            String id = def.transform.id == null || def.transform.id.isEmpty() ? "object#" + index : def.transform.id;

            objects.put(id, object);
            definitions.put(object, def);
            index++;
        }

        for (Map.Entry<String, FObject> entry : objects.entrySet()) {
            FObject object = entry.getValue();
            if (object == root) {
                continue;
            }
            FxObject def = definitions.get(object);
            FObject parent = root;
            if (def != null && def.transform.parentId != null) {
                FObject candidate = objects.get(def.transform.parentId);
                if (candidate != null && candidate != object && !isAncestor(object, candidate)) {
                    parent = candidate;
                }
            }
            object.transform.setParent(parent.transform);
        }
    }

    private static boolean isAncestor(FObject object, FObject candidate) {
        FTransform t = candidate.transform.parent();
        int guard = 0;
        while (t != null && guard++ < 10_000) {
            if (t.owner == object) {
                return true;
            }
            t = t.parent();
        }
        return false;
    }

    private FObject create(FxObject def) {
        return switch (def.kind) {
            case PARTICLE -> def.particle == null ? null : new FParticleEmitter(sim, this, def.particle);
            case BEAM -> def.beam == null ? null : new FBeamEmitter(sim, this, def.beam);
            case TRAIL -> def.trail == null ? null : new FTrailEmitter(sim, this, def.trail);
            case EMPTY -> new FEmpty(sim, this);
        };
    }

    Collection<FObject> objects() {
        return objects.values();
    }

    void emmit() {
        for (FObject object : objects.values()) {
            object.reset();
            sim.add(object);
        }
    }
}

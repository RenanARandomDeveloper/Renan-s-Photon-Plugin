package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class FxDataFixer {
    public static final int CURRENT_VERSION = 5;

    private static final int RENDERER_MATERIALS_VERSION = 2;
    private static final int UV_TILES_VERSION = 3;

    private FxDataFixer() {
    }

    public static NbtTag.CompoundTag fix(NbtTag.CompoundTag fxRoot, int fromVersion) {
        if (fromVersion >= CURRENT_VERSION) {
            return fxRoot;
        }
        NbtTag.CompoundTag fxData = fxRoot.getCompound("fxData");
        NbtTag fxObjects = fxData.get("fxObjects");
        if (fxObjects == null) {
            return fxRoot;
        }
        NbtTag fixedObjects = mapObjects(fxObjects, object -> fixObject(object, fromVersion));
        return with(fxRoot, "fxData", with(fxData, "fxObjects", fixedObjects));
    }

    private static NbtTag mapObjects(NbtTag container, UnaryOperator<NbtTag.CompoundTag> fixer) {
        if (container instanceof NbtTag.ListTag list) {
            return mapList(list, fixer);
        }
        if (container instanceof NbtTag.CompoundTag wrapper && wrapper.get("payload") instanceof NbtTag.ListTag payload) {
            return with(wrapper, "payload", mapList(payload, fixer));
        }
        return container;
    }

    private static NbtTag.ListTag mapList(NbtTag.ListTag list, UnaryOperator<NbtTag.CompoundTag> fixer) {
        List<NbtTag> fixed = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtTag item = list.get(i);
            fixed.add(item instanceof NbtTag.CompoundTag compound ? fixer.apply(compound) : item);
        }
        return new NbtTag.ListTag(fixed);
    }

    private static NbtTag.CompoundTag fixObject(NbtTag.CompoundTag object, int version) {
        if (!(object.get("data") instanceof NbtTag.CompoundTag data) || !(data.get("config") instanceof NbtTag.CompoundTag config)) {
            return object;
        }
        NbtTag.CompoundTag fixed = config;
        if (version < RENDERER_MATERIALS_VERSION) {
            fixed = moveMaterialIntoRenderer(fixed);
        }
        if (version < UV_TILES_VERSION) {
            fixed = fixUvTiles(fixed);
        }
        return with(object, "data", with(data, "config", fixed));
    }

    private static NbtTag.CompoundTag moveMaterialIntoRenderer(NbtTag.CompoundTag config) {
        NbtTag.CompoundTag result = config;
        NbtTag material = result.get("material");
        if (material != null) {
            result = with(without(result, "material"), "renderer",
                    with(result.getCompound("renderer"), "materials", singleMaterial(material)));
        }
        if (result.get("trails") instanceof NbtTag.CompoundTag trails
                && trails.get("config") instanceof NbtTag.CompoundTag trailConfig
                && trailConfig.get("material") != null) {
            NbtTag trailMaterial = trailConfig.get("material");
            NbtTag.CompoundTag fixedTrail = with(without(trailConfig, "material"), "renderer",
                    with(trailConfig.getCompound("renderer"), "materials", singleMaterial(trailMaterial)));
            result = with(result, "trails", with(trails, "config", fixedTrail));
        }
        return result;
    }

    private static NbtTag.CompoundTag fixUvTiles(NbtTag.CompoundTag config) {
        NbtTag.CompoundTag result = config;
        if (result.get("uvAnimation") instanceof NbtTag.CompoundTag uv && uv.get("tiles") instanceof NbtTag.CompoundTag tiles) {
            int[] grid = {tiles.getInt("a", 1), tiles.getInt("b", 1)};
            result = with(result, "uvAnimation", with(uv, "tiles", new NbtTag.IntArrayTag(grid)));
        }
        if (result.get("trails") instanceof NbtTag.CompoundTag trails && trails.get("config") instanceof NbtTag.CompoundTag trailConfig) {
            result = with(result, "trails", with(trails, "config", fixUvTiles(trailConfig)));
        }
        return result;
    }

    private static NbtTag.CompoundTag singleMaterial(NbtTag material) {
        Map<String, NbtTag> wrapper = new LinkedHashMap<>();
        wrapper.put("uid", new NbtTag.IntTag(1));
        wrapper.put("payload", new NbtTag.ListTag(List.of(material)));
        return new NbtTag.CompoundTag(wrapper);
    }

    private static NbtTag.CompoundTag with(NbtTag.CompoundTag source, String key, NbtTag value) {
        Map<String, NbtTag> entries = new LinkedHashMap<>(source.entries());
        entries.put(key, value);
        return new NbtTag.CompoundTag(entries);
    }

    private static NbtTag.CompoundTag without(NbtTag.CompoundTag source, String key) {
        Map<String, NbtTag> entries = new LinkedHashMap<>(source.entries());
        entries.remove(key);
        return new NbtTag.CompoundTag(entries);
    }
}

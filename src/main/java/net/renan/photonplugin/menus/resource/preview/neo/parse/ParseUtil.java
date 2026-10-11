package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;

import java.util.List;

public final class ParseUtil {
    private ParseUtil() {
    }

    public static NbtTag.ListTag resolveList(NbtTag tag) {
        if (tag instanceof NbtTag.ListTag list) {
            return list;
        }
        if (tag instanceof NbtTag.CompoundTag compound && compound.get("payload") instanceof NbtTag.ListTag payload) {
            return payload;
        }
        return new NbtTag.ListTag(List.of());
    }

    public static NbtTag.ListTag resolveList(NbtTag.CompoundTag parent, String key) {
        return resolveList(parent.get(key));
    }
}

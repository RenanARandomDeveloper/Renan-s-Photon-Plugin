package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag.*;

import java.util.ArrayList;
import java.util.List;

final class ForgeNbt {
    private ForgeNbt() {
    }

    static boolean isNumber(NbtTag tag) {
        return tag instanceof ByteTag || tag instanceof ShortTag || tag instanceof IntTag || tag instanceof LongTag
                || tag instanceof FloatTag || tag instanceof DoubleTag;
    }

    static double number(NbtTag tag, double fallback) {
        return switch (tag) {
            case ByteTag t -> t.value();
            case ShortTag t -> t.value();
            case IntTag t -> t.value();
            case LongTag t -> t.value();
            case FloatTag t -> t.value();
            case DoubleTag t -> t.value();
            case null, default -> fallback;
        };
    }

    static boolean isFloating(NbtTag tag) {
        return tag instanceof FloatTag || tag instanceof DoubleTag;
    }

    static CompoundTag compound(CompoundTag parent, String key) {
        return parent.getCompound(key);
    }

    static boolean bool(CompoundTag c, String key, boolean fallback) {
        NbtTag t = c.get(key);
        if (t == null) {
            return fallback;
        }
        if (isNumber(t)) {
            return number(t, fallback ? 1 : 0) != 0;
        }
        return fallback;
    }

    static int integer(CompoundTag c, String key, int fallback) {
        NbtTag t = c.get(key);
        return isNumber(t) ? (int) number(t, fallback) : fallback;
    }

    static float real(CompoundTag c, String key, float fallback) {
        NbtTag t = c.get(key);
        return isNumber(t) ? (float) number(t, fallback) : fallback;
    }

    static String string(CompoundTag c, String key, String fallback) {
        NbtTag t = c.get(key);
        return t instanceof StringTag(String value) ? value : fallback;
    }

    static <E extends Enum<E>> E enumValue(CompoundTag c, String key, Class<E> type, E fallback) {
        NbtTag t = c.get(key);
        if (t instanceof StringTag(String value)) {
            String wanted = value.trim();
            for (E e : type.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(wanted)) {
                    return e;
                }
            }
            return fallback;
        }
        if (isNumber(t)) {
            int ordinal = (int) number(t, -1);
            E[] all = type.getEnumConstants();
            if (ordinal >= 0 && ordinal < all.length) {
                return all[ordinal];
            }
        }
        return fallback;
    }

    static List<Float> floats(NbtTag tag) {
        List<Float> out = new ArrayList<>();
        switch (tag) {
            case ListTag l -> {
                for (NbtTag item : l.items()) {
                    if (isNumber(item)) {
                        out.add((float) number(item, 0));
                    }
                }
            }
            case IntArrayTag a -> {
                for (int v : a.value()) {
                    out.add((float) v);
                }
            }
            case LongArrayTag a -> {
                for (long v : a.value()) {
                    out.add((float) v);
                }
            }
            case ByteArrayTag a -> {
                for (byte v : a.value()) {
                    out.add((float) v);
                }
            }
            case null, default -> {
            }
        }
        return out;
    }

    static float[] vec3(NbtTag tag, float dx, float dy, float dz) {
        if (tag instanceof CompoundTag c) {
            return new float[]{real(c, "x", dx), real(c, "y", dy), real(c, "z", dz)};
        }
        List<Float> values = floats(tag);
        if (values.size() >= 3) {
            return new float[]{values.get(0), values.get(1), values.get(2)};
        }
        return new float[]{dx, dy, dz};
    }

    static String uuidKey(NbtTag tag) {
        if (tag instanceof IntArrayTag(int[] v) && v.length == 4) {
            return v[0] + "," + v[1] + "," + v[2] + "," + v[3];
        }
        if (tag instanceof StringTag(String value1)) {
            return value1;
        }
        if (tag instanceof LongArrayTag(long[] value) && value.length == 2) {
            return value[0] + "," + value[1];
        }
        return null;
    }
}

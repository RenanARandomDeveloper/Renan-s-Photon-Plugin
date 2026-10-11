package net.renan.photonplugin.menus.resource.preview.common.nbt;

import java.util.List;
import java.util.Map;

public sealed interface NbtTag {
    record ByteTag(byte value) implements NbtTag {
    }

    record ShortTag(short value) implements NbtTag {
    }

    record IntTag(int value) implements NbtTag {
    }

    record LongTag(long value) implements NbtTag {
    }

    record FloatTag(float value) implements NbtTag {
    }

    record DoubleTag(double value) implements NbtTag {
    }

    record ByteArrayTag(byte[] value) implements NbtTag {
    }

    record StringTag(String value) implements NbtTag {
    }

    record IntArrayTag(int[] value) implements NbtTag {
    }

    record LongArrayTag(long[] value) implements NbtTag {
    }

    record ListTag(List<NbtTag> items) implements NbtTag {
        public int size() {
            return items.size();
        }

        public NbtTag get(int index) {
            return items.get(index);
        }
    }

    record CompoundTag(Map<String, NbtTag> entries) implements NbtTag {
        public static final CompoundTag EMPTY = new CompoundTag(Map.of());

        public boolean has(String key) {
            return entries.containsKey(key);
        }

        public NbtTag get(String key) {
            return entries.get(key);
        }

        public CompoundTag getCompound(String key) {
            NbtTag tag = entries.get(key);
            return tag instanceof CompoundTag compound ? compound : EMPTY;
        }

        public ListTag getList(String key) {
            NbtTag tag = entries.get(key);
            return tag instanceof ListTag list ? list : new ListTag(List.of());
        }

        public String getString(String key, String fallback) {
            NbtTag tag = entries.get(key);
            return tag instanceof StringTag(String value) ? value : fallback;
        }

        public boolean getBoolean(String key, boolean fallback) {
            NbtTag tag = entries.get(key);
            return tag instanceof ByteTag(byte value) ? value != 0 : fallback;
        }

        public int getInt(String key, int fallback) {
            NbtTag tag = entries.get(key);
            return switch (tag) {
                case IntTag i -> i.value();
                case ByteTag b -> b.value();
                case ShortTag s -> s.value();
                case LongTag l -> (int) l.value();
                case FloatTag f -> (int) f.value();
                case DoubleTag d -> (int) d.value();
                case null, default -> fallback;
            };
        }

        public float getFloat(String key, float fallback) {
            NbtTag tag = entries.get(key);
            return switch (tag) {
                case FloatTag f -> f.value();
                case DoubleTag d -> (float) d.value();
                case IntTag i -> i.value();
                case ByteTag b -> b.value();
                case ShortTag s -> s.value();
                case LongTag l -> l.value();
                case null, default -> fallback;
            };
        }

        public double getDouble(String key, double fallback) {
            NbtTag tag = entries.get(key);
            return switch (tag) {
                case DoubleTag d -> d.value();
                case FloatTag f -> f.value();
                case IntTag i -> i.value();
                case ByteTag b -> b.value();
                case ShortTag s -> s.value();
                case LongTag l -> l.value();
                case null, default -> fallback;
            };
        }
    }
}

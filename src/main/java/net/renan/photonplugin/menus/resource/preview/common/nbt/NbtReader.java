package net.renan.photonplugin.menus.resource.preview.common.nbt;

import net.mcreator.ui.init.L10N;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class NbtReader {
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private NbtReader() {
    }

    public static NbtTag.CompoundTag readCompressed(byte[] fileBytes) throws IOException {
        InputStream raw = new ByteArrayInputStream(fileBytes);
        InputStream source = looksGzipped(fileBytes) ? new GZIPInputStream(raw) : raw;
        try (DataInputStream in = new DataInputStream(source)) {
            int typeId = in.readUnsignedByte();
            if (typeId != TAG_COMPOUND) {
                throw new IOException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.nbt_root", typeId));
            }
            in.readUTF();
            return readCompoundBody(in);
        }
    }

    private static boolean looksGzipped(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1f && (bytes[1] & 0xFF) == 0x8b;
    }

    private static NbtTag readTag(DataInputStream in, int typeId) throws IOException {
        return switch (typeId) {
            case TAG_BYTE -> new NbtTag.ByteTag(in.readByte());
            case TAG_SHORT -> new NbtTag.ShortTag(in.readShort());
            case TAG_INT -> new NbtTag.IntTag(in.readInt());
            case TAG_LONG -> new NbtTag.LongTag(in.readLong());
            case TAG_FLOAT -> new NbtTag.FloatTag(in.readFloat());
            case TAG_DOUBLE -> new NbtTag.DoubleTag(in.readDouble());
            case TAG_BYTE_ARRAY -> {
                int len = in.readInt();
                byte[] data = new byte[Math.max(0, len)];
                in.readFully(data);
                yield new NbtTag.ByteArrayTag(data);
            }
            case TAG_STRING -> new NbtTag.StringTag(in.readUTF());
            case TAG_LIST -> readListBody(in);
            case TAG_COMPOUND -> readCompoundBody(in);
            case TAG_INT_ARRAY -> {
                int len = in.readInt();
                int[] data = new int[Math.max(0, len)];
                for (int i = 0; i < data.length; i++) {
                    data[i] = in.readInt();
                }
                yield new NbtTag.IntArrayTag(data);
            }
            case TAG_LONG_ARRAY -> {
                int len = in.readInt();
                long[] data = new long[Math.max(0, len)];
                for (int i = 0; i < data.length; i++) {
                    data[i] = in.readLong();
                }
                yield new NbtTag.LongArrayTag(data);
            }
            default -> throw new IOException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.nbt_tag", typeId));
        };
    }

    private static NbtTag.CompoundTag readCompoundBody(DataInputStream in) throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        while (true) {
            int typeId = in.readUnsignedByte();
            if (typeId == TAG_END) {
                break;
            }
            String name = in.readUTF();
            entries.put(name, readTag(in, typeId));
        }
        return new NbtTag.CompoundTag(entries);
    }

    private static NbtTag.ListTag readListBody(DataInputStream in) throws IOException {
        int elementTypeId = in.readUnsignedByte();
        int count = in.readInt();
        List<NbtTag> items = new ArrayList<>(Math.max(0, count));
        if (elementTypeId != TAG_END) {
            for (int i = 0; i < count; i++) {
                items.add(readTag(in, elementTypeId));
            }
        }
        return new NbtTag.ListTag(items);
    }
}

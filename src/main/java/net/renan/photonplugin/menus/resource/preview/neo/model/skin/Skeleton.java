package net.renan.photonplugin.menus.resource.preview.neo.model.skin;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.*;

public final class Skeleton {
    public static final int FLOATS_PER_TRS = 10;

    private final int[] parents;
    private final float[] restTrs;
    private final Mat4[] inverseBind;
    private final String[] names;

    Skeleton(int[] parents, float[] restTrs, Mat4[] inverseBind, String[] names) {
        this.parents = parents;
        this.restTrs = restTrs;
        this.inverseBind = inverseBind;
        this.names = names;
    }

    public static Mat4 localFromTrs(float[] trs, int off) {
        Vec3 t = new Vec3(trs[off], trs[off + 1], trs[off + 2]);
        Quat r = new Quat(trs[off + 3], trs[off + 4], trs[off + 5], trs[off + 6]);
        Vec3 s = new Vec3(trs[off + 7], trs[off + 8], trs[off + 9]);
        return Mat4.fromTRS(t, r, s);
    }

    public int jointCount() {
        return parents.length;
    }

    public int parent(int joint) {
        return parents[joint];
    }

    public String name(int joint) {
        return names[joint];
    }

    public float[] restTrs() {
        return restTrs;
    }

    public Mat4 inverseBind(int joint) {
        return inverseBind[joint];
    }

    public void setInverseBind(int joint, Mat4 m) {
        inverseBind[joint] = m;
    }

    public static final class Builder {
        private final List<Integer> keys = new ArrayList<>();
        private final List<Integer> parentKeys = new ArrayList<>();
        private final List<float[]> trsList = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private final Map<Integer, Integer> slotOfKey = new HashMap<>();

        public boolean has(int key) {
            return slotOfKey.containsKey(key);
        }

        public void joint(int key, int parentKey, String name, float[] trs, int trsOff) {
            if (slotOfKey.containsKey(key)) {
                return;
            }
            slotOfKey.put(key, keys.size());
            keys.add(key);
            parentKeys.add(parentKey);
            names.add(name);
            trsList.add(Arrays.copyOfRange(trs, trsOff, trsOff + FLOATS_PER_TRS));
        }

        public int slot(int key) {
            return slotOfKey.getOrDefault(key, -1);
        }

        public int jointCount() {
            return keys.size();
        }

        public int[] sortInto(Skeleton[] out) {
            int n = keys.size();
            int[] remap = new int[n];
            Arrays.fill(remap, -1);
            int[] order = new int[n];
            int written = 0;
            boolean progress = true;
            while (written < n && progress) {
                progress = false;
                for (int slot = 0; slot < n; slot++) {
                    if (remap[slot] >= 0) {
                        continue;
                    }
                    int parentKey = parentKeys.get(slot);
                    int parentSlot = parentKey < 0 ? -1 : slotOfKey.getOrDefault(parentKey, -1);
                    if (parentSlot >= 0 && remap[parentSlot] < 0) {
                        continue;
                    }
                    order[written] = slot;
                    remap[slot] = written++;
                    progress = true;
                }
            }
            for (int slot = 0; slot < n; slot++) {
                if (remap[slot] < 0) {
                    order[written] = slot;
                    remap[slot] = written++;
                }
            }

            int[] parents = new int[n];
            float[] restTrs = new float[n * FLOATS_PER_TRS];
            Mat4[] inverseBind = new Mat4[n];
            String[] jointNames = new String[n];
            for (int sorted = 0; sorted < n; sorted++) {
                int slot = order[sorted];
                int parentKey = parentKeys.get(slot);
                int parentSlot = parentKey < 0 ? -1 : slotOfKey.getOrDefault(parentKey, -1);
                parents[sorted] = parentSlot < 0 ? -1 : remap[parentSlot];
                jointNames[sorted] = names.get(slot);
                System.arraycopy(trsList.get(slot), 0, restTrs, sorted * FLOATS_PER_TRS, FLOATS_PER_TRS);
                inverseBind[sorted] = Mat4.identity();
            }
            out[0] = new Skeleton(parents, restTrs, inverseBind, jointNames);
            return remap;
        }
    }
}

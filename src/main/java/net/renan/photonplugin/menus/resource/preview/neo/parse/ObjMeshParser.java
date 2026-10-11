package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ObjMeshParser {
    private ObjMeshParser() {
    }

    public static ObjMesh parse(byte[] bytes, boolean flipV) {
        return parseText(new String(bytes, StandardCharsets.UTF_8), flipV);
    }

    static ObjMesh parseText(String text, boolean flipV) {
        List<Vec3> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<Vec3> normals = new ArrayList<>();
        List<int[]> faceVerts = new ArrayList<>();
        List<MeshTriangle> triangles = new ArrayList<>();

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] tok = line.split("\\s+");
            switch (tok[0]) {
                case "v" -> {
                    if (tok.length >= 4) {
                        positions.add(new Vec3(f(tok[1]), f(tok[2]), f(tok[3])));
                    }
                }
                case "vt" -> uvs.add(new float[]{tok.length >= 2 ? f(tok[1]) : 0f, tok.length >= 3 ? f(tok[2]) : 0f});
                case "vn" -> {
                    if (tok.length >= 4) {
                        normals.add(new Vec3(f(tok[1]), f(tok[2]), f(tok[3])));
                    }
                }
                case "f" -> {
                    faceVerts.clear();
                    for (int i = 1; i < tok.length; i++) {
                        int[] ref = ref(tok[i], positions.size(), uvs.size(), normals.size());
                        if (ref != null) {
                            faceVerts.add(ref);
                        }
                    }
                    triangulate(faceVerts, positions, uvs, normals, flipV, triangles);
                }
                default -> {
                }
            }
        }
        return new ObjMesh(triangles);
    }

    private static void triangulate(List<int[]> face, List<Vec3> positions, List<float[]> uvs,
                                    List<Vec3> normals, boolean flipV, List<MeshTriangle> out) {
        int n = face.size();
        if (n < 3) {
            return;
        }
        Vec3[] p = new Vec3[n];
        for (int i = 0; i < n; i++) {
            p[i] = pos(positions, face.get(i)[0]);
            if (p[i] == null) {
                return;
            }
        }
        Vec3 polyN = polygonNormal(p);
        if (n == 3) {
            emitTri(face, p, uvs, normals, flipV, polyN, 0, 1, 2, out);
            return;
        }

        if (n == 4 && isConvex(p, polyN)) {
            emitTri(face, p, uvs, normals, flipV, polyN, 0, 1, 2, out);
            emitTri(face, p, uvs, normals, flipV, polyN, 0, 2, 3, out);
            return;
        }
        int[] order = earClip(p, polyN);
        if (order == null) {
            for (int i = 1; i + 1 < n; i++) {
                emitTri(face, p, uvs, normals, flipV, polyN, 0, i, i + 1, out);
            }
            return;
        }
        for (int i = 0; i + 2 < order.length; i += 3) {
            emitTri(face, p, uvs, normals, flipV, polyN, order[i], order[i + 1], order[i + 2], out);
        }
    }

    private static boolean isConvex(Vec3[] p, Vec3 normal) {
        int n = p.length;
        int sign = 0;
        for (int i = 0; i < n; i++) {
            Vec3 prev = p[(i + n - 1) % n];
            Vec3 cur = p[i];
            Vec3 next = p[(i + 1) % n];
            float turn = Vec3.cross(new Vec3(cur).sub(prev), new Vec3(next).sub(cur)).dot(normal);
            if (Math.abs(turn) < 1.0e-9f) {
                continue;
            }
            int s = turn < 0f ? -1 : 1;
            if (sign == 0) {
                sign = s;
            } else if (sign != s) {
                return false;
            }
        }
        return true;
    }

    private static void emitTri(List<int[]> face, Vec3[] p, List<float[]> uvs, List<Vec3> normals,
                                boolean flipV, Vec3 polyN, int a, int b, int c, List<MeshTriangle> out) {
        out.add(new MeshTriangle(
                vertex(p[a], uvOf(face.get(a)[1], uvs, flipV), normal(normals, face.get(a)[2], polyN)),
                vertex(p[b], uvOf(face.get(b)[1], uvs, flipV), normal(normals, face.get(b)[2], polyN)),
                vertex(p[c], uvOf(face.get(c)[1], uvs, flipV), normal(normals, face.get(c)[2], polyN))));
    }

    private static MeshVertex vertex(Vec3 p, float[] uv, Vec3 n) {
        return new MeshVertex(p, uv[0], uv[1], n);
    }

    private static int[] earClip(Vec3[] p, Vec3 normal) {
        int n = p.length;
        Vec3 axis = Math.abs(normal.x) > 0.9f ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = Vec3.cross(axis, normal).normalize();
        Vec3 v = Vec3.cross(normal, u);
        float[] x = new float[n], y = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = p[i].dot(u);
            y[i] = p[i].dot(v);
        }
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += (double) x[i] * y[j] - (double) x[j] * y[i];
        }
        List<Integer> idx = new ArrayList<>(n);
        if (area < 0) {
            for (int i = n - 1; i >= 0; i--) {
                idx.add(i);
            }
        } else {
            for (int i = 0; i < n; i++) {
                idx.add(i);
            }
        }

        int[] out = new int[(n - 2) * 3];
        int oi = 0;
        int guard = 0;
        while (idx.size() > 3) {
            if (guard++ > n * n) {
                return null;
            }
            boolean clipped = false;
            int m = idx.size();
            for (int a = 0; a < m; a++) {
                int ia = idx.get((a + m - 1) % m), ib = idx.get(a), ic = idx.get((a + 1) % m);
                if (isEar(x, y, idx, ia, ib, ic)) {
                    out[oi++] = ia;
                    out[oi++] = ib;
                    out[oi++] = ic;
                    idx.remove(a);
                    clipped = true;
                    break;
                }
            }
            if (!clipped) {
                return null;
            }
        }
        out[oi++] = idx.get(0);
        out[oi++] = idx.get(1);
        out[oi] = idx.get(2);
        return out;
    }

    private static boolean isEar(float[] x, float[] y, List<Integer> idx, int ia, int ib, int ic) {
        double cross = (double) (x[ib] - x[ia]) * (y[ic] - y[ia]) - (double) (y[ib] - y[ia]) * (x[ic] - x[ia]);
        if (cross <= 0) {
            return false;
        }
        for (int k : idx) {
            if (k == ia || k == ib || k == ic) {
                continue;
            }
            if (pointInTriangle(x[k], y[k], x[ia], y[ia], x[ib], y[ib], x[ic], y[ic])) {
                return false;
            }
        }
        return true;
    }

    private static boolean pointInTriangle(float px, float py, float ax, float ay,
                                           float bx, float by, float cx, float cy) {
        double d1 = sign(px, py, ax, ay, bx, by);
        double d2 = sign(px, py, bx, by, cx, cy);
        double d3 = sign(px, py, cx, cy, ax, ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double sign(float px, float py, float ax, float ay, float bx, float by) {
        return (double) (px - bx) * (ay - by) - (double) (ax - bx) * (py - by);
    }

    private static Vec3 polygonNormal(Vec3[] p) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < p.length; i++) {
            Vec3 cur = p[i], nxt = p[(i + 1) % p.length];
            nx += (cur.y - nxt.y) * (cur.z + nxt.z);
            ny += (cur.z - nxt.z) * (cur.x + nxt.x);
            nz += (cur.x - nxt.x) * (cur.y + nxt.y);
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1.0e-6f ? new Vec3(0, 0, 1) : new Vec3(nx / len, ny / len, nz / len);
    }

    private static int[] ref(String token, int posCount, int uvCount, int nrmCount) {
        String[] parts = token.split("/", -1);
        int p = resolve(parts[0], posCount);
        if (p < 0) {
            return null;
        }
        int vt = parts.length >= 2 ? resolve(parts[1], uvCount) : -1;
        int vn = parts.length >= 3 ? resolve(parts[2], nrmCount) : -1;
        return new int[]{p, vt, vn};
    }

    private static int resolve(String s, int count) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        int idx;
        try {
            idx = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
        int zero = idx < 0 ? count + idx : idx - 1;
        return (zero >= 0 && zero < count) ? zero : -1;
    }

    private static Vec3 pos(List<Vec3> positions, int i) {
        return (i >= 0 && i < positions.size()) ? positions.get(i) : null;
    }

    private static float[] uvOf(int i, List<float[]> uvs, boolean flipV) {
        float[] uv = (i >= 0 && i < uvs.size()) ? uvs.get(i) : new float[]{0f, 0f};
        return flipV ? new float[]{uv[0], 1f - uv[1]} : uv;
    }

    private static Vec3 normal(List<Vec3> normals, int i, Vec3 fallback) {
        return (i >= 0 && i < normals.size()) ? normals.get(i) : fallback;
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }
}

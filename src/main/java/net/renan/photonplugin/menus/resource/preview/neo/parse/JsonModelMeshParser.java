package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.json.MinimalJsonParser;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshTriangle;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.MeshVertex;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.ObjMesh;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JsonModelMeshParser {
    private static final List<String> FACE_NAMES = List.of("down", "up", "north", "south", "west", "east");

    private static final int MAX_PARENT_DEPTH = 16;

    private JsonModelMeshParser() {
    }

    public static ObjMesh parse(byte[] bytes) {
        return parse(bytes, null);
    }

    public static ObjMesh parse(byte[] bytes, java.util.function.Function<String, byte[]> parentLoader) {
        Map<String, Object> rootMap = MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)));
        Map<String, Object> textures = new java.util.LinkedHashMap<>();
        List<Object> elements = MinimalJsonParser.asList(rootMap.get("elements"));
        boolean haveElements = rootMap.containsKey("elements");

        Map<String, Object> current = rootMap;
        for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
            for (Map.Entry<String, Object> e : MinimalJsonParser.asMap(current.get("textures")).entrySet()) {
                textures.putIfAbsent(e.getKey(), e.getValue());
            }
            if (!haveElements && current.containsKey("elements")) {
                elements = MinimalJsonParser.asList(current.get("elements"));
                haveElements = true;
            }
            String parent = MinimalJsonParser.asString(current.get("parent"), "");
            byte[] parentBytes = parentLoader == null || parent.isBlank() ? null : parentLoader.apply(parent);
            current = parentBytes == null ? null
                    : MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(parentBytes, StandardCharsets.UTF_8)));
        }

        List<MeshTriangle> triangles = new ArrayList<>();
        for (Object elementObj : elements) {
            Map<String, Object> element = MinimalJsonParser.asMap(elementObj);
            ElementRotation rotation = readRotation(element.get("rotation"));
            float[] from = readBox(element.get("from"), 0, 0, 0);
            float[] to = readBox(element.get("to"), 16, 16, 16);
            Map<String, Object> faces = MinimalJsonParser.asMap(element.get("faces"));
            for (String faceName : FACE_NAMES) {
                if (!faces.containsKey(faceName)) {
                    continue;
                }
                Map<String, Object> face = MinimalJsonParser.asMap(faces.get(faceName));
                String textureRef = resolveTexture(MinimalJsonParser.asString(face.get("texture"), ""), textures);
                float[] uv = readUv(face.get("uv"), faceName, from, to);
                int faceRotation = Math.floorMod((int) MinimalJsonParser.asDouble(face.get("rotation"), 0) / 90, 4);
                String cullface = MinimalJsonParser.asString(face.get("cullface"), "");
                addFace(triangles, faceName, from, to, uv, faceRotation, rotation, textureRef, shadeFor(cullface));
            }
        }
        return new ObjMesh(triangles);
    }

    private static float[] readBox(Object arr, float dx, float dy, float dz) {
        List<Object> list = MinimalJsonParser.asList(arr);
        if (list.size() < 3) {
            return new float[]{dx, dy, dz};
        }
        return new float[]{
                (float) MinimalJsonParser.asDouble(list.get(0), dx),
                (float) MinimalJsonParser.asDouble(list.get(1), dy),
                (float) MinimalJsonParser.asDouble(list.get(2), dz)};
    }

    private static String resolveTexture(String ref, Map<String, Object> textures) {
        String current = ref;
        for (int hop = 0; hop < 4 && current.startsWith("#"); hop++) {
            Object next = textures.get(current.substring(1));
            if (!(next instanceof String s)) {
                return null;
            }
            current = s;
        }
        if (current.startsWith("#") || current.isBlank()) {
            return null;
        }
        return toResourceLocation(current);
    }

    private static String toResourceLocation(String value) {
        String namespace = "minecraft";
        String path = value;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            namespace = value.substring(0, colon);
            path = value.substring(colon + 1);
        }
        return namespace + ":textures/" + path + ".png";
    }

    private static float[] readUv(Object uvObj, String faceName, float[] from, float[] to) {
        List<Object> list = MinimalJsonParser.asList(uvObj);
        if (list.size() >= 4) {
            return new float[]{
                    (float) MinimalJsonParser.asDouble(list.get(0), 0) / 16f,
                    (float) MinimalJsonParser.asDouble(list.get(1), 0) / 16f,
                    (float) MinimalJsonParser.asDouble(list.get(2), 16) / 16f,
                    (float) MinimalJsonParser.asDouble(list.get(3), 16) / 16f};
        }
        float[] d = switch (faceName) {
            case "down" -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case "up" -> new float[]{from[0], from[2], to[0], to[2]};
            case "north" -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case "south" -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case "west" -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            default -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
        return new float[]{d[0] / 16f, d[1] / 16f, d[2] / 16f, d[3] / 16f};
    }

    private static float shadeFor(String cullface) {
        return switch (cullface) {
            case "down", "up" -> 0.9f;
            case "north", "south" -> 0.8f;
            case "west", "east" -> 0.6f;
            default -> 1f;
        };
    }

    private static Vec3 toLocal(float x, float y, float z) {
        return new Vec3((x - 8f) / 16f, (y - 8f) / 16f, (z - 8f) / 16f);
    }

    private static ElementRotation readRotation(Object obj) {
        if (!(obj instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> map = MinimalJsonParser.asMap(obj);
        String axis = MinimalJsonParser.asString(map.get("axis"), "y");
        float angle = (float) MinimalJsonParser.asDouble(map.get("angle"), 0);
        if (angle == 0f) {
            return null;
        }
        return new ElementRotation(readBox(map.get("origin"), 8, 8, 8), axis.isEmpty() ? 'y' : axis.charAt(0), angle,
                MinimalJsonParser.asBoolean(map.get("rescale"), false));
    }

    private static float[][] corners(String face, float[] f, float[] t) {
        float x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        return switch (face) {
            case "down" -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "up" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case "north" -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case "west" -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            default -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };
    }

    private static float[] rotate(float[] p, ElementRotation r) {
        Vec3 v = new Vec3(p[0] - r.origin()[0], p[1] - r.origin()[1], p[2] - r.origin()[2]);
        float rad = (float) Math.toRadians(r.angle());
        Quat q = switch (r.axis()) {
            case 'x' -> Quat.rotationX(rad);
            case 'z' -> Quat.rotationZ(rad);
            default -> Quat.rotationY(rad);
        };
        v = q.rotate(v);
        if (r.rescale()) {
            float k = (float) (1.0 / Math.cos(Math.toRadians(Math.abs(r.angle()) == 22.5f ? 22.5 : 45)));
            switch (r.axis()) {
                case 'x' -> v = new Vec3(v.x, v.y * k, v.z * k);
                case 'z' -> v = new Vec3(v.x * k, v.y * k, v.z);
                default -> v = new Vec3(v.x * k, v.y, v.z * k);
            }
        }
        return new float[]{v.x + r.origin()[0], v.y + r.origin()[1], v.z + r.origin()[2]};
    }

    private static void addFace(List<MeshTriangle> out, String faceName, float[] from, float[] to, float[] uv,
                                int faceRotation, ElementRotation rotation, String textureRef, float shade) {
        float[][] c = corners(faceName, from, to);
        MeshVertex[] v = new MeshVertex[4];
        for (int i = 0; i < 4; i++) {
            int shifted = (i + faceRotation) % 4;
            float u = uv[shifted != 0 && shifted != 1 ? 2 : 0];
            float vv = uv[shifted != 0 && shifted != 3 ? 3 : 1];
            float[] p = rotation == null ? c[i] : rotate(c[i], rotation);
            v[i] = new MeshVertex(toLocal(p[0], p[1], p[2]), u, vv, Vec3.zero());
        }
        out.add(new MeshTriangle(v[0], v[1], v[2], textureRef, shade));
        out.add(new MeshTriangle(v[0], v[2], v[3], textureRef, shade));
    }

    private record ElementRotation(float[] origin, char axis, float angle, boolean rescale) {
    }
}

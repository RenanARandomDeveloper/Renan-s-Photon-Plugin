package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.mcreator.ui.init.L10N;

import net.renan.photonplugin.menus.resource.preview.common.json.MinimalJsonParser;
import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.mesh.*;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.AnimationClip;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.Skeleton;
import net.renan.photonplugin.menus.resource.preview.neo.model.skin.SkinnedModel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GltfMeshParser {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private static final int MAX_NODE_DEPTH = 64;

    private GltfMeshParser() {
    }

    public static ObjMesh parse(byte[] bytes, boolean flipV) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Object> root;
        byte[] glbBin = null;
        if (bytes.length >= 12 && buffer.getInt(0) == GLB_MAGIC) {
            GlbChunks chunks = readGlbChunks(buffer, bytes.length);
            root = MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(chunks.json, StandardCharsets.UTF_8)));
            glbBin = chunks.bin;
        } else {
            root = MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)));
        }
        return new Reader(root, glbBin, flipV).read();
    }

    public static SkinnedModel parseAnimated(byte[] bytes, boolean flipV) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Object> root;
        byte[] glbBin = null;
        if (bytes.length >= 12 && buffer.getInt(0) == GLB_MAGIC) {
            GlbChunks chunks = readGlbChunks(buffer, bytes.length);
            root = MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(chunks.json, StandardCharsets.UTF_8)));
            glbBin = chunks.bin;
        } else {
            root = MinimalJsonParser.asMap(MinimalJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)));
        }
        return new Reader(root, glbBin, flipV).readAnimated();
    }

    private static GlbChunks readGlbChunks(ByteBuffer buffer, int length) {
        int version = buffer.getInt(4);
        if (version != 2) {
            throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.glb_version", version));
        }
        byte[] json = null;
        byte[] bin = null;
        int offset = 12;
        while (offset + 8 <= length) {
            int chunkLength = buffer.getInt(offset);
            int chunkType = buffer.getInt(offset + 4);
            int dataStart = offset + 8;

            if (chunkLength < 0 || chunkLength > length - dataStart) {
                throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.glb_truncated", offset));
            }
            if (chunkType == CHUNK_JSON && json == null) {
                json = new byte[chunkLength];
                buffer.get(dataStart, json);
            } else if (chunkType == CHUNK_BIN && bin == null) {
                bin = new byte[chunkLength];
                buffer.get(dataStart, bin);
            }
            offset = dataStart + chunkLength;
        }
        if (json == null) {
            throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.glb_no_json"));
        }
        return new GlbChunks(json, bin);
    }

    private record GlbChunks(byte[] json, byte[] bin) {
    }

    private static final class Reader {
        private final Map<String, Object> root;
        private final byte[] glbBin;
        private final boolean flipV;
        private final List<byte[]> buffers = new ArrayList<>();
        private final List<MeshTriangle> triangles = new ArrayList<>();
        private final List<SkinnedTriangle> skinnedTriangles = new ArrayList<>();
        private final Map<Integer, Integer> nodeSkin = new HashMap<>();
        private final Map<Integer, int[]> skinJointSlots = new HashMap<>();
        private final Map<Integer, Integer> nodeToPreSortSlot = new HashMap<>();
        private final Map<Integer, Integer> nodeToSkeletonSlot = new HashMap<>();

        private final Map<Integer, Integer> rigidSlotOfNode = new HashMap<>();
        private int[] nodeParent;
        private Skeleton skeleton;

        Reader(Map<String, Object> root, byte[] glbBin, boolean flipV) {
            this.root = root;
            this.glbBin = glbBin;
            this.flipV = flipV;
        }

        private static Mat4 localTransform(Map<String, Object> node) {
            List<Object> m = MinimalJsonParser.asList(node.get("matrix"));
            if (m.size() >= 16) {
                float[] values = new float[16];
                for (int i = 0; i < 16; i++) {
                    values[i] = (float) MinimalJsonParser.asDouble(m.get(i), 0);
                }
                return Mat4.fromColumnMajor(values);
            }
            if (!m.isEmpty()) {
                return Mat4.identity();
            }
            Vec3 t = vec3(node.get("translation"), 0, 0, 0);
            Quat r = quat(node.get("rotation"));
            Vec3 s = vec3(node.get("scale"), 1, 1, 1);
            return Mat4.fromTRS(t, r, s);
        }

        private static Vec3 vec3(Object arr, float dx, float dy, float dz) {
            List<Object> list = MinimalJsonParser.asList(arr);
            if (list.size() < 3) {
                return new Vec3(dx, dy, dz);
            }
            return new Vec3(
                    (float) MinimalJsonParser.asDouble(list.get(0), dx),
                    (float) MinimalJsonParser.asDouble(list.get(1), dy),
                    (float) MinimalJsonParser.asDouble(list.get(2), dz));
        }

        private static Quat quat(Object arr) {
            List<Object> list = MinimalJsonParser.asList(arr);
            if (list.size() < 4) {
                return Quat.identity();
            }
            return new Quat(
                    (float) MinimalJsonParser.asDouble(list.get(0), 0),
                    (float) MinimalJsonParser.asDouble(list.get(1), 0),
                    (float) MinimalJsonParser.asDouble(list.get(2), 0),
                    (float) MinimalJsonParser.asDouble(list.get(3), 1));
        }

        private static float[] normalMatrix(float determinant, Mat4 world) {
            return Float.isFinite(determinant) && Math.abs(determinant) > 1.0e-12f ? world.normalMatrix3x3() : null;
        }

        private static boolean isFiniteNonZero(Vec3 v) {
            float len2 = v.lengthSquared();
            return Float.isFinite(len2) && len2 > 1.0e-20f;
        }

        private static MeshVertex withNormal(MeshVertex v, Vec3 normal) {
            return new MeshVertex(v.position(), v.u(), v.v(), normal);
        }

        private static Vec3 faceNormal(Vec3 a, Vec3 b, Vec3 c) {
            Vec3 normal = Vec3.cross(new Vec3(b).sub(a), new Vec3(c).sub(a));
            float len = normal.length();
            return len < 1.0e-6f ? new Vec3(0, 0, 1) : normal.mul(1f / len);
        }

        private static int[] sequence(int count) {
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = i;
            }
            return out;
        }

        private static boolean fitsInBuffer(int base, int count, int stride, int elementSize, int bufferLimit) {
            if (base < 0 || count <= 0 || stride < 0 || elementSize < 0) {
                return false;
            }
            long lastElementEnd = (long) base + (long) (count - 1) * stride + elementSize;
            return lastElementEnd <= bufferLimit;
        }

        private static float readComponent(ByteBuffer data, int at, int componentType, boolean normalized) {
            return switch (componentType) {
                case 5120 -> normalized ? Math.max(data.get(at) / 127f, -1f) : data.get(at);
                case 5121 -> normalized ? (data.get(at) & 0xFF) / 255f : (data.get(at) & 0xFF);
                case 5122 -> normalized ? Math.max(data.getShort(at) / 32767f, -1f) : data.getShort(at);
                case 5123 -> normalized ? (data.getShort(at) & 0xFFFF) / 65535f : (data.getShort(at) & 0xFFFF);
                case 5125 -> data.getInt(at);
                case 5126 -> data.getFloat(at);
                default -> 0f;
            };
        }

        private static int componentSize(int componentType) {
            return switch (componentType) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> 0;
            };
        }

        private static int componentsOf(String type) {
            return switch (type) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                default -> -1;
            };
        }

        private static float[] nodeTrs(Map<String, Object> node) {
            float[] trs = new float[Skeleton.FLOATS_PER_TRS];
            List<Object> m = MinimalJsonParser.asList(node.get("matrix"));
            Vec3 t;
            Quat r;
            Vec3 s;
            if (m.size() >= 16) {
                float[] values = new float[16];
                for (int i = 0; i < 16; i++) {
                    values[i] = (float) MinimalJsonParser.asDouble(m.get(i), 0);
                }
                Mat4 mat = Mat4.fromColumnMajor(values);
                t = new Vec3(mat.m[12], mat.m[13], mat.m[14]);
                Vec3 c0 = new Vec3(mat.m[0], mat.m[1], mat.m[2]);
                Vec3 c1 = new Vec3(mat.m[4], mat.m[5], mat.m[6]);
                Vec3 c2 = new Vec3(mat.m[8], mat.m[9], mat.m[10]);
                s = new Vec3(c0.length(), c1.length(), c2.length());
                r = quatFromAxes(s.x > 1e-8f ? c0.mul(1f / s.x) : new Vec3(1, 0, 0),
                        s.y > 1e-8f ? c1.mul(1f / s.y) : new Vec3(0, 1, 0),
                        s.z > 1e-8f ? c2.mul(1f / s.z) : new Vec3(0, 0, 1));
            } else {
                t = vec3(node.get("translation"), 0, 0, 0);
                r = quat(node.get("rotation"));
                s = vec3(node.get("scale"), 1, 1, 1);
            }
            trs[0] = t.x;
            trs[1] = t.y;
            trs[2] = t.z;
            trs[3] = r.x;
            trs[4] = r.y;
            trs[5] = r.z;
            trs[6] = r.w;
            trs[7] = s.x;
            trs[8] = s.y;
            trs[9] = s.z;
            return trs;
        }

        private static Quat quatFromAxes(Vec3 x, Vec3 y, Vec3 z) {
            float trace = x.x + y.y + z.z;
            if (trace > 0) {
                float s = (float) Math.sqrt(trace + 1f) * 2f;
                return new Quat((y.z - z.y) / s, (z.x - x.z) / s, (x.y - y.x) / s, s / 4f);
            } else if (x.x > y.y && x.x > z.z) {
                float s = (float) Math.sqrt(1f + x.x - y.y - z.z) * 2f;
                return new Quat(s / 4f, (y.x + x.y) / s, (z.x + x.z) / s, (y.z - z.y) / s);
            } else if (y.y > z.z) {
                float s = (float) Math.sqrt(1f + y.y - x.x - z.z) * 2f;
                return new Quat((y.x + x.y) / s, s / 4f, (z.y + y.z) / s, (z.x - x.z) / s);
            } else {
                float s = (float) Math.sqrt(1f + z.z - x.x - y.y) * 2f;
                return new Quat((z.x + x.z) / s, (z.y + y.z) / s, s / 4f, (x.y - y.x) / s);
            }
        }

        private static SkinnedVertex withSkinnedNormal(SkinnedVertex v, Vec3 normal) {
            return new SkinnedVertex(v.position(), normal, v.u(), v.v(), v.joints(), v.weights());
        }

        private static int rigidKey(int node, int nodeCount) {
            return nodeCount + node;
        }

        ObjMesh read() {
            decodeBuffers();
            List<Object> nodes = list("nodes");
            List<Object> scenes = list("scenes");
            List<Integer> roots = new ArrayList<>();
            int sceneIndex = (int) MinimalJsonParser.asDouble(root.get("scene"), 0);
            if (sceneIndex >= 0 && sceneIndex < scenes.size()) {
                Map<String, Object> scene = MinimalJsonParser.asMap(scenes.get(sceneIndex));
                for (Object n : MinimalJsonParser.asList(scene.get("nodes"))) {
                    roots.add((int) MinimalJsonParser.asDouble(n, -1));
                }
            }
            if (roots.isEmpty()) {
                Set<Integer> children = new HashSet<>();
                for (Object nodeObj : nodes) {
                    Map<String, Object> node = MinimalJsonParser.asMap(nodeObj);
                    for (Object k : MinimalJsonParser.asList(node.get("children"))) {
                        children.add((int) MinimalJsonParser.asDouble(k, -1));
                    }
                }
                for (int i = 0; i < nodes.size(); i++) {
                    if (!children.contains(i)) {
                        roots.add(i);
                    }
                }
            }
            for (int nodeIndex : roots) {
                visitNode(nodes, nodeIndex, Mat4.identity(), 0);
            }
            return new ObjMesh(triangles);
        }

        private void visitNode(List<Object> nodes, int index, Mat4 parent, int depth) {
            if (depth > MAX_NODE_DEPTH || index < 0 || index >= nodes.size()) {
                return;
            }
            Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(index));
            Mat4 world = parent.multiply(localTransform(node));
            if (node.containsKey("mesh")) {
                readMesh((int) MinimalJsonParser.asDouble(node.get("mesh"), -1), world);
            }
            for (Object child : MinimalJsonParser.asList(node.get("children"))) {
                visitNode(nodes, (int) MinimalJsonParser.asDouble(child, -1), world, depth + 1);
            }
        }

        private void readMesh(int meshIndex, Mat4 world) {
            List<Object> meshes = list("meshes");
            if (meshIndex < 0 || meshIndex >= meshes.size()) {
                return;
            }
            float determinant = world.determinant3x3();

            float[] normalMatrix = normalMatrix(determinant, world);

            boolean mirrored = determinant < 0f;
            Map<String, Object> mesh = MinimalJsonParser.asMap(meshes.get(meshIndex));
            for (Object primitiveObj : MinimalJsonParser.asList(mesh.get("primitives"))) {
                readPrimitive(MinimalJsonParser.asMap(primitiveObj), world, normalMatrix, mirrored);
            }
        }

        private void readPrimitive(Map<String, Object> primitive, Mat4 world, float[] normalMatrix, boolean mirrored) {
            int mode = (int) MinimalJsonParser.asDouble(primitive.get("mode"), 4);
            if (mode != 4) {
                return;
            }
            Map<String, Object> attributes = MinimalJsonParser.asMap(primitive.get("attributes"));
            if (!attributes.containsKey("POSITION")) {
                return;
            }
            float[] positions = readAccessor((int) MinimalJsonParser.asDouble(attributes.get("POSITION"), -1), 3);
            if (positions == null) {
                return;
            }
            int vertexCount = positions.length / 3;
            float[] normals = attributeOf(attributes, "NORMAL", 3, vertexCount);
            float[] uvs = attributeOf(attributes, "TEXCOORD_0", 2, vertexCount);

            int[] indices = primitive.containsKey("indices")
                    ? readIndices((int) MinimalJsonParser.asDouble(primitive.get("indices"), -1))
                    : sequence(vertexCount);
            if (indices == null) {
                return;
            }

            for (int i = 0; i + 2 < indices.length; i += 3) {
                MeshVertex[] corner = new MeshVertex[3];
                boolean ok = true;
                for (int k = 0; k < 3 && ok; k++) {
                    int src = (mirrored && k > 0) ? 3 - k : k;
                    ok = fillCorner(corner, k, indices[i + src], vertexCount, positions, normals, uvs, world, normalMatrix);
                }
                if (!ok) {
                    continue;
                }
                if (normals == null) {
                    Vec3 faceNormal = faceNormal(corner[0].position(), corner[1].position(), corner[2].position());
                    corner[0] = withNormal(corner[0], faceNormal);
                    corner[1] = withNormal(corner[1], faceNormal);
                    corner[2] = withNormal(corner[2], faceNormal);
                }
                triangles.add(new MeshTriangle(corner[0], corner[1], corner[2]));
            }
        }

        private boolean fillCorner(MeshVertex[] corner, int slot, int vertex, int vertexCount, float[] positions,
                                   float[] normals, float[] uvs, Mat4 world, float[] normalMatrix) {
            if (vertex < 0 || vertex >= vertexCount) {
                return false;
            }
            Vec3 worldPos = world.transformPosition(new Vec3(positions[vertex * 3], positions[vertex * 3 + 1],
                    positions[vertex * 3 + 2]));
            float u = uvs == null ? 0f : uvs[vertex * 2];
            float v = uvs == null ? 0f : uvs[vertex * 2 + 1];
            Vec3 normal = Vec3.zero();
            if (normals != null) {
                Vec3 localNormal = new Vec3(normals[vertex * 3], normals[vertex * 3 + 1], normals[vertex * 3 + 2]);
                normal = normalMatrix != null ? Mat4.transformDirection3x3(normalMatrix, localNormal) : localNormal;
                normal = isFiniteNonZero(normal) ? normal.normalize() : new Vec3(0, 1, 0);
            }
            corner[slot] = new MeshVertex(worldPos, u, flipV ? 1f - v : v, normal);
            return true;
        }

        private float[] attributeOf(Map<String, Object> attributes, String name, int components, int vertexCount) {
            if (!attributes.containsKey(name)) {
                return null;
            }
            float[] values = readAccessor((int) MinimalJsonParser.asDouble(attributes.get(name), -1), components);

            return values != null && values.length >= vertexCount * components ? values : null;
        }

        private void decodeBuffers() {
            List<Object> declared = list("buffers");
            for (int i = 0; i < declared.size(); i++) {
                Map<String, Object> buffer = MinimalJsonParser.asMap(declared.get(i));
                if (!buffer.containsKey("uri")) {
                    if (i == 0 && glbBin != null) {
                        buffers.add(glbBin);
                        continue;
                    }
                    throw new IllegalArgumentException(
                            L10N.t("plugin.photon.resourcemenu.fx.preview.error.buffer_no_uri", i));
                }
                String uri = MinimalJsonParser.asString(buffer.get("uri"), "");
                if (!uri.startsWith("data:")) {
                    throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.buffer_external", i, uri));
                }
                int comma = uri.indexOf(',');
                if (comma < 0 || uri.lastIndexOf("base64", comma) < 0) {
                    throw new IllegalArgumentException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.buffer_not_base64", i));
                }
                buffers.add(Base64.getDecoder().decode(uri.substring(comma + 1)));
            }
        }

        private float[] readAccessor(int index, int components) {
            List<Object> accessors = list("accessors");
            if (index < 0 || index >= accessors.size()) {
                return null;
            }
            Map<String, Object> accessor = MinimalJsonParser.asMap(accessors.get(index));
            if (accessor.containsKey("sparse")) {
                return null;
            }
            int count = (int) MinimalJsonParser.asDouble(accessor.get("count"), 0);
            int componentType = (int) MinimalJsonParser.asDouble(accessor.get("componentType"), 0);
            int declared = componentsOf(MinimalJsonParser.asString(accessor.get("type"), ""));
            if (declared != components || count <= 0) {
                return null;
            }
            boolean normalized = MinimalJsonParser.asBoolean(accessor.get("normalized"), false);
            int componentSize = componentSize(componentType);
            if (componentSize == 0) {
                return null;
            }

            View view = bufferView(accessor);
            if (view == null) {
                return new float[count * components];
            }
            int stride = view.stride > 0 ? view.stride : components * componentSize;
            int base = view.offset + (int) MinimalJsonParser.asDouble(accessor.get("byteOffset"), 0);
            ByteBuffer data = view.data;
            if (!fitsInBuffer(base, count, stride, components * componentSize, data.limit())) {
                return null;
            }
            float[] out = new float[count * components];
            for (int i = 0; i < count; i++) {
                int element = base + i * stride;
                for (int c = 0; c < components; c++) {
                    int at = element + c * componentSize;
                    out[i * components + c] = readComponent(data, at, componentType, normalized);
                }
            }
            return out;
        }

        private int[] readIndices(int index) {
            List<Object> accessors = list("accessors");
            if (index < 0 || index >= accessors.size()) {
                return null;
            }
            Map<String, Object> accessor = MinimalJsonParser.asMap(accessors.get(index));
            if (accessor.containsKey("sparse")) {
                return null;
            }
            int count = (int) MinimalJsonParser.asDouble(accessor.get("count"), 0);
            int componentType = (int) MinimalJsonParser.asDouble(accessor.get("componentType"), 0);
            int componentSize = componentSize(componentType);
            if (componentSize == 0 || count <= 0) {
                return null;
            }
            View view = bufferView(accessor);
            if (view == null) {
                return null;
            }
            int stride = view.stride > 0 ? view.stride : componentSize;
            int base = view.offset + (int) MinimalJsonParser.asDouble(accessor.get("byteOffset"), 0);
            ByteBuffer data = view.data;
            if (!fitsInBuffer(base, count, stride, componentSize, data.limit())) {
                return null;
            }
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                int at = base + i * stride;
                out[i] = switch (componentType) {
                    case 5121 -> data.get(at) & 0xFF;
                    case 5123 -> data.getShort(at) & 0xFFFF;
                    case 5125 -> data.getInt(at);
                    default -> -1;
                };
            }
            return out;
        }

        private View bufferView(Map<String, Object> accessor) {
            if (!accessor.containsKey("bufferView")) {
                return null;
            }
            List<Object> views = list("bufferViews");
            int index = (int) MinimalJsonParser.asDouble(accessor.get("bufferView"), -1);
            if (index < 0 || index >= views.size()) {
                return null;
            }
            Map<String, Object> view = MinimalJsonParser.asMap(views.get(index));
            int bufferIndex = (int) MinimalJsonParser.asDouble(view.get("buffer"), -1);
            if (bufferIndex < 0 || bufferIndex >= buffers.size()) {
                return null;
            }
            ByteBuffer data = ByteBuffer.wrap(buffers.get(bufferIndex)).order(ByteOrder.LITTLE_ENDIAN);
            int offset = (int) MinimalJsonParser.asDouble(view.get("byteOffset"), 0);
            int stride = (int) MinimalJsonParser.asDouble(view.get("byteStride"), 0);
            return new View(data, offset, stride);
        }

        private List<Object> list(String name) {
            return MinimalJsonParser.asList(root.get(name));
        }

        SkinnedModel readAnimated() {
            decodeBuffers();
            List<Object> nodes = list("nodes");
            buildNodeParents(nodes);
            buildSkeleton(nodes);
            if (skeleton == null) {
                return new SkinnedModel(List.of(), null, List.of());
            }

            List<Object> scenes = list("scenes");
            List<Integer> roots = new ArrayList<>();
            int sceneIndex = (int) MinimalJsonParser.asDouble(root.get("scene"), 0);
            if (sceneIndex >= 0 && sceneIndex < scenes.size()) {
                Map<String, Object> scene = MinimalJsonParser.asMap(scenes.get(sceneIndex));
                for (Object n : MinimalJsonParser.asList(scene.get("nodes"))) {
                    roots.add((int) MinimalJsonParser.asDouble(n, -1));
                }
            }
            if (roots.isEmpty()) {
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodeParent[i] < 0) {
                        roots.add(i);
                    }
                }
            }
            for (int nodeIndex : roots) {
                visitNodeAnimated(nodes, nodeIndex, Mat4.identity(), 0);
            }

            List<AnimationClip> clips = readAnimations(nodes);
            return new SkinnedModel(skinnedTriangles, skeleton, clips);
        }

        private void buildNodeParents(List<Object> nodes) {
            nodeParent = new int[nodes.size()];
            Arrays.fill(nodeParent, -1);
            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(i));
                for (Object child : MinimalJsonParser.asList(node.get("children"))) {
                    int childIndex = (int) MinimalJsonParser.asDouble(child, -1);
                    if (childIndex >= 0 && childIndex < nodeParent.length) {
                        nodeParent[childIndex] = i;
                    }
                }
            }
        }

        private void buildSkeleton(List<Object> nodes) {
            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(i));
                if (node.containsKey("skin")) {
                    nodeSkin.put(i, (int) MinimalJsonParser.asDouble(node.get("skin"), -1));
                }
            }
            List<Integer> rigidNodes = rigidMeshNodes(nodes);
            if (nodeSkin.isEmpty() && rigidNodes.isEmpty()) {
                return;
            }
            List<Object> skins = list("skins");
            Skeleton.Builder builder = new Skeleton.Builder();
            for (Object skinObj : skins) {
                Map<String, Object> skin = MinimalJsonParser.asMap(skinObj);
                for (Object jointObj : MinimalJsonParser.asList(skin.get("joints"))) {
                    addJointChain(builder, (int) MinimalJsonParser.asDouble(jointObj, -1), nodes);
                }
            }

            float[] identity = {0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
            for (int node : rigidNodes) {
                addJointChain(builder, node, nodes);
                builder.joint(rigidKey(node, nodes.size()), node, "rigid" + node, identity, 0);
            }
            if (builder.jointCount() == 0) {
                return;
            }
            Skeleton[] out = new Skeleton[1];
            int[] remap = builder.sortInto(out);
            skeleton = out[0];
            for (Map.Entry<Integer, Integer> entry : nodeToPreSortSlot.entrySet()) {
                nodeToSkeletonSlot.put(entry.getKey(), remap[entry.getValue()]);
            }
            for (int node : rigidNodes) {
                int key = builder.slot(rigidKey(node, nodes.size()));
                if (key >= 0) {
                    rigidSlotOfNode.put(node, remap[key]);
                }
            }

            for (int skinIndex = 0; skinIndex < skins.size(); skinIndex++) {
                Map<String, Object> skin = MinimalJsonParser.asMap(skins.get(skinIndex));
                List<Object> joints = MinimalJsonParser.asList(skin.get("joints"));
                int[] slots = new int[joints.size()];
                float[] inverseBind = skin.containsKey("inverseBindMatrices")
                        ? readAccessorMat4((int) MinimalJsonParser.asDouble(skin.get("inverseBindMatrices"), -1), joints.size())
                        : null;
                for (int j = 0; j < joints.size(); j++) {
                    int nodeIndex = (int) MinimalJsonParser.asDouble(joints.get(j), -1);
                    int key = builder.slot(nodeIndex);
                    int slot = key < 0 ? -1 : remap[key];
                    slots[j] = slot;
                    if (slot >= 0) {
                        Mat4 inv = inverseBind != null ? Mat4.fromColumnMajor(Arrays.copyOfRange(inverseBind, j * 16, j * 16 + 16))
                                : Mat4.identity();
                        skeleton.setInverseBind(slot, inv);
                    }
                }
                skinJointSlots.put(skinIndex, slots);
            }
        }

        private List<Integer> rigidMeshNodes(List<Object> nodes) {
            Set<Integer> animated = new HashSet<>();
            for (Object animationObj : list("animations")) {
                for (Object channelObj : MinimalJsonParser.asList(MinimalJsonParser.asMap(animationObj).get("channels"))) {
                    Map<String, Object> target = MinimalJsonParser.asMap(MinimalJsonParser.asMap(channelObj).get("target"));
                    String path = MinimalJsonParser.asString(target.get("path"), "");
                    if (target.containsKey("node")
                            && (path.equals("translation") || path.equals("rotation") || path.equals("scale"))) {
                        animated.add((int) MinimalJsonParser.asDouble(target.get("node"), -1));
                    }
                }
            }
            List<Integer> rigid = new ArrayList<>();
            if (animated.isEmpty()) {
                return rigid;
            }
            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(i));
                if (!node.containsKey("mesh") || node.containsKey("skin")) {
                    continue;
                }
                int cursor = i;
                for (int depth = 0; cursor >= 0 && depth <= MAX_NODE_DEPTH; depth++) {
                    if (animated.contains(cursor)) {
                        rigid.add(i);
                        break;
                    }
                    cursor = nodeParent[cursor];
                }
            }
            return rigid;
        }

        private void addJointChain(Skeleton.Builder builder, int nodeIndex, List<Object> nodes) {
            List<Integer> chain = new ArrayList<>();
            int cursor = nodeIndex;
            int guard = 0;
            while (cursor >= 0 && cursor < nodes.size() && !builder.has(cursor) && guard++ < MAX_NODE_DEPTH) {
                chain.add(cursor);
                cursor = nodeParent[cursor];
            }
            int parentKey = cursor >= 0 && builder.has(cursor) ? cursor : -1;
            for (int i = chain.size() - 1; i >= 0; i--) {
                int key = chain.get(i);
                Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(key));
                float[] trs = nodeTrs(node);
                builder.joint(key, parentKey, MinimalJsonParser.asString(node.get("name"), "node" + key), trs, 0);
                nodeToPreSortSlot.put(key, builder.slot(key));
                parentKey = key;
            }
        }

        private float[] readAccessorMat4(int index, int expectedCount) {
            List<Object> accessors = list("accessors");
            if (index < 0 || index >= accessors.size()) {
                return null;
            }
            Map<String, Object> accessor = MinimalJsonParser.asMap(accessors.get(index));
            int count = (int) MinimalJsonParser.asDouble(accessor.get("count"), 0);
            if (count < expectedCount) {
                return null;
            }
            View view = bufferView(accessor);
            if (view == null) {
                return new float[count * 16];
            }
            int base = view.offset + (int) MinimalJsonParser.asDouble(accessor.get("byteOffset"), 0);
            int stride = view.stride > 0 ? view.stride : 64;
            ByteBuffer data = view.data;
            if (!fitsInBuffer(base, count, stride, 64, data.limit())) {
                return null;
            }
            float[] out = new float[count * 16];
            for (int i = 0; i < count; i++) {
                int element = base + i * stride;
                for (int c = 0; c < 16; c++) {
                    out[i * 16 + c] = data.getFloat(element + c * 4);
                }
            }
            return out;
        }

        private void visitNodeAnimated(List<Object> nodes, int index, Mat4 world, int depth) {
            if (depth > MAX_NODE_DEPTH || index < 0 || index >= nodes.size()) {
                return;
            }
            Map<String, Object> node = MinimalJsonParser.asMap(nodes.get(index));
            Mat4 childWorld = world.multiply(localTransform(node));
            if (node.containsKey("mesh")) {
                Integer skinIndex = nodeSkin.get(index);
                int rigidSlot = rigidSlotOfNode.getOrDefault(index, -1);

                boolean posed = skinIndex != null || rigidSlot >= 0;
                readMeshAnimated((int) MinimalJsonParser.asDouble(node.get("mesh"), -1),
                        posed ? Mat4.identity() : childWorld, skinIndex, rigidSlot);
            }
            for (Object child : MinimalJsonParser.asList(node.get("children"))) {
                visitNodeAnimated(nodes, (int) MinimalJsonParser.asDouble(child, -1), childWorld, depth + 1);
            }
        }

        private void readMeshAnimated(int meshIndex, Mat4 world, Integer skinIndex, int rigidSlot) {
            List<Object> meshes = list("meshes");
            if (meshIndex < 0 || meshIndex >= meshes.size()) {
                return;
            }
            Map<String, Object> mesh = MinimalJsonParser.asMap(meshes.get(meshIndex));
            int[] slots = skinIndex != null ? skinJointSlots.get(skinIndex) : null;
            for (Object primitiveObj : MinimalJsonParser.asList(mesh.get("primitives"))) {
                readPrimitiveAnimated(MinimalJsonParser.asMap(primitiveObj), world, slots, rigidSlot);
            }
        }

        private void readPrimitiveAnimated(Map<String, Object> primitive, Mat4 world, int[] jointSlots, int rigidSlot) {
            int mode = (int) MinimalJsonParser.asDouble(primitive.get("mode"), 4);
            if (mode != 4) {
                return;
            }
            Map<String, Object> attributes = MinimalJsonParser.asMap(primitive.get("attributes"));
            if (!attributes.containsKey("POSITION")) {
                return;
            }
            float[] positions = readAccessor((int) MinimalJsonParser.asDouble(attributes.get("POSITION"), -1), 3);
            if (positions == null) {
                return;
            }
            int vertexCount = positions.length / 3;
            float[] normals = attributeOf(attributes, "NORMAL", 3, vertexCount);
            float[] uvs = attributeOf(attributes, "TEXCOORD_0", 2, vertexCount);
            float[] joints = jointSlots != null ? attributeOf(attributes, "JOINTS_0", 4, vertexCount) : null;
            float[] weights = jointSlots != null ? attributeOf(attributes, "WEIGHTS_0", 4, vertexCount) : null;

            int[] indices = primitive.containsKey("indices")
                    ? readIndices((int) MinimalJsonParser.asDouble(primitive.get("indices"), -1))
                    : sequence(vertexCount);
            if (indices == null) {
                return;
            }

            for (int i = 0; i + 2 < indices.length; i += 3) {
                SkinnedVertex[] corner = new SkinnedVertex[3];
                boolean ok = true;
                for (int k = 0; k < 3 && ok; k++) {
                    ok = fillSkinnedCorner(corner, k, indices[i + k], vertexCount, positions, normals, uvs, joints,
                            weights, jointSlots, world, rigidSlot);
                }
                if (!ok) {
                    continue;
                }
                if (normals == null) {
                    Vec3 faceNormal = faceNormal(corner[0].position(), corner[1].position(), corner[2].position());
                    corner[0] = withSkinnedNormal(corner[0], faceNormal);
                    corner[1] = withSkinnedNormal(corner[1], faceNormal);
                    corner[2] = withSkinnedNormal(corner[2], faceNormal);
                }
                skinnedTriangles.add(new SkinnedTriangle(corner[0], corner[1], corner[2]));
            }
        }

        private boolean fillSkinnedCorner(SkinnedVertex[] corner, int slot, int vertex, int vertexCount,
                                          float[] positions, float[] normals, float[] uvs, float[] joints,
                                          float[] weights, int[] jointSlots, Mat4 world, int rigidSlot) {
            if (vertex < 0 || vertex >= vertexCount) {
                return false;
            }
            Vec3 localPos = new Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2]);
            Vec3 localNormal = normals != null
                    ? new Vec3(normals[vertex * 3], normals[vertex * 3 + 1], normals[vertex * 3 + 2])
                    : Vec3.zero();
            float u = uvs == null ? 0f : uvs[vertex * 2];
            float v = uvs == null ? 0f : uvs[vertex * 2 + 1];

            int[] outJoints = new int[4];
            float[] outWeights = new float[4];
            if (jointSlots != null && joints != null && weights != null) {
                for (int c = 0; c < 4; c++) {
                    int raw = Math.round(joints[vertex * 4 + c]);
                    outJoints[c] = raw >= 0 && raw < jointSlots.length ? jointSlots[raw] : -1;
                    outWeights[c] = weights[vertex * 4 + c];
                }
            } else if (rigidSlot >= 0) {
                outJoints[0] = rigidSlot;
                outWeights[0] = 1f;
            } else {
                localPos = world.transformPosition(localPos);
                if (normals != null) {
                    localNormal = world.transformDirection(localNormal);
                }
            }
            Vec3 normal = isFiniteNonZero(localNormal) ? localNormal.normalize() : new Vec3(0, 1, 0);
            corner[slot] = new SkinnedVertex(localPos, normal, u, flipV ? 1f - v : v, outJoints, outWeights);
            return true;
        }

        private List<AnimationClip> readAnimations(List<Object> nodes) {
            List<AnimationClip> clips = new ArrayList<>();
            List<Object> animations = list("animations");
            for (int a = 0; a < animations.size(); a++) {
                Map<String, Object> animation = MinimalJsonParser.asMap(animations.get(a));
                List<Object> samplers = MinimalJsonParser.asList(animation.get("samplers"));
                List<AnimationClip.Channel> channels = new ArrayList<>();
                for (Object channelObj : MinimalJsonParser.asList(animation.get("channels"))) {
                    Map<String, Object> channel = MinimalJsonParser.asMap(channelObj);
                    Map<String, Object> target = MinimalJsonParser.asMap(channel.get("target"));
                    if (!target.containsKey("node")) {
                        continue;
                    }
                    int nodeIndex = (int) MinimalJsonParser.asDouble(target.get("node"), -1);
                    AnimationClip.Path path = switch (MinimalJsonParser.asString(target.get("path"), "")) {
                        case "translation" -> AnimationClip.Path.TRANSLATION;
                        case "rotation" -> AnimationClip.Path.ROTATION;
                        case "scale" -> AnimationClip.Path.SCALE;
                        default -> null;
                    };
                    if (path == null || nodeIndex < 0) {
                        continue;
                    }
                    int samplerIndex = (int) MinimalJsonParser.asDouble(channel.get("sampler"), -1);
                    if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                        continue;
                    }
                    Map<String, Object> sampler = MinimalJsonParser.asMap(samplers.get(samplerIndex));
                    float[] times = readAccessor((int) MinimalJsonParser.asDouble(sampler.get("input"), -1), 1);
                    float[] values = readAccessor((int) MinimalJsonParser.asDouble(sampler.get("output"), -1),
                            path.components);
                    if (times == null || values == null) {
                        continue;
                    }
                    AnimationClip.Interpolation interpolation = switch (
                            MinimalJsonParser.asString(sampler.get("interpolation"), "LINEAR")) {
                        case "STEP" -> AnimationClip.Interpolation.STEP;
                        case "CUBICSPLINE" -> AnimationClip.Interpolation.CUBICSPLINE;
                        default -> AnimationClip.Interpolation.LINEAR;
                    };
                    int slot = nodeToSkeletonSlot.getOrDefault(nodeIndex, -1);
                    if (slot < 0) {
                        continue;
                    }
                    channels.add(new AnimationClip.Channel(slot, path, interpolation, times, values));
                }
                if (!channels.isEmpty()) {
                    clips.add(new AnimationClip(MinimalJsonParser.asString(animation.get("name"), "clip" + a), channels));
                }
            }
            return clips;
        }

        private record View(ByteBuffer data, int offset, int stride) {
        }
    }
}

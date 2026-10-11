package net.renan.photonplugin.menus.resource.preview.neo.model.mesh;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MeshSampler {
    public static final MeshSampler EMPTY = new MeshSampler(List.of(), List.of(), List.of());
    private final List<Vec3> vertices;
    private final List<Edge> edges;
    private final List<Triangle> triangles;
    private final double edgeSumLength;
    private final double triangleSumArea;

    private MeshSampler(List<Vec3> vertices, List<Edge> edges, List<Triangle> triangles) {
        this.vertices = vertices;
        this.edges = edges;
        this.triangles = triangles;
        double lengthSum = 0;
        for (Edge edge : edges) {
            lengthSum += edge.length();
        }
        this.edgeSumLength = lengthSum;
        double areaSum = 0;
        for (Triangle triangle : triangles) {
            areaSum += triangle.area();
        }
        this.triangleSumArea = areaSum;
    }

    public static MeshSampler of(ObjMesh mesh) {
        if (mesh.isEmpty()) {
            return EMPTY;
        }
        Map<Long, Vec3> vertexByKey = new LinkedHashMap<>();
        Map<Long, Edge> edgeByKey = new LinkedHashMap<>();
        List<Triangle> triangles = new ArrayList<>(mesh.triangles().size());
        for (MeshTriangle tri : mesh.triangles()) {
            Vec3 a = tri.a().position();
            Vec3 b = tri.b().position();
            Vec3 c = tri.c().position();
            addVertex(vertexByKey, a);
            addVertex(vertexByKey, b);
            addVertex(vertexByKey, c);
            addEdge(edgeByKey, a, b);
            addEdge(edgeByKey, b, c);
            addEdge(edgeByKey, c, a);
            double area = triangleArea(a, b, c);
            if (area > 0) {
                triangles.add(new Triangle(a, b, c, area));
            }
        }
        return new MeshSampler(new ArrayList<>(vertexByKey.values()), new ArrayList<>(edgeByKey.values()), triangles);
    }

    private static void addVertex(Map<Long, Vec3> byKey, Vec3 v) {
        byKey.putIfAbsent(vertexKey(v), v);
    }

    private static void addEdge(Map<Long, Edge> byKey, Vec3 a, Vec3 b) {
        double length = new Vec3(a).sub(b).length();
        if (length <= 0) {
            return;
        }
        long ka = vertexKey(a);
        long kb = vertexKey(b);
        long key = ka < kb ? (ka * 1_000_003L) ^ kb : (kb * 1_000_003L) ^ ka;
        byKey.putIfAbsent(key, new Edge(a, b, length));
    }

    private static double triangleArea(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 cross = Vec3.cross(new Vec3(b).sub(a), new Vec3(c).sub(a));
        return 0.5 * cross.length();
    }

    private static long vertexKey(Vec3 v) {
        int qx = Math.round(v.x * 4096f);
        int qy = Math.round(v.y * 4096f);
        int qz = Math.round(v.z * 4096f);
        return (((long) qx & 0x1FFFFF) << 42) | (((long) qy & 0x1FFFFF) << 21) | ((long) qz & 0x1FFFFF);
    }

    public boolean isEmpty() {
        return vertices.isEmpty();
    }

    public Vec3 randomVertex(float t) {
        if (vertices.isEmpty()) {
            return null;
        }
        int index = Math.min(vertices.size() - 1, (int) (vertices.size() * t));
        return vertices.get(index);
    }

    public Edge randomEdge(float t) {
        if (edges.isEmpty()) {
            return null;
        }
        double target = t * edgeSumLength;
        double cumulative = 0;
        for (Edge edge : edges) {
            if (target <= edge.length() + cumulative) {
                return edge;
            }
            cumulative += edge.length();
        }
        return edges.get(edges.size() - 1);
    }

    public Triangle randomTriangle(float t) {
        if (triangles.isEmpty()) {
            return null;
        }
        double target = t * triangleSumArea;
        double cumulative = 0;
        for (Triangle triangle : triangles) {
            if (target <= triangle.area() + cumulative) {
                return triangle;
            }
            cumulative += triangle.area();
        }
        return triangles.get(triangles.size() - 1);
    }

    public record Edge(Vec3 a, Vec3 b, double length) {
    }

    public record Triangle(Vec3 a, Vec3 b, Vec3 c, double area) {
    }
}

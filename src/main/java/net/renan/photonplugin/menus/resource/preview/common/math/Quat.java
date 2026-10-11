package net.renan.photonplugin.menus.resource.preview.common.math;

public final class Quat {
    public float x, y, z, w;

    public Quat(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public static Quat identity() {
        return new Quat(0, 0, 0, 1);
    }

    public static Quat fromAxisAngle(Vec3 axis, float angleRad) {
        Vec3 n = new Vec3(axis).normalize();
        float half = angleRad * 0.5f;
        float s = (float) Math.sin(half);
        return new Quat(n.x * s, n.y * s, n.z * s, (float) Math.cos(half));
    }

    public static Quat rotationX(float a) {
        return new Quat((float) Math.sin(a * 0.5), 0, 0, (float) Math.cos(a * 0.5));
    }

    public static Quat rotationY(float a) {
        return new Quat(0, (float) Math.sin(a * 0.5), 0, (float) Math.cos(a * 0.5));
    }

    public static Quat rotationZ(float a) {
        return new Quat(0, 0, (float) Math.sin(a * 0.5), (float) Math.cos(a * 0.5));
    }

    public static Quat euler(Vec3 rotation) {
        return rotationY(rotation.y).multiply(rotationX(rotation.x)).multiply(rotationZ(rotation.z));
    }

    public static Quat rotationXYZ(float angleX, float angleY, float angleZ) {
        return rotationX(angleX).multiply(rotationY(angleY)).multiply(rotationZ(angleZ));
    }

    public static Quat rotationYXZ(float angleY, float angleX, float angleZ) {
        return rotationY(angleY).multiply(rotationX(angleX)).multiply(rotationZ(angleZ));
    }

    public static Quat fromBasis(Vec3 ax, Vec3 ay, Vec3 az) {
        float m00 = ax.x, m10 = ax.y, m20 = ax.z;
        float m01 = ay.x, m11 = ay.y, m21 = ay.z;
        float m02 = az.x, m12 = az.y, m22 = az.z;
        float tr = m00 + m11 + m22;
        float qx, qy, qz, qw;
        if (tr >= 0f) {
            float t = (float) Math.sqrt(tr + 1f);
            qw = t * 0.5f;
            t = 0.5f / t;
            qx = (m21 - m12) * t;
            qy = (m02 - m20) * t;
            qz = (m10 - m01) * t;
        } else if (m00 >= m11 && m00 >= m22) {
            float t = (float) Math.sqrt(1f + m00 - m11 - m22);
            qx = t * 0.5f;
            t = 0.5f / t;
            qy = (m10 + m01) * t;
            qz = (m02 + m20) * t;
            qw = (m21 - m12) * t;
        } else if (m11 > m22) {
            float t = (float) Math.sqrt(1f + m11 - m00 - m22);
            qy = t * 0.5f;
            t = 0.5f / t;
            qx = (m10 + m01) * t;
            qz = (m21 + m12) * t;
            qw = (m02 - m20) * t;
        } else {
            float t = (float) Math.sqrt(1f + m22 - m00 - m11);
            qz = t * 0.5f;
            t = 0.5f / t;
            qx = (m02 + m20) * t;
            qy = (m21 + m12) * t;
            qw = (m10 - m01) * t;
        }
        return new Quat(qx, qy, qz, qw).normalize();
    }

    public static Quat rotationTo(Vec3 from, Vec3 to) {
        Vec3 f = new Vec3(from).normalize();
        Vec3 t = new Vec3(to).normalize();
        float dot = f.dot(t);
        if (dot < -1f + 1e-6f) {
            float x = f.y, y = -f.x, z = 0f;
            if (x * x + y * y == 0f) {
                x = 0f;
                y = f.z;
                z = -f.y;
            }
            return new Quat(x, y, z, 0f).normalize();
        }
        float sd2 = (float) Math.sqrt((1f + dot) * 2f);
        float isd2 = 1f / sd2;
        float cx = f.y * t.z - f.z * t.y;
        float cy = f.z * t.x - f.x * t.z;
        float cz = f.x * t.y - f.y * t.x;
        return new Quat(cx * isd2, cy * isd2, cz * isd2, sd2 * 0.5f).normalize();
    }

    public Quat normalize() {
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len > 1e-8f) {
            x /= len;
            y /= len;
            z /= len;
            w /= len;
        }
        return this;
    }

    public Quat conjugate() {
        return new Quat(-x, -y, -z, w);
    }

    public Quat multiply(Quat o) {
        return new Quat(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z);
    }

    public Vec3 rotate(Vec3 v) {
        float tx = 2 * (y * v.z - z * v.y);
        float ty = 2 * (z * v.x - x * v.z);
        float tz = 2 * (x * v.y - y * v.x);
        return new Vec3(
                v.x + w * tx + (y * tz - z * ty),
                v.y + w * ty + (z * tx - x * tz),
                v.z + w * tz + (x * ty - y * tx));
    }

    public Quat copy() {
        return new Quat(x, y, z, w);
    }

    public Vec3 rotateInverse(Vec3 v) {
        return conjugate().rotate(v);
    }
}

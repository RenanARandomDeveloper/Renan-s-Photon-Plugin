package net.renan.photonplugin.menus.resource.preview.common.math;

public final class Mat4 {
    public final float[] m = new float[16];

    private Mat4() {
    }

    public static Mat4 identity() {
        Mat4 r = new Mat4();
        r.m[0] = 1;
        r.m[5] = 1;
        r.m[10] = 1;
        r.m[15] = 1;
        return r;
    }

    public static Mat4 fromColumnMajor(float[] values) {
        Mat4 r = new Mat4();
        System.arraycopy(values, 0, r.m, 0, 16);
        return r;
    }

    public static Mat4 fromTRS(Vec3 t, Quat r, Vec3 s) {
        float x = r.x, y = r.y, z = r.z, w = r.w;
        float x2 = x + x, y2 = y + y, z2 = z + z;
        float xx = x * x2, xy = x * y2, xz = x * z2;
        float yy = y * y2, yz = y * z2, zz = z * z2;
        float wx = w * x2, wy = w * y2, wz = w * z2;

        Mat4 mat = new Mat4();
        mat.m[0] = (1 - (yy + zz)) * s.x;
        mat.m[1] = (xy + wz) * s.x;
        mat.m[2] = (xz - wy) * s.x;
        mat.m[3] = 0;

        mat.m[4] = (xy - wz) * s.y;
        mat.m[5] = (1 - (xx + zz)) * s.y;
        mat.m[6] = (yz + wx) * s.y;
        mat.m[7] = 0;

        mat.m[8] = (xz + wy) * s.z;
        mat.m[9] = (yz - wx) * s.z;
        mat.m[10] = (1 - (xx + yy)) * s.z;
        mat.m[11] = 0;

        mat.m[12] = t.x;
        mat.m[13] = t.y;
        mat.m[14] = t.z;
        mat.m[15] = 1;
        return mat;
    }

    public static Vec3 transformDirection3x3(float[] m3, Vec3 v) {
        return new Vec3(
                m3[0] * v.x + m3[3] * v.y + m3[6] * v.z,
                m3[1] * v.x + m3[4] * v.y + m3[7] * v.z,
                m3[2] * v.x + m3[5] * v.y + m3[8] * v.z);
    }

    public Mat4 multiply(Mat4 o) {
        Mat4 r = new Mat4();
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += m[k * 4 + row] * o.m[col * 4 + k];
                }
                r.m[col * 4 + row] = sum;
            }
        }
        return r;
    }

    public Vec3 transformDirection(Vec3 v) {
        float x = m[0] * v.x + m[4] * v.y + m[8] * v.z;
        float y = m[1] * v.x + m[5] * v.y + m[9] * v.z;
        float z = m[2] * v.x + m[6] * v.y + m[10] * v.z;
        return new Vec3(x, y, z);
    }

    public Mat4 invert() {
        float[] a = m;
        float[] inv = new float[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        float det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        if (Math.abs(det) < 1e-12f) {
            return identity();
        }
        float invDet = 1f / det;
        Mat4 r = new Mat4();
        for (int i = 0; i < 16; i++) {
            r.m[i] = inv[i] * invDet;
        }
        return r;
    }

    public Vec3 transformPosition(Vec3 v) {
        float x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12];
        float y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13];
        float z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14];
        return new Vec3(x, y, z);
    }

    public float determinant3x3() {
        float a = m[0], b = m[4], c = m[8];
        float d = m[1], e = m[5], f = m[9];
        float g = m[2], h = m[6], i = m[10];
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    }

    public float[] normalMatrix3x3() {
        float a = m[0], b = m[4], c = m[8];
        float d = m[1], e = m[5], f = m[9];
        float g = m[2], h = m[6], i = m[10];

        float c00 = e * i - f * h, c01 = f * g - d * i, c02 = d * h - e * g;
        float c10 = c * h - b * i, c11 = a * i - c * g, c12 = b * g - a * h;
        float c20 = b * f - c * e, c21 = c * d - a * f, c22 = a * e - b * d;

        float det = a * c00 + b * c01 + c * c02;
        if (!Float.isFinite(det) || Math.abs(det) < 1.0e-12f) {
            return null;
        }
        float invDet = 1f / det;

        return new float[]{
                c00 * invDet, c10 * invDet, c20 * invDet,
                c01 * invDet, c11 * invDet, c21 * invDet,
                c02 * invDet, c12 * invDet, c22 * invDet};
    }
}

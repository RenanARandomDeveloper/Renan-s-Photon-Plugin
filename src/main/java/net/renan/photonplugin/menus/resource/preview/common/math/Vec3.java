package net.renan.photonplugin.menus.resource.preview.common.math;

public final class Vec3 {
    public float x, y, z;

    public Vec3() {
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public static Vec3 zero() {
        return new Vec3(0, 0, 0);
    }

    public static Vec3 cross(Vec3 a, Vec3 b) {
        return a.copy().cross(b);
    }

    public Vec3 set(float nx, float ny, float nz) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
        return this;
    }

    public Vec3 set(Vec3 other) {
        return set(other.x, other.y, other.z);
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }

    public Vec3 add(Vec3 other) {
        x += other.x;
        y += other.y;
        z += other.z;
        return this;
    }

    public Vec3 add(float ox, float oy, float oz) {
        x += ox;
        y += oy;
        z += oz;
        return this;
    }

    public Vec3 sub(Vec3 other) {
        x -= other.x;
        y -= other.y;
        z -= other.z;
        return this;
    }

    public Vec3 mul(float s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }

    public Vec3 mul(Vec3 other) {
        x *= other.x;
        y *= other.y;
        z *= other.z;
        return this;
    }

    public Vec3 mul(float sx, float sy, float sz) {
        x *= sx;
        y *= sy;
        z *= sz;
        return this;
    }

    public Vec3 negate() {
        x = -x;
        y = -y;
        z = -z;
        return this;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vec3 normalize() {
        float len = length();
        if (len > 1e-8f) {
            mul(1f / len);
        }
        return this;
    }

    public Vec3 normalize(float toLength) {
        float len = length();
        if (len > 1e-8f) {
            mul(toLength / len);
        } else {
            set(0, 0, 0);
        }
        return this;
    }

    public float dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        float nx = y * other.z - z * other.y;
        float ny = z * other.x - x * other.z;
        float nz = x * other.y - y * other.x;
        return set(nx, ny, nz);
    }

    public Vec3 lerp(Vec3 target, float t) {
        x += (target.x - x) * t;
        y += (target.y - y) * t;
        z += (target.z - z) * t;
        return this;
    }

    public Vec3 project(Vec3 axis) {
        float denom = axis.lengthSquared();
        if (denom < 1e-12f) {
            return set(0, 0, 0);
        }
        float scale = dot(axis) / denom;
        return set(axis.x * scale, axis.y * scale, axis.z * scale);
    }

    public boolean isZero() {
        return Math.abs(x) < 1e-8f && Math.abs(y) < 1e-8f && Math.abs(z) < 1e-8f;
    }

    public Vec3 rotateAroundAxis(Vec3 axis, float angleRad) {
        if (angleRad == 0f) {
            return this;
        }
        float axLen = axis.length();
        if (axLen < 1e-8f) {
            return this;
        }
        float ax = axis.x / axLen, ay = axis.y / axLen, az = axis.z / axLen;
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float dotAV = x * ax + y * ay + z * az;
        float crossX = ay * z - az * y;
        float crossY = az * x - ax * z;
        float crossZ = ax * y - ay * x;
        float nx = x * cos + crossX * sin + ax * dotAV * (1 - cos);
        float ny = y * cos + crossY * sin + ay * dotAV * (1 - cos);
        float nz = z * cos + crossZ * sin + az * dotAV * (1 - cos);
        return set(nx, ny, nz);
    }

    public Vec3 rotateX(float angleRad) {
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float ny = y * cos - z * sin;
        float nz = y * sin + z * cos;
        return set(x, ny, nz);
    }

    public Vec3 rotateY(float angleRad) {
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float nx = x * cos + z * sin;
        float nz = -x * sin + z * cos;
        return set(nx, y, nz);
    }

    public Vec3 rotateZ(float angleRad) {
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float nx = x * cos - y * sin;
        float ny = x * sin + y * cos;
        return set(nx, ny, z);
    }

    public Vec3 rotateYXYDegrees(Vec3 rotationDegrees) {
        if (rotationDegrees == null || rotationDegrees.isZero()) {
            return this;
        }

        float toRad = (float) (Math.PI / 180.0);
        rotateY(rotationDegrees.y * toRad);
        rotateX(rotationDegrees.x * toRad);
        rotateY(rotationDegrees.z * toRad);
        return this;
    }

    public Vec3 rotateYXZDegrees(Vec3 rotationDegrees) {
        if (rotationDegrees == null || rotationDegrees.isZero()) {
            return this;
        }
        float toRad = (float) (Math.PI / 180.0);
        rotateY(rotationDegrees.y * toRad);
        rotateX(rotationDegrees.x * toRad);
        rotateZ(rotationDegrees.z * toRad);
        return this;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}

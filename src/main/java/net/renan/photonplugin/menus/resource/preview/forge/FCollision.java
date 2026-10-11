package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;

final class FCollision {
    private static final double EPSILON = 1.0E-7;
    private final Box floor;

    FCollision(Box floor) {
        this.floor = floor;
    }

    Vec3 collide(Vec3 move, Box body) {
        double dx = move.x;
        double dy = move.y;
        double dz = move.z;
        Box bb = body;
        if (dy != 0.0) {
            dy = clip(Axis.Y, bb, dy);
            if (dy != 0.0) {
                bb = bb.move(0, dy, 0);
            }
        }
        boolean xSmaller = Math.abs(dx) < Math.abs(dz);
        if (xSmaller && dz != 0.0) {
            dz = clip(Axis.Z, bb, dz);
            if (dz != 0.0) {
                bb = bb.move(0, 0, dz);
            }
        }
        if (dx != 0.0) {
            dx = clip(Axis.X, bb, dx);
            if (!xSmaller && dx != 0.0) {
                bb = bb.move(dx, 0, 0);
            }
        }
        if (!xSmaller && dz != 0.0) {
            dz = clip(Axis.Z, bb, dz);
        }
        return new Vec3((float) dx, (float) dy, (float) dz);
    }

    private double clip(Axis axis, Box m, double distance) {
        if (Math.abs(distance) < EPSILON) {
            return 0.0;
        }

        boolean overlapX = axis == Axis.X || (m.maxX > floor.minX + EPSILON && m.minX < floor.maxX - EPSILON);
        boolean overlapY = axis == Axis.Y || (m.maxY > floor.minY + EPSILON && m.minY < floor.maxY - EPSILON);
        boolean overlapZ = axis == Axis.Z || (m.maxZ > floor.minZ + EPSILON && m.minZ < floor.maxZ - EPSILON);
        if (!(overlapX && overlapY && overlapZ)) {
            return distance;
        }
        double fMin, fMax, mMin, mMax;
        switch (axis) {
            case X -> {
                fMin = floor.minX;
                fMax = floor.maxX;
                mMin = m.minX;
                mMax = m.maxX;
            }
            case Y -> {
                fMin = floor.minY;
                fMax = floor.maxY;
                mMin = m.minY;
                mMax = m.maxY;
            }
            default -> {
                fMin = floor.minZ;
                fMax = floor.maxZ;
                mMin = m.minZ;
                mMax = m.maxZ;
            }
        }
        if (distance > 0.0) {
            double gap = fMin - mMax;
            if (gap >= -EPSILON) {
                distance = Math.min(gap, distance);
            }
        } else {
            double gap = fMax - mMin;
            if (gap <= EPSILON) {
                distance = Math.max(gap, distance);
            }
        }
        return distance;
    }

    private enum Axis {X, Y, Z}

    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Box move(double dx, double dy, double dz) {
            return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
        }
    }
}

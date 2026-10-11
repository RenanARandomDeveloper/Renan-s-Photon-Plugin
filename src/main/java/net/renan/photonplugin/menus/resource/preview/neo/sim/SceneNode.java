package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Mat4;
import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.neo.model.Transform;

import java.util.ArrayList;
import java.util.List;

public final class SceneNode {
    public static final String ROOT_ID = "00000000-0000-0000-0000-000000000000";

    public final String id;
    private final Transform authored;
    private final Transform local;
    private final Transform world;
    private final List<SceneNode> children = new ArrayList<>();
    private SceneNode parent;
    private Mat4 localToWorld = Mat4.identity();
    private Mat4 worldToLocal = Mat4.identity();
    private boolean selfActive = true;
    private boolean selfVisible = true;
    private float selfTimeScale = 1f;

    public SceneNode(String id, Transform authored) {
        this.id = id;
        this.authored = authored;
        this.local = new Transform(new Vec3(authored.position()), authored.rotation().copy(), new Vec3(authored.scale()));
        this.world = new Transform(new Vec3(authored.position()), authored.rotation().copy(), new Vec3(authored.scale()));
    }

    private static void copyRotation(Quat from, Quat to) {
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.w = from.w;
    }

    public void setParent(SceneNode newParent) {
        this.parent = newParent;
        if (newParent != null) {
            newParent.children.add(this);
        }
    }

    public SceneNode parent() {
        return parent;
    }

    public List<SceneNode> children() {
        return children;
    }

    public Transform local() {
        return local;
    }

    public Transform world() {
        return world;
    }

    public Mat4 localToWorld() {
        return localToWorld;
    }

    public Mat4 worldToLocal() {
        return worldToLocal;
    }

    public void restoreAuthoredTransform() {
        local.position().set(authored.position());
        copyRotation(authored.rotation(), local.rotation());
        local.scale().set(authored.scale());
    }

    public void setLocalPosition(float x, float y, float z) {
        local.position().set(x, y, z);
    }

    public void setLocalScale(float x, float y, float z) {
        local.scale().set(x, y, z);
    }

    public void setLocalRotation(Quat q) {
        copyRotation(q, local.rotation());
    }

    public void refresh() {
        Mat4 localMatrix = Mat4.fromTRS(local.position(), local.rotation(), local.scale());
        if (parent == null) {
            localToWorld = localMatrix;
            world.position().set(local.position());
            copyRotation(local.rotation(), world.rotation());
            world.scale().set(local.scale());
        } else {
            localToWorld = parent.localToWorld.multiply(localMatrix);
            world.position().set(parent.localToWorld.transformPosition(local.position()));
            copyRotation(parent.world.rotation().multiply(local.rotation()).normalize(), world.rotation());
            world.scale().set(new Vec3(local.scale()).mul(parent.world.scale()));
        }
        worldToLocal = localToWorld.invert();
        for (SceneNode child : children) {
            child.refresh();
        }
    }

    public boolean selfActive() {
        return selfActive;
    }

    public void setSelfActive(boolean value) {
        this.selfActive = value;
    }

    public void setSelfVisible(boolean value) {
        this.selfVisible = value;
    }

    public void setSelfTimeScale(float value) {
        this.selfTimeScale = Math.max(0f, value);
    }

    public void resetTimelineState() {
        selfActive = true;
        selfVisible = true;
        selfTimeScale = 1f;
    }

    public boolean isActive() {
        return selfActive && (parent == null || parent.isActive());
    }

    public boolean isVisible() {
        return selfVisible && (parent == null || parent.isVisible());
    }

    public float timeScale() {
        return selfTimeScale * (parent == null ? 1f : parent.timeScale());
    }
}

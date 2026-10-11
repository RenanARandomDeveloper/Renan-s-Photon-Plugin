package net.renan.photonplugin.menus.resource.preview.neo.model.timeline;

import java.util.ArrayList;
import java.util.List;

public final class Track {
    public final Kind kind;
    public final String targetId;
    public final boolean mute;
    public final List<Clip> clips = new ArrayList<>();
    public final List<AnimatedProperty> properties = new ArrayList<>();
    public final List<Track> children = new ArrayList<>();

    public Track(Kind kind, String targetId, boolean mute) {
        this.kind = kind;
        this.targetId = targetId;
        this.mute = mute;
    }

    public double contentEnd() {
        double end = 0;
        for (Clip clip : clips) {
            end = Math.max(end, clip.end());
        }
        for (AnimatedProperty property : properties) {
            end = Math.max(end, property.lastKeyTime());
        }
        return end;
    }

    public Clip clipAt(double time) {
        for (Clip clip : clips) {
            if (clip.contains(time)) {
                return clip;
            }
        }
        return null;
    }

    public Clip clipForObjectAt(String objectId, double time) {
        for (Clip clip : clips) {
            if (objectId.equals(clip.targetId()) && clip.contains(time)) {
                return clip;
            }
        }
        return null;
    }

    public float sampleSpeed(double time) {
        if (properties.isEmpty()) {
            return 1f;
        }
        return Math.max(0f, properties.get(0).sample((float) time)[0]);
    }

    public enum Kind {ACTIVATOR, CONTROL, ANIMATION, SPEED, GROUP, OTHER}
}

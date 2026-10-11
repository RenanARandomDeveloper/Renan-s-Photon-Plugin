package net.renan.photonplugin.menus.resource.preview.neo.sim;

import net.renan.photonplugin.menus.resource.preview.common.math.Quat;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.AnimatedProperty;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Clip;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Timeline;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Track;

import java.util.*;

final class TimelineSim {
    private static final float DEG_TO_RAD = (float) Math.toRadians(1);

    private final FxSceneSimulation scene;
    private final Timeline timeline;
    private final double duration;
    private final Map<String, Clip> lastControlClip = new HashMap<>();
    private Set<String> lastControlled = new HashSet<>();
    private Set<String> lastSpeedControlled = new HashSet<>();
    private double localTime;

    TimelineSim(FxSceneSimulation scene, Timeline timeline) {
        this.scene = scene;
        this.timeline = timeline;
        this.duration = timeline.duration();
    }

    private static Set<String> controlledIds(List<Track> leaves) {
        Set<String> ids = new HashSet<>();
        for (Track track : leaves) {
            if (track.kind == Track.Kind.ACTIVATOR) {
                if (track.targetId != null) {
                    ids.add(track.targetId);
                }
            } else if (track.kind == Track.Kind.CONTROL) {
                for (Clip clip : track.clips) {
                    if (clip.targetId() != null) {
                        ids.add(clip.targetId());
                    }
                }
            }
        }
        return ids;
    }

    boolean isEmpty() {
        return timeline.isEmpty();
    }

    boolean isFinished() {
        return timeline.isEmpty() || localTime > duration;
    }

    void begin() {
        localTime = 0;
        evaluate(0);
    }

    void tick(float rate) {
        evaluate(localTime);
        localTime += Math.max(0f, rate);
    }

    private void evaluate(double time) {
        List<Track> leaves = timeline.leafTracks();
        Set<String> controlled = controlledIds(leaves);
        for (String id : lastControlled) {
            if (!controlled.contains(id)) {
                SceneNode node = scene.node(id);
                if (node != null) {
                    node.setSelfActive(true);
                    node.setSelfVisible(true);
                    lastControlClip.remove(id);
                }
            }
        }
        lastControlled = controlled;
        for (String id : controlled) {
            SceneNode node = scene.node(id);
            if (node == null) {
                continue;
            }
            boolean hasActivator = false;
            boolean activatorActive = false;
            boolean hasControl = false;
            Clip controlClip = null;
            for (Track track : leaves) {
                if (track.kind == Track.Kind.ACTIVATOR) {
                    if (id.equals(track.targetId)) {
                        hasActivator = true;
                        if (track.clipAt(time) != null) {
                            activatorActive = true;
                        }
                    }
                } else if (track.kind == Track.Kind.CONTROL) {
                    for (Clip clip : track.clips) {
                        if (id.equals(clip.targetId())) {
                            hasControl = true;
                            break;
                        }
                    }
                    if (controlClip == null) {
                        controlClip = track.clipForObjectAt(id, time);
                    }
                }
            }
            Clip previous = lastControlClip.get(id);
            if (controlClip != previous) {
                if (controlClip != null) {
                    long seed = controlClip.randomSeed() ? scene.random().nextLong() : controlClip.seed();
                    scene.resetSubtree(node, seed);
                }
                lastControlClip.put(id, controlClip);
            }
            boolean alive = hasControl ? controlClip != null : (!hasActivator || activatorActive);
            boolean visible = !hasActivator || activatorActive;
            node.setSelfActive(alive);
            node.setSelfVisible(alive && visible);
        }
        applyAnimations(leaves, time);
        applySpeed(leaves, time);
    }

    private void applyAnimations(List<Track> leaves, double time) {
        for (Track track : leaves) {
            if (track.kind != Track.Kind.ANIMATION || track.targetId == null) {
                continue;
            }
            SceneNode node = scene.node(track.targetId);
            if (node == null) {
                continue;
            }
            for (AnimatedProperty property : track.properties) {
                apply(node, property, (float) time);
            }
        }
    }

    private void apply(SceneNode node, AnimatedProperty property, float time) {
        switch (property.kind) {
            case POSITION -> {
                float[] v = property.sample(time);
                node.setLocalPosition(v[0], v[1], v[2]);
            }
            case SCALE -> {
                float[] v = property.sample(time);
                node.setLocalScale(v[0], v[1], v[2]);
            }
            case ROTATION -> {
                float[] v = property.sample(time);
                node.setLocalRotation(Quat.rotationXYZ(v[0] * DEG_TO_RAD, v[1] * DEG_TO_RAD, v[2] * DEG_TO_RAD).normalize());
            }
            case CONFIG -> scene.setOverride(node, property.path, property.configValue(time));
            case SPEED -> {
            }
        }
    }

    private void applySpeed(List<Track> leaves, double time) {
        Set<String> controlled = new HashSet<>();
        for (Track track : leaves) {
            if (track.kind != Track.Kind.SPEED || track.targetId == null) {
                continue;
            }
            SceneNode node = scene.node(track.targetId);
            if (node != null) {
                node.setSelfTimeScale(track.sampleSpeed(time));
                controlled.add(track.targetId);
            }
        }
        for (String id : lastSpeedControlled) {
            if (!controlled.contains(id)) {
                SceneNode node = scene.node(id);
                if (node != null) {
                    node.setSelfTimeScale(1f);
                }
            }
        }
        lastSpeedControlled = controlled;
    }
}

package net.renan.photonplugin.menus.resource.preview.neo.parse;

import net.renan.photonplugin.menus.resource.preview.common.math.CurveSegment;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.Gradient;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.model.shape.TimelineExpression;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.AnimatedProperty;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Clip;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Timeline;
import net.renan.photonplugin.menus.resource.preview.neo.model.timeline.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TimelineParser {
    private TimelineParser() {
    }

    public static Timeline parse(NbtTag.CompoundTag timelineTag) {
        Timeline timeline = new Timeline();
        NbtTag.ListTag tracks = timelineTag.getList("tracks");
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i) instanceof NbtTag.CompoundTag entry) {
                timeline.tracks.add(parseTrack(entry));
            }
        }
        return timeline;
    }

    private static Track parseTrack(NbtTag.CompoundTag entry) {
        String type = entry.getString("type", "");
        NbtTag.CompoundTag data = entry.getCompound("data");
        Track.Kind kind = switch (type) {
            case "activator" -> Track.Kind.ACTIVATOR;
            case "control" -> Track.Kind.CONTROL;
            case "animation" -> Track.Kind.ANIMATION;
            case "speed" -> Track.Kind.SPEED;
            case "group" -> Track.Kind.GROUP;
            default -> Track.Kind.OTHER;
        };
        Track track = new Track(kind, uuid(data.get("target")), data.getBoolean("mute", false));
        NbtTag.ListTag clips = data.getList("clips");
        for (int i = 0; i < clips.size(); i++) {
            if (clips.get(i) instanceof NbtTag.CompoundTag c) {
                track.clips.add(new Clip(c.getDouble("start", 0), c.getDouble("duration", 0), c.getFloat("speed", 1f),
                        uuid(c.get("clipTarget")), longOf(c.get("seed")), c.getBoolean("randomSeed", true)));
            }
        }
        if (kind == Track.Kind.ANIMATION || kind == Track.Kind.SPEED) {
            NbtTag.ListTag properties = data.getList("properties");
            for (int i = 0; i < properties.size(); i++) {
                if (properties.get(i) instanceof NbtTag.CompoundTag p) {
                    AnimatedProperty property = parseProperty(p);
                    if (property != null) {
                        track.properties.add(property);
                    }
                }
            }
        }
        if (kind == Track.Kind.GROUP) {
            NbtTag.ListTag children = data.getList("children");
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) instanceof NbtTag.CompoundTag c) {
                    track.children.add(parseTrack(c));
                }
            }
        }
        return track;
    }

    private static AnimatedProperty parseProperty(NbtTag.CompoundTag tag) {
        String type = tag.getString("type", "");
        AnimatedProperty.Kind kind;
        AnimatedProperty.ValueType valueType = AnimatedProperty.ValueType.FLOAT;
        String path = "";
        int channelCount;
        switch (type) {
            case "position" -> {
                kind = AnimatedProperty.Kind.POSITION;
                channelCount = 3;
            }
            case "rotation" -> {
                kind = AnimatedProperty.Kind.ROTATION;
                channelCount = 3;
            }
            case "scale" -> {
                kind = AnimatedProperty.Kind.SCALE;
                channelCount = 3;
            }
            case "speed" -> {
                kind = AnimatedProperty.Kind.SPEED;
                channelCount = 1;
            }
            case "config" -> {
                kind = AnimatedProperty.Kind.CONFIG;
                path = tag.getString("path", "");
                try {
                    valueType = AnimatedProperty.ValueType.valueOf(tag.getString("valueType", "FLOAT"));
                } catch (IllegalArgumentException e) {
                    return null;
                }
                channelCount = valueType.channelCount();
            }
            default -> {
                return null;
            }
        }

        List<List<CurveSegment>> channels = new ArrayList<>();
        NbtTag.ListTag channelTags = tag.getList("channels");
        for (int i = 0; i < channelCount; i++) {
            List<CurveSegment> segments = new ArrayList<>();
            if (i < channelTags.size() && channelTags.get(i) instanceof NbtTag.ListTag curve) {
                for (int s = 0; s < curve.size(); s++) {
                    if (curve.get(s) instanceof NbtTag.ListTag seg && seg.size() >= 8) {
                        segments.add(new CurveSegment(f(seg, 0), f(seg, 1), f(seg, 2), f(seg, 3), f(seg, 4), f(seg, 5),
                                f(seg, 6), f(seg, 7)));
                    }
                }
            }
            if (segments.isEmpty()) {
                segments.add(new CurveSegment(0, 0.5f, 0.1f, 0.5f, 0.9f, 0.5f, 1, 0.5f));
            }
            channels.add(segments);
        }

        List<List<AnimatedProperty.ExprClip>> exprClips = new ArrayList<>();
        NbtTag.ListTag exprAxes = tag.getList("exprClips");
        NbtTag.ListTag legacyModes = tag.getList("modes");
        NbtTag.ListTag legacyExprs = tag.getList("exprs");
        for (int axis = 0; axis < channelCount; axis++) {
            List<AnimatedProperty.ExprClip> list = new ArrayList<>();
            if (tag.get("exprClips") instanceof NbtTag.ListTag) {
                if (axis < exprAxes.size() && exprAxes.get(axis) instanceof NbtTag.ListTag clips) {
                    for (int i = 0; i < clips.size(); i++) {
                        if (clips.get(i) instanceof NbtTag.CompoundTag c) {
                            list.add(new AnimatedProperty.ExprClip(c.getDouble("start", 0), c.getDouble("duration", 0),
                                    TimelineExpression.parse(c.getString("expr", ""))));
                        }
                    }
                }
            } else if (axis < legacyModes.size() && legacyModes.get(axis) instanceof NbtTag.IntTag(
                    int value1
            ) && value1 == 1) {
                String expr = axis < legacyExprs.size() && legacyExprs.get(axis) instanceof NbtTag.StringTag(
                        String value
                ) ? value : "";
                list.add(new AnimatedProperty.ExprClip(0, 1e9, TimelineExpression.parse(expr)));
            }
            exprClips.add(list);
        }

        List<List<AnimatedProperty.CurveClip>> curveClips = new ArrayList<>();
        NbtTag.ListTag curveAxes = tag.getList("curveClips");
        for (int axis = 0; axis < channelCount; axis++) {
            List<AnimatedProperty.CurveClip> list = new ArrayList<>();
            if (axis < curveAxes.size() && curveAxes.get(axis) instanceof NbtTag.ListTag clips) {
                for (int i = 0; i < clips.size(); i++) {
                    if (clips.get(i) instanceof NbtTag.CompoundTag c && c.get("curve") != null) {
                        NumberFunction curve = NumberFunctionParser.parse(c.get("curve"), 0);
                        list.add(new AnimatedProperty.CurveClip(c.getDouble("start", 0), c.getDouble("duration", 0), curve));
                    }
                }
            }
            curveClips.add(list);
        }

        List<AnimatedProperty.ColorStop> stops = new ArrayList<>();
        List<AnimatedProperty.GradientClip> gradientClips = new ArrayList<>();
        if (kind == AnimatedProperty.Kind.CONFIG && valueType == AnimatedProperty.ValueType.COLOR) {
            NbtTag.ListTag stopTags = tag.getList("stops");
            for (int i = 0; i < stopTags.size(); i++) {
                if (stopTags.get(i) instanceof NbtTag.CompoundTag s) {
                    stops.add(new AnimatedProperty.ColorStop(s.getFloat("tick", 0), s.getInt("argb", -1), s.getFloat("intensity", 1f)));
                }
            }
            NbtTag.ListTag clipTags = tag.getList("gradientClips");
            for (int i = 0; i < clipTags.size(); i++) {
                if (clipTags.get(i) instanceof NbtTag.CompoundTag c && c.get("gradient") instanceof NbtTag.CompoundTag g) {
                    Gradient gradient = NumberFunctionParser.parseGradient(g);
                    gradientClips.add(new AnimatedProperty.GradientClip(c.getDouble("start", 0), c.getDouble("duration", 0), gradient));
                }
            }
        }

        boolean shortest = kind == AnimatedProperty.Kind.ROTATION && tag.getInt("interp", 0) == 1;
        boolean hdr = kind == AnimatedProperty.Kind.CONFIG && valueType == AnimatedProperty.ValueType.COLOR && tag.getBoolean("hdr", false);
        return new AnimatedProperty(kind, path, valueType, shortest, channels, exprClips, curveClips, stops, gradientClips, hdr);
    }

    private static float f(NbtTag.ListTag list, int index) {
        return switch (list.get(index)) {
            case NbtTag.FloatTag v -> v.value();
            case NbtTag.DoubleTag v -> (float) v.value();
            case NbtTag.IntTag v -> v.value();
            default -> 0f;
        };
    }

    private static long longOf(NbtTag tag) {
        return switch (tag) {
            case NbtTag.LongTag v -> v.value();
            case NbtTag.IntTag v -> v.value();
            case null, default -> 0L;
        };
    }

    static String uuid(NbtTag tag) {
        if (tag instanceof NbtTag.IntArrayTag(int[] a) && a.length >= 4) {
            long most = ((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL);
            long least = ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL);
            return new UUID(most, least).toString();
        }
        if (tag instanceof NbtTag.StringTag(String value)) {
            return value;
        }
        return null;
    }
}

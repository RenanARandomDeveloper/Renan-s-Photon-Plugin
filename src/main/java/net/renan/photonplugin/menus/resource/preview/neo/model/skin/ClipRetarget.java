package net.renan.photonplugin.menus.resource.preview.neo.model.skin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClipRetarget {
    private ClipRetarget() {
    }

    public static Result onto(Skeleton target, Skeleton source, List<AnimationClip> clips, String label) {
        Map<String, Integer> slotOfName = new HashMap<>();
        for (int joint = target.jointCount() - 1; joint >= 0; joint--) {
            slotOfName.put(target.name(joint), joint);
        }

        int[] remap = new int[source.jointCount()];
        int matched = 0;
        for (int joint = 0; joint < remap.length; joint++) {
            remap[joint] = slotOfName.getOrDefault(source.name(joint), -1);
            if (remap[joint] >= 0) {
                matched++;
            }
        }

        List<AnimationClip> out = new ArrayList<>();
        int dropped = 0;
        for (AnimationClip clip : clips) {
            List<AnimationClip.Channel> channels = new ArrayList<>();
            for (AnimationClip.Channel channel : clip.channels()) {
                int joint = channel.joint() >= 0 && channel.joint() < remap.length ? remap[channel.joint()] : -1;
                if (joint < 0) {
                    dropped++;
                    continue;
                }
                channels.add(new AnimationClip.Channel(joint, channel.path(), channel.interpolation(),
                        channel.times(), channel.values()));
            }
            if (channels.isEmpty()) {
                continue;
            }
            out.add(new AnimationClip(clips.size() == 1 ? label : label + "/" + clip.name(), channels));
        }
        return new Result(List.copyOf(out), matched, dropped);
    }

    public record Result(List<AnimationClip> clips, int matchedJoints, int droppedChannels) {
    }
}

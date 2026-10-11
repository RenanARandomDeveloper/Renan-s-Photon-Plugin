package net.renan.photonplugin.menus.resource.preview.neo.model.timeline;

import java.util.ArrayList;
import java.util.List;

public final class Timeline {
    public final List<Track> tracks = new ArrayList<>();

    private static void collect(List<Track> list, List<Track> out) {
        for (Track track : list) {
            if (track.kind == Track.Kind.GROUP) {
                if (!track.mute) {
                    collect(track.children, out);
                }
            } else if (!track.mute) {
                out.add(track);
            }
        }
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public List<Track> leafTracks() {
        List<Track> out = new ArrayList<>();
        collect(tracks, out);
        return out;
    }

    public double duration() {
        double duration = 0;
        for (Track track : leafTracks()) {
            duration = Math.max(duration, track.contentEnd());
        }
        return duration;
    }
}

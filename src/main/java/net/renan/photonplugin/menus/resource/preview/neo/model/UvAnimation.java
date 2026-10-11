package net.renan.photonplugin.menus.resource.preview.neo.model;

import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.neo.model.number.NumberFunction;
import net.renan.photonplugin.menus.resource.preview.neo.sim.MemRandom;

public record UvAnimation(boolean enable, int tilesX, int tilesY, Animation animation,
                          NumberFunction frameOverTime, NumberFunction startFrame, float cycle) {
    public UvRect getUvs(double t, MemRandom mem) {
        float cellU = 1f / tilesX;
        float cellV = 1f / tilesY;
        float currentFrame = (float) startFrame.get(t, mem.of("uv-startFrame"));
        currentFrame += cycle * (float) frameOverTime.get(t, mem.of("uv-frameOverTime"));
        float u0, v0;
        if (animation == Animation.WHOLE_SHEET) {
            int frame = Math.floorMod((int) currentFrame, Math.max(1, tilesX * tilesY));
            int x = frame % tilesX;
            int y = frame / tilesX;
            u0 = x * cellU;
            v0 = y * cellV;
        } else {
            int x = Math.floorMod((int) currentFrame, Math.max(1, tilesX));
            int y = (int) (mem.get("uv-randomRow") * tilesY);
            u0 = x * cellU;
            v0 = y * cellV;
        }
        return new UvRect(u0, v0, u0 + cellU, v0 + cellV);
    }

    public enum Animation {WHOLE_SHEET, SINGLE_ROW}
}

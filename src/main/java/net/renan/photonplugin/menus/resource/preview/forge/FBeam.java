package net.renan.photonplugin.menus.resource.preview.forge;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.IMaterial;
import net.renan.photonplugin.menus.resource.preview.common.model.UvRect;
import net.renan.photonplugin.menus.resource.preview.common.render.RenderItem;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeConfig.BeamCfg;

import java.util.Random;

final class FBeam extends FParticle {
    final FEmitter emitter;
    final BeamCfg config;

    float r = 1, g = 1, b = 1, a = 1;
    float ro = 1, go = 1, bo = 1, ao = 1;

    FBeam(FEmitter emitter, BeamCfg config, Random random) {
        super(random);
        this.emitter = emitter;
        this.config = config;
        delay = config.startDelay;
        update();
        updateOrigin();
    }

    float getT(float partial) {
        return emitter.getT(partial);
    }

    @Override
    void tick() {
        if (delay > 0) {
            delay--;
            return;
        }
        updateOrigin();
        update();
    }

    private void updateOrigin() {
        ro = r;
        go = g;
        bo = b;
        ao = a;
    }

    private void update() {
        int color = (int) config.color.get(emitter.t, () -> getMemRandom("color"));
        r = FUtil.red(color);
        g = FUtil.green(color);
        b = FUtil.blue(color);
        a = FUtil.alpha(color);
    }

    private float[] getRealColor(float partial) {
        float[] e = emitter.rgba();
        return new float[]{e[0] * FUtil.lerp(partial, ro, r), e[1] * FUtil.lerp(partial, go, g),
                e[2] * FUtil.lerp(partial, bo, b), e[3] * FUtil.lerp(partial, ao, a)};
    }

    private UvRect getRealUVs(float partial) {
        if (!config.uvAnimation.enable) {
            return UvRect.FULL;
        }
        ForgeConfig.UvAnimationCfg c = config.uvAnimation;
        float t = getT(partial);
        int tilesA = (int) c.tiles.a;
        int tilesB = (int) c.tiles.b;
        float cellU = 1f / tilesA;
        float cellV = 1f / tilesB;
        float frame = (float) c.startFrame.get(t, () -> getMemRandom("startFrame"));
        frame += c.cycle * (float) c.frameOverTime.get(t, () -> getMemRandom("frameOverTime"));
        float u0, v0;
        if (c.animation == ForgeConfig.UvAnimationMode.WholeSheet) {
            u0 = (int) (frame % tilesA) * cellU;
            v0 = (int) (frame / tilesA) * cellV;
        } else {
            u0 = (int) (frame % tilesA) * cellU;
            v0 = (int) (getMemRandom("randomRow") * tilesB) * cellV;
        }
        if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(cellU) || !Float.isFinite(cellV)) {
            return UvRect.FULL;
        }
        return new UvRect(u0, v0, u0 + cellU, v0 + cellV);
    }

    @Override
    Object renderKey() {
        return config;
    }

    @Override
    void gather(FCtx ctx) {
        if (delay > 0 || !emitter.isVisible()) {
            return;
        }
        IMaterial material = config.material.material;
        if (material == null) {
            return;
        }
        float partial = ctx.partial;
        Vec3 from = emitter.transform.position();
        Vec3 end = from.copy().add(emitter.transform.localToWorld().transformDirection(config.end));
        float offset = -(float) config.emitRate.get(getT(partial), () -> getMemRandom("emit"));
        UvRect uvs = getRealUVs(partial);
        float halfWidth = (float) config.width.get(getT(partial), () -> getMemRandom("width"));
        float[] color = getRealColor(partial);
        int argb = FUtil.argb(color[0], color[1], color[2], color[3]);
        UvRect uv = new UvRect(uvs.u0() + offset, uvs.v0(), uvs.u1() + offset, uvs.v1());
        float depth = ctx.distance(from.copy().add(end).mul(0.5f));

        ctx.items.add(new RenderItem.RibbonSegmentItem(from, end, halfWidth * 2f, halfWidth * 2f, argb, argb, material,
                config.material.blendMode, uv, FCtx.settings(config.renderer, config.material), null, null, depth));
    }
}

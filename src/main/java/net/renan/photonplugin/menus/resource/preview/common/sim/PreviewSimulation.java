package net.renan.photonplugin.menus.resource.preview.common.sim;

import net.renan.photonplugin.menus.resource.preview.common.render.GroundForecast;
import net.renan.photonplugin.menus.resource.preview.common.render.Java2DFxRenderer;
import net.renan.photonplugin.menus.resource.preview.common.render.PreviewCamera;

import java.awt.*;

public interface PreviewSimulation {
    void tick(float ticks);

    void restart();

    void simulateTo(float targetTicks);

    long seed();

    void setSeed(long seed);

    float playbackTime();

    int particleCount();

    default float predictGroundReach() {
        return predictGround().reach();
    }

    default GroundForecast predictGround() {
        return GroundForecast.NONE;
    }

    default boolean groundForecastReady() {
        return true;
    }

    void render(Java2DFxRenderer renderer, Graphics2D g2, int width, int height, PreviewCamera camera);
}

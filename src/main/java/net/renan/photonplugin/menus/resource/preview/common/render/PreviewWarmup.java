package net.renan.photonplugin.menus.resource.preview.common.render;

import net.renan.photonplugin.menus.resource.preview.common.math.Vec3;
import net.renan.photonplugin.menus.resource.preview.common.model.BlendMode;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PreviewWarmup {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private PreviewWarmup() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(PreviewWarmup::run, "fx-preview-warmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static List<BlendMode> modes() {
        BlendMode.Factor one = BlendMode.Factor.ONE;
        return List.of(BlendMode.standardAlpha(),
                new BlendMode(true, BlendMode.Factor.SRC_ALPHA, one, one, one, BlendMode.Equation.ADD),
                new BlendMode(true, one, one, one, one, BlendMode.Equation.ADD),
                new BlendMode(true, BlendMode.Factor.DST_COLOR, BlendMode.Factor.ZERO, one, BlendMode.Factor.ZERO,
                        BlendMode.Equation.ADD),
                new BlendMode(false, one, BlendMode.Factor.ZERO, one, BlendMode.Factor.ZERO, BlendMode.Equation.ADD));
    }

    private static void run() {
        try {
            Java2DFxRenderer renderer = new Java2DFxRenderer(null, message -> {
            });
            PreviewCamera camera = new PreviewCamera();
            BufferedImage frame = new BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB);
            BufferedImage texture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < 64; i++) {
                texture.setRGB(i % 8, i / 8, 0xC0FFFFFF);
            }
            Vec3 a = new Vec3(-0.5f, 0f, 0f);
            Vec3 b = new Vec3(0.5f, 0f, 0f);
            Vec3 c = new Vec3(0f, 1f, 0f);
            for (int round = 0; round < 3; round++) {
                Graphics2D g2 = frame.createGraphics();
                try {
                    renderer.renderItems(g2, 96, 64, List.of(), camera);
                    for (BlendMode mode : modes()) {
                        renderer.drawWorldTriangle(g2, 96, 64, camera, texture, a, 0f, 0f, b, 1f, 0f, c, 0.5f, 1f, mode);
                    }
                } finally {
                    g2.dispose();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}

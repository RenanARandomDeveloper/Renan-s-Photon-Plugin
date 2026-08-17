package net.renan.photonplugin.menus.resource.core.support;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class IconScaler {

    private IconScaler() {
    }

    public static Icon scaleIconTo(Icon source, int targetPx) {
        if (source == null) return null;
        int sw = source.getIconWidth(), sh = source.getIconHeight();
        if (sw <= 0 || sh <= 0) return null;

        BufferedImage img = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        source.paintIcon(new JLabel(), g2, 0, 0);
        g2.dispose();

        BufferedImage scaled = new BufferedImage(targetPx, targetPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g3 = scaled.createGraphics();
        g3.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g3.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int newW, newH;
        if (sw >= sh) {
            newW = targetPx;
            newH = Math.max(1, targetPx * sh / sw);
        } else {
            newH = targetPx;
            newW = Math.max(1, targetPx * sw / sh);
        }
        int xOff = (targetPx - newW) / 2;
        int yOff = (targetPx - newH) / 2;

        g3.drawImage(img, xOff, yOff, newW, newH, null);
        g3.dispose();
        return new ImageIcon(scaled);
    }
}

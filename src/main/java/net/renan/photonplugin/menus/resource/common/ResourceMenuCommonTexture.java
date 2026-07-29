package net.renan.photonplugin.menus.resource.common;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.common.ResourceMenuCommon.*;

public final class ResourceMenuCommonTexture {
    private static final String PNG_EXTENSION = ".png";
    private ResourceMenuCommonTexture() {}

    public static final class TextureThumbnailCache {
        public static final int THUMB_SIZE = 64;
        private static final int MAX_ENTRIES = 500;
        private static final Map<String, ImageIcon> CACHE =
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
                        return size() > MAX_ENTRIES;
                    }
                };

        private TextureThumbnailCache() {}

        public static synchronized ImageIcon getThumbnail(File file) {
            if (file == null || !file.isFile()) return null;
            String key = file.getAbsolutePath() + "|" + file.lastModified();
            ImageIcon cached = CACHE.get(key);
            if (cached != null) return cached;

            ImageIcon loaded = loadAndScale(file);
            if (loaded != null) CACHE.put(key, loaded);
            return loaded;
        }

        private static ImageIcon loadAndScale(File file) {
            try {
                BufferedImage src = ImageIO.read(file);
                if (src == null) return null;
                int w = src.getWidth(), h = src.getHeight();
                if (w <= 0 || h <= 0) return null;

                int newW, newH;
                if (w >= h) {
                    newW = THUMB_SIZE;
                    newH = Math.max(1, THUMB_SIZE * h / w);
                } else {
                    newH = THUMB_SIZE;
                    newW = Math.max(1, THUMB_SIZE * w / h);
                }

                BufferedImage canvas = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = canvas.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

                int xOff = (THUMB_SIZE - newW) / 2;
                int yOff = (THUMB_SIZE - newH) / 2;
                g2.drawImage(src, xOff, yOff, newW, newH, null);
                g2.dispose();
                return new ImageIcon(canvas);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static JPanel createFXPanel(MCreator mcreator, Supplier<File> targetDirGetter) {
        FXBrowserPanel browser = new FXBrowserPanel(
                List.of(targetDirGetter),
                PNG_EXTENSION,
                file -> {
                    if (hasExtension(file, PNG_EXTENSION)) {
                        return TextureThumbnailCache.getThumbnail(file);
                    }
                    return null;
                }
        );

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        TransparentToolBar toolBar = createToolBar();
        toolBar.add(buildImportButton(mcreator, browser, targetDirGetter, PNG_EXTENSION));
        toolBar.add(buildCloneButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildDeleteButtonForBrowser(mcreator, browser));
        toolBar.add(buildExportButtonForBrowser(mcreator, browser));
        toolBar.add(buildRenameButtonForBrowser(mcreator, browser));
        toolBar.add(buildMoveButtonForBrowser(mcreator, browser, targetDirGetter));
        toolBar.add(buildCreateFolderButtonForBrowser(mcreator, browser));
        toolBar.add(buildFilterBarForBrowser(mcreator, browser));

        panel.add(toolBar, BorderLayout.NORTH);
        panel.add(browser, BorderLayout.CENTER);

        attachDirectoryWatcher(panel, browser, targetDirGetter);

        return panel;
    }
}
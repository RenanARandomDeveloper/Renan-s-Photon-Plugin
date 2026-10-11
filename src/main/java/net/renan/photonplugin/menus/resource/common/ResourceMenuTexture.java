package net.renan.photonplugin.menus.resource.common;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TransparentToolBar;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static net.renan.photonplugin.menus.resource.core.actions.CloneAction.buildCloneButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.CreateFolderAction.buildCreateFolderButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.DeleteAction.buildDeleteButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ExportAction.buildExportButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.FilterBar.buildFilterBarForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.ImportAction.buildImportButton;
import static net.renan.photonplugin.menus.resource.core.actions.MoveAction.buildMoveButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.actions.RenameAction.buildRenameButtonForBrowser;
import static net.renan.photonplugin.menus.resource.core.naming.FileNameRules.hasExtension;
import static net.renan.photonplugin.menus.resource.core.support.DirectoryWatcher.attachDirectoryWatcher;
import static net.renan.photonplugin.menus.resource.core.support.MenuToolbars.createToolBar;

public final class ResourceMenuTexture {
    private static final String PNG_EXTENSION = ".png";

    private ResourceMenuTexture() {
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

    public static final class TextureThumbnailCache {
        public static final int THUMB_SIZE = 64;
        private static final int MAX_ENTRIES = 1024;
        private static final int LOADER_THREADS = 2;
        private static final Map<String, ImageIcon> CACHE =
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
                        return size() > MAX_ENTRIES;
                    }
                };
        private static final ExecutorService LOADER = Executors.newFixedThreadPool(LOADER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "Photon-ThumbnailLoader");
            thread.setDaemon(true);
            return thread;
        });

        private TextureThumbnailCache() {
        }

        private static synchronized ImageIcon cached(String key) {
            return CACHE.get(key);
        }

        private static synchronized void store(String key, ImageIcon icon) {
            CACHE.put(key, icon);
        }

        public static Icon getThumbnail(File file) {
            if (file == null || !file.isFile()) return null;
            String key = file.getAbsolutePath() + "|" + file.lastModified();
            ImageIcon ready = cached(key);
            if (ready != null) return ready;
            return new LazyThumbnailIcon(key, file);
        }

        static ImageIcon loadAndScale(File file) {
            try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
                if (input == null) return null;
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int fullW = reader.getWidth(0), fullH = reader.getHeight(0);
                    if (fullW <= 0 || fullH <= 0) return null;
                    ImageReadParam param = reader.getDefaultReadParam();
                    int step = Math.max(1, Math.max(fullW, fullH) / (THUMB_SIZE * 2));
                    if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                    BufferedImage src = reader.read(0, param);
                    if (src == null) return null;
                    return scaleToThumbnail(src);
                } finally {
                    reader.dispose();
                }
            } catch (Exception | OutOfMemoryError e) {
                return null;
            }
        }

        private static ImageIcon scaleToThumbnail(BufferedImage src) {
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
        }

        private static final class LazyThumbnailIcon implements Icon {
            private final String key;
            private final File file;
            private final AtomicBoolean requested = new AtomicBoolean(false);
            private volatile ImageIcon loaded;

            LazyThumbnailIcon(String key, File file) {
                this.key = key;
                this.file = file;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                ImageIcon ready = loaded;
                if (ready == null) ready = cached(key);
                if (ready != null) {
                    loaded = ready;
                    ready.paintIcon(c, g, x, y);
                    return;
                }
                if (requested.compareAndSet(false, true)) {
                    WeakReference<Component> owner = new WeakReference<>(c);
                    LOADER.execute(() -> {
                        ImageIcon icon = loadAndScale(file);
                        if (icon != null) {
                            store(key, icon);
                            loaded = icon;
                        }
                        Component target = owner.get();
                        if (target != null) SwingUtilities.invokeLater(target::repaint);
                    });
                }
            }

            @Override
            public int getIconWidth() {
                return THUMB_SIZE;
            }

            @Override
            public int getIconHeight() {
                return THUMB_SIZE;
            }
        }
    }
}

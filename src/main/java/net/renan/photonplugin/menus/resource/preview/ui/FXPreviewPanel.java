package net.renan.photonplugin.menus.resource.preview.ui;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.preview.common.PreviewMode;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtReader;
import net.renan.photonplugin.menus.resource.preview.common.nbt.NbtTag;
import net.renan.photonplugin.menus.resource.preview.common.render.*;
import net.renan.photonplugin.menus.resource.preview.common.sim.PreviewSimulation;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeSimulation;
import net.renan.photonplugin.menus.resource.preview.forge.ForgeTextures;
import net.renan.photonplugin.menus.resource.preview.neo.model.FxScene;
import net.renan.photonplugin.menus.resource.preview.neo.pack.FxPackArchive;
import net.renan.photonplugin.menus.resource.preview.neo.parse.FxNbtParser;
import net.renan.photonplugin.menus.resource.preview.neo.render.MeshCache;
import net.renan.photonplugin.menus.resource.preview.neo.render.NeoItemGatherer;
import net.renan.photonplugin.menus.resource.preview.neo.render.NeoLibraryResolver;
import net.renan.photonplugin.menus.resource.preview.neo.sim.FxSceneSimulation;
import net.renan.photonplugin.menus.resource.preview.neo.sim.NeoPreviewSimulation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FXPreviewPanel extends JPanel {
    private static final String FXPROJ_EXTENSION = ".fxproj";
    private static final Color BACKGROUND = new Color(0x2b2f36);

    private static final int MAX_FPS = 60;
    private static final long FRAME_PERIOD_NANOS = 1_000_000_000L / MAX_FPS;
    private final Supplier<List<File>> assetsRootsGetter;
    private final PreviewMode mode;
    private final PreviewCamera camera = new PreviewCamera();
    private final RenderCanvas canvas = new RenderCanvas();
    private final RenderLoop renderLoop = new RenderLoop();
    private final JComboBox<FxPackArchive.Effect> effectSelector = new JComboBox<>();
    private final JPanel effectBar = new JPanel(new BorderLayout(6, 0));
    private final JButton playPauseButton = new JButton();
    private final JButton restartButton = new JButton();
    private final JButton randomSeedButton = new JButton(L10N.t("plugin.photon.resourcemenu.fx.preview.random_seed"));
    private final JTextField playbackTimeField = new JTextField(6);
    private final JTextField seedField = new JTextField(8);
    private final JLabel cpuTimeLabel = new JLabel();
    private final JLabel frameTimeLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel();
    private final JLabel particlesLabel = new JLabel();
    private State state = State.EMPTY;
    private String errorMessage = "";
    private File currentFile;
    private FxPackArchive currentPack;
    private boolean updatingEffectSelector;
    private int loadToken;
    private boolean paused;

    private volatile int canvasWidth;
    private volatile int canvasHeight;

    private BufferedImage latestFrame;

    private float lastKnownPlaybackTime;
    private long lastKnownSeed;

    public FXPreviewPanel(Supplier<List<File>> assetsRootsGetter) {
        this(assetsRootsGetter, PreviewMode.NEOFORGE);
    }

    public FXPreviewPanel(Supplier<List<File>> assetsRootsGetter, PreviewMode mode) {
        super(new BorderLayout());
        this.assetsRootsGetter = assetsRootsGetter;
        this.mode = Objects.requireNonNull(mode, "mode");
        PreviewWarmup.start();
        setPreferredSize(new Dimension(340, 400));
        setMinimumSize(new Dimension(220, 200));
        setOpaque(true);
        setBackground(BACKGROUND);

        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                canvasWidth = canvas.getWidth();
                canvasHeight = canvas.getHeight();
            }
        });

        add(buildEffectBar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(buildSceneInfoPanel(), BorderLayout.SOUTH);
        updateTransportEnabled();
        resetTelemetry();

        renderLoop.start();
    }

    private static boolean isFxProject(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(FXPROJ_EXTENSION);
    }

    private static FxScene parseScene(byte[] bytes) throws IOException {
        NbtTag.CompoundTag root = NbtReader.readCompressed(bytes);
        return FxNbtParser.parseScene(root);
    }

    private static FxScene parseProject(byte[] bytes) throws IOException {
        NbtTag.CompoundTag root = NbtReader.readCompressed(bytes);
        return FxNbtParser.parseProject(root);
    }

    private static Function<String, FxScene> subEffectResolver(FxPackArchive pack, PreviewDiagnostics diagnostics) {
        if (pack == null) {
            return fxId -> null;
        }
        Map<String, Optional<FxScene>> cache = new HashMap<>();
        return fxId -> {
            if (fxId == null) {
                return null;
            }
            return cache.computeIfAbsent(fxId, id -> {
                for (FxPackArchive.Effect effect : pack.effects()) {
                    if (effect.id().equals(id)) {
                        byte[] bytes = pack.effectBytes(effect);
                        try {
                            return Optional.of(parseScene(bytes));
                        } catch (IOException e) {
                            diagnostics.warn("FX preview: sub emitter reference \"" + id + "\" failed to parse: "
                                    + e.getMessage());
                            return Optional.empty();
                        }
                    }
                }
                diagnostics.warn("FX preview: sub emitter reference not found in this pack: \"" + id + "\"");
                return Optional.empty();
            }).orElse(null);
        };
    }

    public void setSelectedFile(File file) {
        if (Objects.equals(file, currentFile)) {
            return;
        }
        currentFile = file;
        currentPack = null;
        hideEffectBar();
        loadToken++;
        int myToken = loadToken;

        if (file == null) {
            renderLoop.unload();
            state = State.EMPTY;
            latestFrame = null;
            updateTransportEnabled();
            updatePlayPauseLabel();
            resetTelemetry();
            canvas.repaint();
            return;
        }

        beginLoading();
        if (FxPackArchive.isFxPack(file)) {
            if (mode == PreviewMode.NEOFORGE) {
                loadPack(file, myToken);
            } else {
                startSceneLoad(myToken, 0L, null, () -> {
                    throw SceneLoader.fxPackNotSupported(mode);
                }, file);
            }
        } else {
            boolean project = isFxProject(file);
            startSceneLoad(myToken, file.getAbsolutePath().hashCode(), null,
                    () -> SceneLoader.parse(Files.readAllBytes(file.toPath()), mode, project), file);
        }
    }

    private void beginLoading() {
        state = State.LOADING;
        renderLoop.unload();
        latestFrame = null;
        updateTransportEnabled();
        resetTelemetry();
        canvas.repaint();
    }

    private void loadPack(File file, int myToken) {
        new SwingWorker<FxPackArchive, Void>() {
            @Override
            protected FxPackArchive doInBackground() throws Exception {
                return FxPackArchive.read(file);
            }

            @Override
            protected void done() {
                if (myToken != loadToken) {
                    return;
                }
                try {
                    FxPackArchive pack = get();
                    if (pack.effects().isEmpty()) {
                        showError(L10N.t("plugin.photon.resourcemenu.fx.preview.pack_empty"));
                        return;
                    }
                    currentPack = pack;
                    showEffectBar(pack.effects());
                    loadPackEffect(pack.effects().get(0));
                } catch (Exception ex) {
                    failLoad(file, ex);
                }
            }
        }.execute();
    }

    private void loadPackEffect(FxPackArchive.Effect effect) {
        FxPackArchive pack = currentPack;
        File file = currentFile;
        loadToken++;
        int myToken = loadToken;
        beginLoading();
        long seed = (file.getAbsolutePath() + "|" + effect.id()).hashCode();
        startSceneLoad(myToken, seed, pack, () -> new LoadedScene.Neo(parseScene(pack.effectBytes(effect))),
                file + " (" + effect.id() + ")");
    }

    private void startSceneLoad(int myToken, long seed, FxPackArchive pack, Callable<LoadedScene> loader, Object source) {
        new SwingWorker<LoadedScene, Void>() {
            @Override
            protected LoadedScene doInBackground() throws Exception {
                return loader.call();
            }

            @Override
            protected void done() {
                if (myToken != loadToken) {
                    return;
                }
                try {
                    LoadedScene scene = get();
                    if (scene.isEmpty()) {
                        showError(L10N.t("plugin.photon.resourcemenu.fx.preview.empty_scene"));
                        return;
                    }
                    paused = false;
                    state = FXPreviewPanel.State.PLAYING;
                    List<File> assetsRoots = assetsRootsGetter.get();
                    renderLoop.load(scene, seed, assetsRoots, pack, myToken);
                    updateTransportEnabled();
                    updatePlayPauseLabel();
                    canvas.repaint();
                } catch (Exception ex) {
                    failLoad(source, ex);
                }
            }
        }.execute();
    }

    private void failLoad(Object source, Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof SceneLoader.EditionMismatch mismatch) {
            MenuLog.warn("FX preview (" + mode.displayName() + ") skipped " + source + ": " + mismatch.getMessage());
            showError(mismatch.getMessage());
            return;
        }
        MenuLog.error("Failed to load FX preview for " + source, ex);
        String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        showError(L10N.t("plugin.photon.resourcemenu.fx.preview.load_failed") + ": " + detail);
    }

    private void showError(String message) {
        state = State.ERROR;
        errorMessage = message;
        updateTransportEnabled();
        updatePlayPauseLabel();
        canvas.repaint();
    }

    private JPanel buildEffectBar() {
        effectBar.setOpaque(true);
        effectBar.setBackground(new Color(0x23262c));
        effectBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JLabel label = new JLabel(L10N.t("plugin.photon.resourcemenu.fx.preview.pack_effect"));
        label.setForeground(new Color(0xa8adb5));
        effectBar.add(label, BorderLayout.WEST);
        effectBar.add(effectSelector, BorderLayout.CENTER);
        effectSelector.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onEffectSelected();
            }
        });
        effectBar.setVisible(false);
        return effectBar;
    }

    private void onEffectSelected() {
        if (updatingEffectSelector || currentPack == null) {
            return;
        }
        if (effectSelector.getSelectedItem() instanceof FxPackArchive.Effect effect) {
            loadPackEffect(effect);
        }
    }

    private void showEffectBar(List<FxPackArchive.Effect> effects) {
        updatingEffectSelector = true;
        try {
            effectSelector.setModel(new DefaultComboBoxModel<>(effects.toArray(new FxPackArchive.Effect[0])));
            effectSelector.setSelectedIndex(0);
        } finally {
            updatingEffectSelector = false;
        }
        effectBar.setVisible(true);
        revalidate();
        repaint();
    }

    private void hideEffectBar() {
        if (!effectBar.isVisible()) {
            return;
        }
        updatingEffectSelector = true;
        try {
            effectSelector.setModel(new DefaultComboBoxModel<>());
        } finally {
            updatingEffectSelector = false;
        }
        effectBar.setVisible(false);
        revalidate();
        repaint();
    }

    private void applySnapshot(RenderLoop.Snapshot snapshot) {
        if (snapshot.generation() != loadToken) {
            return;
        }
        latestFrame = snapshot.image();
        lastKnownPlaybackTime = snapshot.playbackTime();
        lastKnownSeed = snapshot.seed();
        if (!playbackTimeField.isFocusOwner()) {
            playbackTimeField.setText(String.format(Locale.ROOT, "%.0f", snapshot.playbackTime()));
        }
        if (!seedField.isFocusOwner()) {
            seedField.setText(Long.toString(snapshot.seed()));
        }
        cpuTimeLabel.setText(snapshot.cpuTimeMicros() + " us");
        frameTimeLabel.setText(snapshot.renderTimeMicros() + " us");
        fpsLabel.setText(String.format(Locale.ROOT, "%.0f fps", snapshot.fps()));
        particlesLabel.setText(Integer.toString(snapshot.particleCount()));
        canvas.repaint();
    }

    private JPanel buildSceneInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(0x23262c));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;

        JLabel title = new JLabel(L10N.t("plugin.photon.resourcemenu.fx.preview.scene_info_title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(new Color(0xd8dce2));
        panel.add(title, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        restartButton.setText(L10N.t("plugin.photon.resourcemenu.fx.preview.restart"));
        restartButton.addActionListener(e -> restart());
        playPauseButton.addActionListener(e -> togglePlayPause());
        updatePlayPauseLabel();
        buttons.add(restartButton);
        buttons.add(playPauseButton);
        gbc.gridy = 1;
        panel.add(buttons, gbc);

        gbc.gridwidth = 1;
        addFieldRow(panel, gbc, 2, L10N.t("plugin.photon.resourcemenu.fx.preview.playback_time"), playbackTimeField, null);
        addFieldRow(panel, gbc, 3, L10N.t("plugin.photon.resourcemenu.fx.preview.seed"), seedField, randomSeedButton);

        styleReadout(cpuTimeLabel);
        styleReadout(frameTimeLabel);
        styleReadout(fpsLabel);
        styleReadout(particlesLabel);
        addReadoutRow(panel, gbc, 4, L10N.t("plugin.photon.resourcemenu.fx.preview.cpu_time"), cpuTimeLabel);
        addReadoutRow(panel, gbc, 5, L10N.t("plugin.photon.resourcemenu.fx.preview.frame_time"), frameTimeLabel);
        addReadoutRow(panel, gbc, 6, L10N.t("plugin.photon.resourcemenu.fx.preview.fps"), fpsLabel);
        addReadoutRow(panel, gbc, 7, L10N.t("plugin.photon.resourcemenu.fx.preview.particles"), particlesLabel);

        installCommitHandler(playbackTimeField, this::commitPlaybackTime);
        installCommitHandler(seedField, this::commitSeed);
        randomSeedButton.addActionListener(e -> rerollSeed());

        return panel;
    }

    private void addFieldRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field, JButton trailingButton) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(new Color(0xa8adb5));
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        if (trailingButton != null) {
            gbc.gridx = 2;
            gbc.weightx = 0;
            panel.add(trailingButton, gbc);
        }
        gbc.weightx = 0;
    }

    private void addReadoutRow(JPanel panel, GridBagConstraints gbc, int row, String label, JLabel valueLabel) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(new Color(0xa8adb5));
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(valueLabel, gbc);
        gbc.gridwidth = 1;
    }

    private void styleReadout(JLabel label) {
        label.setForeground(new Color(0xd8dce2));
        label.setText("-");
    }

    private void resetTelemetry() {
        playbackTimeField.setText("");
        seedField.setText("");
        lastKnownPlaybackTime = 0f;
        lastKnownSeed = 0L;
        styleReadout(cpuTimeLabel);
        styleReadout(frameTimeLabel);
        styleReadout(fpsLabel);
        styleReadout(particlesLabel);
    }

    private void installCommitHandler(JTextField field, Runnable commit) {
        field.addActionListener(e -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit.run();
            }
        });
    }

    private void commitPlaybackTime() {
        if (state != State.PLAYING) {
            return;
        }
        try {
            float target = Math.max(0f, Float.parseFloat(playbackTimeField.getText().trim()));
            renderLoop.simulateTo(target);
            lastKnownPlaybackTime = target;
            playbackTimeField.setText(String.format(Locale.ROOT, "%.0f", target));
        } catch (NumberFormatException ignored) {
            playbackTimeField.setText(String.format(Locale.ROOT, "%.0f", lastKnownPlaybackTime));
        }
    }

    private void commitSeed() {
        if (state != State.PLAYING) {
            return;
        }
        try {
            long seed = Long.parseLong(seedField.getText().trim());
            applySeed(seed);
        } catch (NumberFormatException ignored) {
            seedField.setText(Long.toString(lastKnownSeed));
        }
    }

    private void rerollSeed() {
        if (state != State.PLAYING) {
            return;
        }
        applySeed(new Random().nextLong());
    }

    private void applySeed(long seed) {
        renderLoop.reseed(seed);
        lastKnownSeed = seed;
        seedField.setText(Long.toString(seed));
    }

    private void togglePlayPause() {
        if (state != State.PLAYING) {
            return;
        }
        paused = !paused;
        renderLoop.setPaused(paused);
        updatePlayPauseLabel();
        canvas.repaint();
    }

    private void restart() {
        if (state != State.PLAYING) {
            return;
        }
        paused = true;
        renderLoop.restart();
        updatePlayPauseLabel();
        canvas.repaint();
    }

    private void updatePlayPauseLabel() {
        playPauseButton.setText(paused || state != State.PLAYING
                ? L10N.t("plugin.photon.resourcemenu.fx.preview.play")
                : L10N.t("plugin.photon.resourcemenu.fx.preview.pause"));
    }

    private void updateTransportEnabled() {
        boolean enabled = state == State.PLAYING;
        playPauseButton.setEnabled(enabled);
        restartButton.setEnabled(enabled);
        playbackTimeField.setEnabled(enabled);
        seedField.setEnabled(enabled);
        randomSeedButton.setEnabled(enabled);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        renderLoop.stop();
    }

    private enum State {EMPTY, LOADING, ERROR, PLAYING}

    private final class RenderCanvas extends JPanel {
        private int lastMouseX, lastMouseY;

        RenderCanvas() {
            setOpaque(true);
            setBackground(BACKGROUND);
            installMouseControls();
        }

        private void installMouseControls() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2) {
                        camera.resetView();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    boolean panModifier = e.isShiftDown() || (e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0;
                    if (panModifier) {
                        camera.pan(dx, dy);
                    } else {
                        camera.orbit(-dx, dy);
                    }
                }
            });
            addMouseWheelListener(this::onMouseWheel);
        }

        private void onMouseWheel(MouseWheelEvent e) {
            camera.zoom(e.getWheelRotation());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                switch (state) {
                    case PLAYING -> {
                        if (latestFrame != null) {
                            g2.drawImage(latestFrame, 0, 0, null);
                        }
                        if (paused) {
                            drawPausedBadge(g2);
                        }
                        drawHint(g2);
                    }
                    case LOADING -> drawCenteredMessage(g2, L10N.t("plugin.photon.resourcemenu.fx.preview.loading"));
                    case ERROR -> drawCenteredMessage(g2, errorMessage);
                    case EMPTY ->
                            drawCenteredMessage(g2, L10N.t("plugin.photon.resourcemenu.fx.preview.select_a_file"));
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawCenteredMessage(Graphics2D g2, String message) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xa8adb5));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            int maxWidth = Math.max(60, getWidth() - 32);
            drawWrapped(g2, message, maxWidth);
        }

        private void drawWrapped(Graphics2D g2, String message, int maxWidth) {
            List<String> lines = wrapText(g2, message, maxWidth);
            int lineHeight = g2.getFontMetrics().getHeight();
            int totalHeight = lineHeight * lines.size();
            int y = (getHeight() - totalHeight) / 2 + g2.getFontMetrics().getAscent();
            for (String line : lines) {
                Rectangle2D bounds = g2.getFontMetrics().getStringBounds(line, g2);
                int x = (int) ((getWidth() - bounds.getWidth()) / 2);
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }

        private List<String> wrapText(Graphics2D g2, String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (g2.getFontMetrics().stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private void drawHint(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g2.setColor(new Color(255, 255, 255, 110));
            String hint = L10N.t("plugin.photon.resourcemenu.fx.preview.controls_hint");
            g2.drawString(hint, 8, getHeight() - 8);
        }

        private void drawPausedBadge(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.setColor(new Color(255, 255, 255, 170));
            String text = L10N.t("plugin.photon.resourcemenu.fx.preview.paused_badge");
            Rectangle2D bounds = g2.getFontMetrics().getStringBounds(text, g2);
            g2.drawString(text, (float) (getWidth() - bounds.getWidth()) - 8f, 16f);
        }
    }

    private final class RenderLoop {
        private final BlockingQueue<Command> commands = new LinkedBlockingQueue<>();

        private final BufferedImage[] frameBuffers = new BufferedImage[2];
        private volatile boolean running = true;
        private Thread thread;
        private int writeIndex;

        void start() {
            thread = new Thread(this::runLoop, "fx-preview-render");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        void load(LoadedScene scene, long seed, List<File> assetsRoots, FxPackArchive pack, int generation) {
            commands.add(new Command.Load(scene, seed, assetsRoots, pack, generation));
        }

        void unload() {
            commands.add(new Command.Unload());
        }

        void restart() {
            commands.add(new Command.Restart());
        }

        void simulateTo(float targetTicks) {
            commands.add(new Command.SimulateTo(targetTicks));
        }

        void reseed(long seed) {
            commands.add(new Command.Reseed(seed));
        }

        void setPaused(boolean paused) {
            commands.add(new Command.SetPaused(paused));
        }

        private void runLoop() {
            PreviewDiagnostics diagnostics = MenuLog::warn;
            PreviewSimulation simulation = null;
            Java2DFxRenderer renderer = null;
            boolean framed = true;
            int generation = 0;
            boolean paused = false;
            long lastFrameNanos = 0;
            double smoothedFps = 0;
            long nextDeadline = System.nanoTime();

            while (running) {
                Command cmd;
                try {
                    cmd = simulation == null ? commands.poll(200, TimeUnit.MILLISECONDS) : commands.poll();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                while (cmd != null) {
                    try {
                        switch (cmd) {
                            case Command.Load l -> {
                                generation = l.generation();
                                AssetResolver resolver = new AssetResolver(l.assetsRoots(), l.pack(), diagnostics);
                                TextureCache textures = new TextureCache(resolver, diagnostics);
                                renderer = new Java2DFxRenderer(textures, diagnostics);
                                switch (l.scene()) {
                                    case LoadedScene.Neo neo -> {
                                        MeshCache meshCache = new MeshCache(resolver, diagnostics);
                                        textures.setLibraryResolver(new NeoLibraryResolver(resolver, diagnostics));
                                        simulation = new NeoPreviewSimulation(
                                                new FxSceneSimulation(neo.scene(), l.seed(), meshCache::get,
                                                        subEffectResolver(l.pack(), diagnostics)),
                                                new NeoItemGatherer(meshCache));
                                    }
                                    case LoadedScene.Forge forge -> {
                                        for (String reference : forge.scene().generatedTextures) {
                                            textures.putGenerated(reference, ForgeTextures.image(reference));
                                        }
                                        for (String note : forge.scene().notes) {
                                            diagnostics.warn("FX preview (1.20.1): " + note);
                                        }
                                        simulation = new ForgeSimulation(forge.scene(), l.seed());
                                    }
                                }
                                camera.frame(null);
                                framed = false;
                                paused = false;
                                lastFrameNanos = 0;
                                smoothedFps = 0;
                                nextDeadline = System.nanoTime();
                            }
                            case Command.Unload u -> {
                                simulation = null;
                                renderer = null;
                            }
                            case Command.Restart r -> {
                                if (simulation != null) {
                                    simulation.restart();
                                    paused = true;
                                    lastFrameNanos = 0;
                                }
                            }
                            case Command.SimulateTo s -> {
                                if (simulation != null) {
                                    simulation.simulateTo(s.targetTicks());
                                }
                            }
                            case Command.Reseed r -> {
                                if (simulation != null) {
                                    float currentTime = simulation.playbackTime();
                                    simulation.setSeed(r.seed());
                                    simulation.simulateTo(currentTime);
                                }
                            }
                            case Command.SetPaused p -> paused = p.paused();
                        }
                    } catch (RuntimeException e) {
                        MenuLog.error("FX preview render thread: " + cmd.getClass().getSimpleName() + " failed", e);
                        reportFailure(generation, e);
                        simulation = null;
                        renderer = null;
                    }
                    cmd = commands.poll();
                }

                if (simulation == null || renderer == null) {
                    continue;
                }

                int width = canvasWidth;
                int height = canvasHeight;
                if (width <= 0 || height <= 0) {
                    nextDeadline = scheduleNextFrame(nextDeadline);
                    continue;
                }

                long now = System.nanoTime();
                float dtSeconds = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
                lastFrameNanos = now;

                long tickMicros;
                long renderMicros;
                BufferedImage buffer;
                try {
                    long tm = 0;
                    if (!paused) {
                        float dt = Math.min(dtSeconds, 0.25f);
                        long tickStart = System.nanoTime();
                        simulation.tick(dt * 20f);
                        tm = (System.nanoTime() - tickStart) / 1000;
                    }
                    tickMicros = tm;

                    BufferedImage buf = frameBuffers[writeIndex];
                    if (buf == null || buf.getWidth() != width || buf.getHeight() != height) {
                        buf = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        frameBuffers[writeIndex] = buf;
                    }
                    long renderStart = System.nanoTime();
                    Graphics2D g2 = buf.createGraphics();
                    try {
                        camera.applyPendingFrame(width, height);
                        if (!framed && simulation.groundForecastReady()) {
                            camera.frame(simulation.predictGround().bounds());
                            framed = true;
                        }
                        simulation.render(renderer, g2, width, height, camera);
                    } finally {
                        g2.dispose();
                    }
                    renderMicros = (System.nanoTime() - renderStart) / 1000;
                    buffer = buf;
                } catch (RuntimeException e) {
                    MenuLog.error("FX preview render thread: frame failed, unloading this scene", e);
                    reportFailure(generation, e);
                    simulation = null;
                    renderer = null;
                    nextDeadline = scheduleNextFrame(nextDeadline);
                    continue;
                }
                writeIndex = 1 - writeIndex;

                double instantFps = dtSeconds > 0 ? 1.0 / dtSeconds : 0;
                smoothedFps = smoothedFps <= 0 ? instantFps : smoothedFps * 0.9 + instantFps * 0.1;
                smoothedFps = Math.min(smoothedFps, MAX_FPS);

                int particleCount = simulation.particleCount();

                Snapshot snapshot = new Snapshot(generation, buffer, simulation.playbackTime(), simulation.seed(),
                        tickMicros, renderMicros, smoothedFps, particleCount);
                SwingUtilities.invokeLater(() -> applySnapshot(snapshot));

                nextDeadline = scheduleNextFrame(nextDeadline);
            }
        }

        private void reportFailure(int generation, RuntimeException cause) {
            SwingUtilities.invokeLater(() -> {
                if (generation != loadToken) {
                    return;
                }
                state = State.ERROR;
                latestFrame = null;
                String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                errorMessage = L10N.t("plugin.photon.resourcemenu.fx.preview.load_failed") + ": " + detail;
                updateTransportEnabled();
                updatePlayPauseLabel();
                canvas.repaint();
            });
        }

        private long scheduleNextFrame(long previousDeadline) {
            long deadline = previousDeadline + FRAME_PERIOD_NANOS;
            long sleepNanos = deadline - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
                return deadline;
            }
            return System.nanoTime();
        }

        private sealed interface Command {
            record Load(LoadedScene scene, long seed, List<File> assetsRoots, FxPackArchive pack,
                        int generation) implements Command {
            }

            record Unload() implements Command {
            }

            record Restart() implements Command {
            }

            record SimulateTo(float targetTicks) implements Command {
            }

            record Reseed(long seed) implements Command {
            }

            record SetPaused(boolean paused) implements Command {
            }
        }

        record Snapshot(int generation, BufferedImage image, float playbackTime, long seed,
                        long cpuTimeMicros, long renderTimeMicros, double fps, int particleCount) {
        }
    }
}

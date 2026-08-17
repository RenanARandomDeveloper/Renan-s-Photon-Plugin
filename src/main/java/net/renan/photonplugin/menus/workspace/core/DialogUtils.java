package net.renan.photonplugin.menus.workspace.core;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public final class DialogUtils {
    private DialogUtils() {
    }

    public static void closeOnEscape(JDialog dialog) {
        KeyStroke escapeKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), escapeKey,
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static void setDefaultButton(JDialog dialog, JButton button) {
        dialog.getRootPane().setDefaultButton(button);
    }

    public static int platformShortcutModifier() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    public static void bindShortcut(JComponent root, KeyStroke keyStroke, Runnable action) {
        String actionKey = "photon.shortcut." + keyStroke;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey);
        root.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    public static void configureListMouseSelection(JList<?> list, Runnable onEmptyAreaClick) {
        int[] anchor = { -1 };
        boolean[] dragging = { false };
        boolean[] dragOccurred = { false };

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragOccurred[0] = false;
                anchor[0] = -1;
                dragging[0] = false;

                int index = list.locationToIndex(e.getPoint());
                Rectangle cellBounds = index >= 0 ? list.getCellBounds(index, index) : null;
                boolean withinCell = cellBounds != null && cellBounds.contains(e.getPoint());

                if (!withinCell) {
                    onEmptyAreaClick.run();
                    return;
                }

                if (!SwingUtilities.isLeftMouseButton(e) || e.isShiftDown() || e.isControlDown()
                        || e.isMetaDown()) {
                    return;
                }

                anchor[0] = index;
                dragging[0] = true;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                dragging[0] = false;
                if (dragOccurred[0]) {
                    return;
                }

                int index = list.locationToIndex(e.getPoint());
                Rectangle cellBounds = index >= 0 ? list.getCellBounds(index, index) : null;
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) {
                    onEmptyAreaClick.run();
                }
            }
        });

        list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (!dragging[0] || anchor[0] < 0) {
                    return;
                }
                dragOccurred[0] = true;

                int size = list.getModel().getSize();
                if (size == 0) {
                    return;
                }
                int current = list.locationToIndex(e.getPoint());
                if (current < 0) {
                    return;
                }
                current = Math.max(0, Math.min(size - 1, current));
                list.setSelectionInterval(anchor[0], current);
                Rectangle cellBounds = list.getCellBounds(current, current);
                if (cellBounds != null) {
                    list.scrollRectToVisible(cellBounds);
                }
            }
        });
    }

    public static void enableWheelSelectionNavigation(JList<?> list) {
        list.addMouseWheelListener(e -> {
            int[] selected = list.getSelectedIndices();
            if (selected.length != 1) {
                return;
            }
            int size = list.getModel().getSize();
            if (size == 0) {
                return;
            }
            int next = selected[0] + e.getWheelRotation();
            if (next < 0 || next >= size) {
                return;
            }
            list.setSelectedIndex(next);
            list.ensureIndexIsVisible(next);
            e.consume();
        });
    }

    public static void clampToScreen(Window window, double maxWidthFraction, double maxHeightFraction) {
        GraphicsConfiguration graphicsConfig = window.getGraphicsConfiguration();
        if (graphicsConfig == null) {
            graphicsConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }

        Rectangle screenBounds = graphicsConfig.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfig);
        int usableWidth = screenBounds.width - screenInsets.left - screenInsets.right;
        int usableHeight = screenBounds.height - screenInsets.top - screenInsets.bottom;

        int maxWidth = (int) (usableWidth * maxWidthFraction);
        int maxHeight = (int) (usableHeight * maxHeightFraction);

        Dimension current = window.getSize();
        int width = Math.min(current.width, Math.max(maxWidth, 1));
        int height = Math.min(current.height, Math.max(maxHeight, 1));
        if (width != current.width || height != current.height) {
            window.setSize(width, height);
        }
    }

    public static JPanel bottomButtonRow(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (JButton button : buttons) {
            row.add(button);
        }
        return row;
    }

    public static JPanel withCaption(JButton button, String caption) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        captionLabel.setForeground(Color.GRAY);
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, Math.max(10f,
                captionLabel.getFont().getSize() - 2f)));
        captionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));

        wrapper.add(button);
        wrapper.add(captionLabel);
        return wrapper;
    }

    public static JLabel mutedCaption() {
        JLabel label = new JLabel();
        label.setForeground(Color.GRAY);
        label.setFont(label.getFont().deriveFont(Math.max(10f, label.getFont().getSize() - 1f)));
        return label;
    }

    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 1f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }
}

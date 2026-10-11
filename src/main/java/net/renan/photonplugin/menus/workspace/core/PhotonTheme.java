package net.renan.photonplugin.menus.workspace.core;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class PhotonTheme {

    public static final Color DANGER = new Color(0xE5, 0x48, 0x4D);
    public static final Color DANGER_HOVER = new Color(0xD0, 0x3B, 0x40);
    public static final Color DANGER_PRESSED = new Color(0xB2, 0x2E, 0x33);
    public static final int BUTTON_ARC = 10;
    public static final int CARD_ARC = 14;
    public static final int LIST_CONTAINER_ARC = 12;
    public static final int LIST_ROW_ARC = 8;
    private static final Color ACCENT_FALLBACK = new Color(0x3D, 0x7B, 0xFF);

    private PhotonTheme() {
    }

    public static Color accent() {
        return uiColor("Component.accentColor", ACCENT_FALLBACK);
    }

    public static Color accentHover() {
        return darken(accent(), 0.12);
    }

    public static Color accentPressed() {
        return darken(accent(), 0.24);
    }

    public static Color windowBackground() {
        return uiColor("Panel.background", Color.WHITE);
    }

    public static Color textForeground() {
        return uiColor("Label.foreground", Color.BLACK);
    }

    public static boolean isDark() {
        Color bg = windowBackground();
        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return luminance < 128;
    }

    public static Color mutedForeground() {
        return mix(textForeground(), windowBackground(), 0.45);
    }

    public static Color cardBackground() {
        return isDark() ? lighten(windowBackground(), 0.08) : darken(windowBackground(), 0.025);
    }

    public static Color cardBorder() {
        return isDark() ? lighten(windowBackground(), 0.22) : darken(windowBackground(), 0.14);
    }

    public static Color listBackground() {
        return uiColor("List.background", windowBackground());
    }

    public static Color zebraStripe() {
        return isDark() ? lighten(listBackground(), 0.06) : darken(listBackground(), 0.04);
    }

    public static Color badgeForeground() {
        return isDark() ? lighten(accent(), 0.25) : accentPressed();
    }

    private static Color neutralButtonBase() {
        return isDark() ? lighten(windowBackground(), 0.14) : darken(windowBackground(), 0.06);
    }

    private static Color neutralButtonHover() {
        return isDark() ? lighten(windowBackground(), 0.22) : darken(windowBackground(), 0.12);
    }

    private static Color neutralButtonPressed() {
        return isDark() ? lighten(windowBackground(), 0.30) : darken(windowBackground(), 0.18);
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color lighten(Color c, double amount) {
        return mix(c, Color.WHITE, amount);
    }

    private static Color darken(Color c, double amount) {
        return mix(c, Color.BLACK, amount);
    }

    private static Color mix(Color a, Color b, double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) Math.round(a.getRed() * (1 - ratio) + b.getRed() * ratio);
        int g = (int) Math.round(a.getGreen() * (1 - ratio) + b.getGreen() * ratio);
        int bl = (int) Math.round(a.getBlue() * (1 - ratio) + b.getBlue() * ratio);
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static Border roundedLineBorder(Color color, int thickness, int arc) {
        return new AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                int inset = Math.max(1, thickness) / 2;
                g2.drawRoundRect(x + inset, y + inset, width - thickness, height - thickness, arc, arc);
                g2.dispose();
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(thickness, thickness, thickness, thickness);
            }

            @Override
            public Insets getBorderInsets(Component c, Insets insets) {
                insets.set(thickness, thickness, thickness, thickness);
                return insets;
            }

            @Override
            public boolean isBorderOpaque() {
                return false;
            }
        };
    }

    public static void paintRoundedCellBackground(Graphics g, JComponent cell, Color background) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(background);
        g2.fillRoundRect(1, 0, Math.max(0, cell.getWidth() - 2), cell.getHeight(), LIST_ROW_ARC, LIST_ROW_ARC);
        g2.dispose();
    }

    public static JPanel roundedPanel(int arc) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    public static JButton button(String text, ButtonVariant variant) {
        Color base;
        Color hover;
        Color pressed;
        Color foreground;
        Color border;

        if (variant == ButtonVariant.PRIMARY) {
            base = accent();
            hover = accentHover();
            pressed = accentPressed();
            foreground = Color.WHITE;
            border = accentPressed();
        } else if (variant == ButtonVariant.DANGER) {
            base = DANGER;
            hover = DANGER_HOVER;
            pressed = DANGER_PRESSED;
            foreground = Color.WHITE;
            border = DANGER_PRESSED;
        } else {
            base = neutralButtonBase();
            hover = neutralButtonHover();
            pressed = neutralButtonPressed();
            foreground = textForeground();
            border = cardBorder();
        }

        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, BUTTON_ARC, BUTTON_ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(foreground);
        button.setBackground(base);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(roundedLineBorder(border, 1, BUTTON_ARC),
                BorderFactory.createEmptyBorder(7, 16, 7, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12.5f));
        button.addMouseListener(new HoverPainter(button, base, hover, pressed));
        return button;
    }

    public static JPanel card() {
        JPanel panel = roundedPanel(CARD_ARC);
        panel.setBackground(cardBackground());
        panel.setBorder(BorderFactory.createCompoundBorder(
                roundedLineBorder(cardBorder(), 1, CARD_ARC),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        return panel;
    }

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13.5f));
        label.setForeground(textForeground());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return label;
    }

    public enum ButtonVariant {
        PRIMARY, NEUTRAL, DANGER
    }

    private static final class HoverPainter extends MouseAdapter {
        private final JButton button;
        private final Color base;
        private final Color hover;
        private final Color pressed;

        private HoverPainter(JButton button, Color base, Color hover, Color pressed) {
            this.button = button;
            this.base = base;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(hover);
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(base);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(pressed);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(button.contains(e.getPoint()) ? hover : base);
            }
        }
    }
}
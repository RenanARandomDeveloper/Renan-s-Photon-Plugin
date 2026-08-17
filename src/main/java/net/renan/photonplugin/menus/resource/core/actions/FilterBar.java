package net.renan.photonplugin.menus.resource.core.actions;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.browser.FilterType;
import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class FilterBar {

    private FilterBar() {
    }

    public static JComponent buildFilterBarForBrowser(MCreator mcreator, FXBrowserPanel browser) {
        JPanel bar = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color base = MenuTheme.altBackgroundColor();
                g2.setPaint(new GradientPaint(0, 0, MenuTheme.shade(base, 12), 0, h, MenuTheme.shade(base, -12)));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g2.setColor(MenuTheme.accentColor());
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 6));

        JTextField searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(MenuTheme.shade(MenuTheme.foregroundColor(), -60));
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(L10N.t("plugin.photon.resourcemenu.filter.placeholder"), getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        searchField.setOpaque(false);
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        searchField.setForeground(MenuTheme.foregroundColor());
        searchField.setCaretColor(MenuTheme.foregroundColor());
        ComponentUtils.deriveFont(searchField, 12f);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }

            private void apply() {
                browser.setFilter(searchField.getText());
            }
        });

        JLabel divider = new JLabel("|");
        divider.setForeground(MenuTheme.shade(MenuTheme.foregroundColor(), -40));
        ComponentUtils.deriveFont(divider, 12f);

        JLabel filterLabel = new JLabel(L10N.t("plugin.photon.resourcemenu.filter.elements"));
        filterLabel.setForeground(MenuTheme.foregroundColor());
        ComponentUtils.deriveFont(filterLabel, 12f);
        filterLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        filterLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));

        JPopupMenu popup = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (FilterType type : FilterType.values()) {
            String label = switch (type) {
                case ALL -> L10N.t("plugin.photon.resourcemenu.filter.option.all");
                case FILE -> L10N.t("plugin.photon.resourcemenu.filter.option.file");
                case FOLDER -> L10N.t("plugin.photon.resourcemenu.filter.option.folder");
            };
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, type == FilterType.ALL);
            item.addActionListener(e -> browser.setFilterType(type));
            group.add(item);
            popup.add(item);
        }
        filterLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.show(filterLabel, 0, filterLabel.getHeight());
            }
        });

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rightSide.setOpaque(false);
        rightSide.add(divider);
        rightSide.add(filterLabel);

        bar.add(searchField, BorderLayout.CENTER);
        bar.add(rightSide, BorderLayout.EAST);
        bar.setPreferredSize(new Dimension(230, 24));
        return bar;
    }
}

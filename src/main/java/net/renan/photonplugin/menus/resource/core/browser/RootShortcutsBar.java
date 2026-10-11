package net.renan.photonplugin.menus.resource.core.browser;

import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.Supplier;

final class RootShortcutsBar {

    private RootShortcutsBar() {
    }

    static JPanel build(FXBrowserPanel browser) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));

        final Color normalBg = MenuTheme.altBackgroundColor();
        final Color hoverBg = MenuTheme.blend(normalBg, MenuTheme.foregroundColor(), 0.18f);
        final Color borderColor = MenuTheme.blend(normalBg, MenuTheme.foregroundColor(), 0.35f);
        final Color dropBg = MenuTheme.accentColor();

        for (Supplier<File> rootSupplier : browser.rootSuppliers) {
            File rootDir = rootSupplier.get();
            JLabel pill = new JLabel("\u2192 " + rootDir.getName());
            pill.setFont(pill.getFont().deriveFont(Font.PLAIN, 11f));
            pill.setForeground(MenuTheme.foregroundColor());
            pill.setBackground(normalBg);
            pill.setOpaque(true);
            pill.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor, 1),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)));
            pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pill.setToolTipText(rootDir.getAbsolutePath());
            pill.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    browser.navHistory.clear();
                    browser.navHistory.push(FXBrowserPanel.VIRTUAL_ROOT_SENTINEL);
                    browser.currentDir = rootSupplier.get();
                    browser.clearSelection();
                    browser.refresh();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    pill.setBackground(hoverBg);
                    pill.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    pill.setBackground(normalBg);
                    pill.repaint();
                }
            });
            FileDropSupport.install(pill, browser, "root shortcut '" + pill.getText() + "'",
                    rootSupplier, null,
                    () -> {
                        pill.setBackground(dropBg);
                        pill.repaint();
                    },
                    () -> {
                        pill.setBackground(normalBg);
                        pill.repaint();
                    });
            bar.add(pill);
        }
        return bar;
    }
}

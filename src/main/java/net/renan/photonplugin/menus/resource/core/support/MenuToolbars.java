package net.renan.photonplugin.menus.resource.core.support;

import net.mcreator.ui.component.TransparentToolBar;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.init.UIRES;

import javax.swing.*;
import java.awt.*;

public final class MenuToolbars {

    private MenuToolbars() {
    }

    public static TransparentToolBar createToolBar() {
        TransparentToolBar toolBar = new TransparentToolBar();
        toolBar.setOpaque(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 0));
        return toolBar;
    }

    public static JButton createToolbarButton(String text, String iconKey) {
        JButton button = new JButton(text);
        if (iconKey != null) {
            try {
                button.setIcon(UIRES.get(iconKey));
            } catch (Exception ignored) {
            }
        }
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        ComponentUtils.deriveFont(button, 12.0F);
        return button;
    }
}

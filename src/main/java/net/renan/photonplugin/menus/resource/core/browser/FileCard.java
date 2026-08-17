package net.renan.photonplugin.menus.resource.core.browser;

import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.Function;

public class FileCard extends ItemCard {
    private static final int W = 90;
    private static final int H = 100;
    private static final int ICON_SIZE = 64;
    private final Icon icon;
    private final String name;

    public FileCard(File file, FXBrowserPanel browser, Function<File, Icon> iconProvider) {
        super(file, W, H, ICON_SIZE);
        this.icon = iconProvider != null ? iconProvider.apply(file) : null;
        this.name = FileNameParts.of(file).baseName();

        installHoverEffect();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    browser.handleFileCardClick(FileCard.this, e);
            }
        });

        installDragSource(browser,
                () -> browser.containsSelectedFile(file),
                browser::getSelectedFiles,
                () -> browser.handleFileCardClick(FileCard.this,
                        new MouseEvent(FileCard.this, MouseEvent.MOUSE_CLICKED,
                                System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1)));
    }

    @Override
    protected void paintContent(Graphics2D g2, int w, int h) {
        paintIconAndLabel(g2, w, h, icon, name, Font.PLAIN);
    }
}

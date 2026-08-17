package net.renan.photonplugin.menus.resource.core.browser;

import net.mcreator.ui.init.UIRES;
import net.renan.photonplugin.menus.resource.core.support.IconScaler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class FolderCard extends ItemCard {
    private static final int W = 90;
    private static final int H = 100;
    private static final int FOLDER_ICON_SIZE = 64;
    private final Icon icon;
    private final String name;

    public FolderCard(File dir, FXBrowserPanel browser) {
        super(dir, W, H, FOLDER_ICON_SIZE);
        Icon resolved = null;
        try {
            resolved = IconScaler.scaleIconTo(UIRES.get("16px_photon.folder"), FOLDER_ICON_SIZE);
        } catch (Exception ignored) {
        }
        if (resolved == null)
            try {
                resolved = IconScaler.scaleIconTo(UIManager.getIcon("FileView.directoryIcon"), FOLDER_ICON_SIZE);
            } catch (Exception ignored) {
            }
        if (resolved == null)
            try {
                resolved = IconScaler.scaleIconTo(UIManager.getIcon("Tree.openIcon"), FOLDER_ICON_SIZE);
            } catch (Exception ignored) {
            }

        this.icon = resolved;
        this.name = browser.getFolderName(dir);

        installHoverEffect();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                browser.handleFolderCardClick(FolderCard.this, e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    browser.navigate(dir);
                }
            }
        });

        FileDropSupport.install(this, browser, "folder '" + dir.getName() + "'", () -> dir, null,
                () -> {
                    dropHighlight = true;
                    repaint();
                },
                () -> {
                    dropHighlight = false;
                    repaint();
                });

        installDragSource(browser,
                () -> browser.containsSelectedFolder(file),
                browser::getSelectedFolders,
                () -> browser.handleFolderCardClick(FolderCard.this,
                        new MouseEvent(FolderCard.this, MouseEvent.MOUSE_CLICKED,
                                System.currentTimeMillis(), 0, 0, 0, 1, false, MouseEvent.BUTTON1)));
    }

    @Override
    protected void paintContent(Graphics2D g2, int w, int h) {
        paintIconAndLabel(g2, w, h, icon, name, Font.BOLD);
    }
}

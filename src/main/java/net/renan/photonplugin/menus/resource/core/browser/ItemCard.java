package net.renan.photonplugin.menus.resource.core.browser;

import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class ItemCard extends JPanel {
    protected static final int ICON_PAD = 12;
    protected static final int TEXT_GAP = 10;
    protected static final int MAX_DISPLAY_NAME_CHARS = 15;
    protected final File file;
    protected final int iconSize;
    protected boolean hovered = false;
    protected boolean selected = false;
    protected boolean dropHighlight = false;
    private boolean nameTruncated = false;

    ItemCard(File file, int prefW, int prefH, int iconSize) {
        this.file = file;
        this.iconSize = iconSize;
        setPreferredSize(new Dimension(prefW, prefH));
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static Color bgHoverColor() {
        return MenuTheme.foregroundColor(50);
    }

    private static Color bgSelectedColor() {
        return MenuTheme.accentColor(120);
    }

    private static Color bdSelectedColor() {
        return MenuTheme.accentColor();
    }

    protected static Font cardFont(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font("Dialog", Font.PLAIN, 12);
        return base.deriveFont(style, size);
    }

    protected static String truncToWidth(Graphics2D g2, String s, int maxPx) {
        if (s == null || s.isEmpty()) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(s) <= maxPx) return s;

        String ellipsis = "...";
        while (s.length() > 1 && fm.stringWidth(s + ellipsis) > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? ellipsis : s + ellipsis;
    }

    protected static String truncToChars(String s, int maxChars) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= maxChars) return s;

        return s.substring(0, maxChars) + "...";
    }

    public File getFile() {
        return file;
    }

    public void setSelected(boolean v) {
        if (selected != v) {
            selected = v;
            repaint();
        }
    }

    protected abstract void paintContent(Graphics2D g2, int w, int h);

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth(), h = getHeight();
            if (dropHighlight) {
                g2.setColor(MenuTheme.accentColor(110));
                g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                g2.setColor(MenuTheme.accentColor());
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);
            } else if (selected) {
                g2.setColor(bgSelectedColor());
                g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
                g2.setColor(bdSelectedColor());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);
            } else if (hovered) {
                g2.setColor(bgHoverColor());
                g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8);
            }
            paintContent(g2, w, h);
        } finally {
            g2.dispose();
        }
    }

    protected void drawIcon(Graphics2D g2, Icon icon, int h) {
        if (icon == null) return;
        icon.paintIcon(this, g2, ICON_PAD, (h - iconSize) / 2);
    }

    protected int textX() {
        return ICON_PAD + iconSize + TEXT_GAP;
    }

    protected final void paintIconAndLabel(Graphics2D g2, int w, int h, Icon icon, String name, int fontStyle) {
        g2.setFont(cardFont(fontStyle, 11f));
        FontMetrics fm = g2.getFontMetrics();
        int contentH = iconSize + 5 + fm.getAscent() + fm.getDescent();
        int topOffset = Math.max(4, (h - contentH) / 2);
        if (icon != null) {
            int ix = (w - iconSize) / 2;
            icon.paintIcon(this, g2, ix, topOffset);
        }
        g2.setColor(MenuTheme.foregroundColor());
        String displayName = truncToWidth(g2, truncToChars(name, MAX_DISPLAY_NAME_CHARS), w - 8);
        boolean isTruncated = !displayName.equals(name);
        if (isTruncated != nameTruncated) {
            nameTruncated = isTruncated;
            setToolTipText(isTruncated ? name : null);
        }
        int tx = Math.max(4, (w - fm.stringWidth(displayName)) / 2);
        int ty = topOffset + iconSize + 5 + fm.getAscent();
        g2.drawString(displayName, tx, ty);
    }

    protected final void installHoverEffect() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    protected final void installDragSource(FXBrowserPanel browser, BooleanSupplier alreadySelected,
                                           Supplier<List<File>> selectedItemsSupplier, Runnable selectThisItem) {
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                this, DnDConstants.ACTION_MOVE, (DragGestureEvent dge) -> {
                    if (!browser.isDragAndDropEnabled()) return;
                    final List<File> dragItems;
                    if (alreadySelected.getAsBoolean()) {
                        dragItems = selectedItemsSupplier.get();
                    } else {
                        browser.clearSelectionExposed();
                        selectThisItem.run();
                        dragItems = List.of(file);
                    }
                    if (dragItems.isEmpty()) return;
                    dge.startDrag(DragSource.DefaultMoveDrop, new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
                        }

                        @Override
                        public boolean isDataFlavorSupported(DataFlavor flavor) {
                            return DataFlavor.javaFileListFlavor.equals(flavor);
                        }

                        @Override
                        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                            return dragItems;
                        }
                    });
                });
    }
}

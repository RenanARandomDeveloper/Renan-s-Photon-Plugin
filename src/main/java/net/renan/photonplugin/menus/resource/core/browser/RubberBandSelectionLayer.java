package net.renan.photonplugin.menus.resource.core.browser;

import net.renan.photonplugin.menus.resource.core.support.MenuTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;

final class RubberBandSelectionLayer extends JPanel {
    private static final int THRESHOLD = 6;

    private static Color fillClr() {
        return MenuTheme.accentColor(50);
    }

    private static Color edgeClr() {
        return MenuTheme.accentColor(190);
    }

    private final FXBrowserPanel browser;
    private final JScrollPane scroll;

    private Point pressPoint;
    private Point dragPoint;
    private boolean banding = false;
    private boolean dispatching = false;
    private Component hoveredCard = null;
    private Component pressedComponent = null;
    private Component lastClickedComponent = null;

    RubberBandSelectionLayer(FXBrowserPanel browser, JScrollPane scroll) {
        this.browser = browser;
        this.scroll = scroll;
        setOpaque(false);
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressPoint = e.getPoint();
                dragPoint = e.getPoint();
                banding = false;
                pressedComponent = bestTargetAt(e.getPoint());
                lastClickedComponent = pressedComponent;

                if (!(pressedComponent instanceof ItemCard) && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
                    browser.clearSelection();
                    browser.grid.requestFocusInWindow();
                }

                redispatchTo(pressedComponent, e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null) return;
                dragPoint = e.getPoint();
                if (pressedComponent instanceof ItemCard) {
                    redispatchTo(pressedComponent, e);
                    return;
                }
                int dx = Math.abs(dragPoint.x - pressPoint.x);
                int dy = Math.abs(dragPoint.y - pressPoint.y);
                if (!banding && (dx > THRESHOLD || dy > THRESHOLD)) {
                    banding = true;
                    updateHover(null);
                }
                if (banding) {
                    applyRubberBandSelection(e.isControlDown() || e.isMetaDown());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean wasBanding = banding;
                banding = false;
                pressPoint = null;
                dragPoint = null;
                repaint();
                if (!wasBanding && pressedComponent != null) redispatchTo(pressedComponent, e);
                if (wasBanding) lastClickedComponent = null;
                pressedComponent = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (lastClickedComponent != null) {
                    redispatchTo(lastClickedComponent, e);
                    lastClickedComponent = null;
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
                if (hoveredCard != null) {
                    Point cp = SwingUtilities.convertPoint(RubberBandSelectionLayer.this, e.getPoint(), hoveredCard);
                    synthesise(hoveredCard, MouseEvent.MOUSE_MOVED, cp.x, cp.y);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateHover(null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int rotation = e.getWheelRotation();
                if (rotation == 0) return;
                e.consume();
                browser.navigateGrid(rotation < 0 ? 1 : -1, 0);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private Component deepestAt(Point layerPt) {
        Point sp = SwingUtilities.convertPoint(this, layerPt, scroll);
        Component c = SwingUtilities.getDeepestComponentAt(scroll, sp.x, sp.y);
        return c != null ? c : scroll;
    }

    private Component bestTargetAt(Point layerPt) {
        Component deep = deepestAt(layerPt);
        if (deep == null) return null;
        Component c = deep;
        while (c != null && !(c instanceof ItemCard)) {
            if (c == scroll) return deep;
            c = c.getParent();
        }
        return (c instanceof ItemCard) ? c : deep;
    }

    private Component deepestCard(Point layerPt) {
        Component c = deepestAt(layerPt);
        while (c != null && !(c instanceof ItemCard)) {
            if (c == scroll) return null;
            c = c.getParent();
        }
        return (c instanceof ItemCard) ? c : null;
    }

    private void redispatchTo(Component target, MouseEvent src) {
        if (target == null || target == this || dispatching) return;
        dispatching = true;
        try {
            target.dispatchEvent(SwingUtilities.convertMouseEvent(this, src, target));
        } finally {
            dispatching = false;
        }
    }

    private void updateHover(Point layerPt) {
        Component under = (layerPt != null) ? deepestCard(layerPt) : null;
        if (under == hoveredCard) return;
        if (hoveredCard != null) synthesise(hoveredCard, MouseEvent.MOUSE_EXITED, -1, -1);
        if (under != null && layerPt != null) {
            Point cp = SwingUtilities.convertPoint(this, layerPt, under);
            synthesise(under, MouseEvent.MOUSE_ENTERED, cp.x, cp.y);
        }
        hoveredCard = under;
    }

    private void synthesise(Component target, int id, int x, int y) {
        if (dispatching) return;
        dispatching = true;
        try {
            target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), 0, x, y, 0, false));
        } finally {
            dispatching = false;
        }
    }

    private void applyRubberBandSelection(boolean additive) {
        Rectangle band = getBandRect();
        if (band == null) return;

        for (ItemCard card : browser.allCards) {
            Rectangle cb = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), this);
            boolean hit = band.intersects(cb);
            File f = card.getFile();
            if (additive) {
                if (hit) {
                    card.setSelected(true);
                    if (card instanceof FolderCard) browser.selectedFolders.add(f);
                    else if (card instanceof FileCard) browser.selectedFiles.add(f);
                }
            } else {
                card.setSelected(hit);
                if (card instanceof FolderCard) {
                    if (hit) browser.selectedFolders.add(f);
                    else browser.selectedFolders.remove(f);
                } else if (card instanceof FileCard) {
                    if (hit) browser.selectedFiles.add(f);
                    else browser.selectedFiles.remove(f);
                }
            }
        }
        browser.lastSelected = null;
    }

    private Rectangle getBandRect() {
        if (pressPoint == null || dragPoint == null) return null;
        return new Rectangle(
                Math.min(pressPoint.x, dragPoint.x),
                Math.min(pressPoint.y, dragPoint.y),
                Math.abs(pressPoint.x - dragPoint.x),
                Math.abs(pressPoint.y - dragPoint.y));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle r = getBandRect();
        if (!banding || r == null || (r.width < 1 && r.height < 1)) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fillClr());
        g2.fillRect(r.x, r.y, r.width, r.height);
        g2.setColor(edgeClr());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(r.x, r.y, r.width, r.height);
        g2.dispose();
    }
}
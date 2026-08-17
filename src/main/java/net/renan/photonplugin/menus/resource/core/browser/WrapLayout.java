package net.renan.photonplugin.menus.resource.core.browser;

import java.awt.*;

final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            Container container = target;
            while (targetWidth == 0 && container.getParent() != null) {
                container = container.getParent();
                targetWidth = container.getWidth();
            }
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int memberCount = target.getComponentCount();
            for (int i = 0; i < memberCount; i++) {
                Component member = target.getComponent(i);
                if (!member.isVisible()) continue;

                Dimension memberSize = preferred ? member.getPreferredSize() : member.getMinimumSize();
                if (rowWidth + memberSize.width > maxWidth && rowWidth > 0) {
                    dim.width = Math.max(dim.width, rowWidth);
                    dim.height += rowHeight + (dim.height > 0 ? vgap : 0);
                    rowWidth = 0;
                    rowHeight = 0;
                }

                if (rowWidth != 0) rowWidth += hgap;
                rowWidth += memberSize.width;
                rowHeight = Math.max(rowHeight, memberSize.height);
            }

            dim.width = Math.max(dim.width, rowWidth);
            dim.height += rowHeight + (dim.height > 0 ? vgap : 0);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }
}

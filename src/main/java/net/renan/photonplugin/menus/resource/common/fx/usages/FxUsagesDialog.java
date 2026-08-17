package net.renan.photonplugin.menus.resource.common.fx.usages;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.workspace.elements.ModElement;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;
import net.renan.photonplugin.menus.resource.core.support.MenuToolbars;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FxUsagesDialog {

    private static final String FX_EXTENSION = ".fx";

    private record UsagesHeaderRow(String text) {
    }

    private static final Object NO_USAGES_ROW = new Object();

    private FxUsagesDialog() {
    }

    public static JButton buildSearchUsagesButton(MCreator mcreator, FXBrowserPanel browser) {
        JButton button = MenuToolbars.createToolbarButton(L10N.t("plugin.photon.resourcemenu.search.usages"), "16px_photon.search");
        button.addActionListener(e -> {
            List<File> targets = browser.getSelectedFiles();
            if (targets.isEmpty()) {
                JOptionPane.showMessageDialog(mcreator,
                        L10N.t("plugin.photon.resourcemenu.search.usages.no.selection"),
                        L10N.t("plugin.photon.resourcemenu.search.usages.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Map<String, List<ModElement>> usagesMap = new LinkedHashMap<>();
            for (File fxFile : targets) {
                FileNameParts parts = FileNameParts.of(fxFile);
                usagesMap.put(parts.baseName(), new ArrayList<>());
            }
            FxUsageSearcher.searchInWorkspace(mcreator, usagesMap);
            show(mcreator, usagesMap);
        });
        return button;
    }

    private static void show(MCreator mcreator, Map<String, List<ModElement>> usagesMap) {
        JDialog dialog = new JDialog(mcreator, L10N.t("plugin.photon.resourcemenu.search.usages.title"), true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 380);
        dialog.setLocationRelativeTo(mcreator);
        dialog.setResizable(true);
        DefaultListModel<Object> listModel = new DefaultListModel<>();

        for (Map.Entry<String, List<ModElement>> entry : usagesMap.entrySet()) {
            listModel.addElement(new UsagesHeaderRow(entry.getKey() + FX_EXTENSION));
            if (entry.getValue().isEmpty()) {
                listModel.addElement(NO_USAGES_ROW);
            } else {
                entry.getValue().forEach(listModel::addElement);
            }
        }

        JList<Object> resultList = new JList<>(listModel);
        resultList.setCellRenderer(new ModElementResultRenderer());
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setOpaque(false);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                Object selected = resultList.getSelectedValue();
                if (!(selected instanceof ModElement me)) return;
                dialog.dispose();
                try {
                    ModElementGUI<?> gui = me.getType().getModElementGUI(mcreator, me, true);
                    if (gui != null) gui.showView();
                } catch (Exception ex) {
                    MenuLog.error("Failed to open mod element editor for " + me.getName(), ex);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        dialog.add(scroll, BorderLayout.CENTER);

        JButton closeBtn = new JButton(L10N.t("common.close"));
        closeBtn.addActionListener(ev -> dialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnPanel.setOpaque(false);
        btnPanel.add(closeBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static class ModElementResultRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value == NO_USAGES_ROW) {
                setText("    " + L10N.t("plugin.photon.resourcemenu.search.usages.no.usages"));
                setFont(getFont().deriveFont(Font.ITALIC, 11f));
                setIcon(null);
                setForeground(isSelected ? Color.WHITE : Color.GRAY);
                setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            } else if (value instanceof UsagesHeaderRow header) {
                setText(header.text());
                setFont(getFont().deriveFont(Font.BOLD, 12f));
                setIcon(null);
                setForeground(isSelected ? Color.WHITE : new Color(210, 210, 210));
                setBorder(BorderFactory.createEmptyBorder(8, 6, 3, 6));
            } else if (value instanceof ModElement me) {
                setText(me.getName());
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
                setForeground(isSelected ? Color.WHITE : new Color(150, 210, 110));
                setBorder(BorderFactory.createEmptyBorder(2, 22, 2, 6));
                setIcon(resolveModElementIcon(me));
            }
            return this;
        }

        private static Icon resolveModElementIcon(ModElement me) {
            try {
                Icon icon = me.getType().getIcon();
                if (icon != null) return icon;
            } catch (Exception ignored) {
            }
            try {
                return UIRES.get("16px." + me.getType().getRegistryName());
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}

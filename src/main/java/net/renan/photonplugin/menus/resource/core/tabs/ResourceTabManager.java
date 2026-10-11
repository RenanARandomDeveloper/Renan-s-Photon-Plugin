package net.renan.photonplugin.menus.resource.core.tabs;

import net.mcreator.ui.MCreator;
import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ResourceTabManager {

    private static final String LEGACY_RESOURCES_PANEL_CLASS_NAME = "WorkspacePanelResources";

    private ResourceTabManager() {
    }

    private static Object invokeNoArgMethod(Object target, String methodName) {
        if (target == null) return null;
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Method method = cls.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                MenuLog.error("Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> cls = target.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                MenuLog.error("Failed to read field '" + fieldName + "' on " + target.getClass().getName(), e);
                return null;
            }
        }
        return null;
    }

    private static Container findContainerBySimpleClassName(Component root, String simpleName, int depth) {
        if (root == null || depth > 24) return null;
        if (root instanceof Container container) {
            if (simpleName.equals(root.getClass().getSimpleName())) return container;
            for (Component child : container.getComponents()) {
                Container found = findContainerBySimpleClassName(child, simpleName, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Container resolveResourcesPan(MCreator mcreator) {
        if (mcreator == null) return null;

        Object workspacePanel = invokeNoArgMethod(mcreator, "getWorkspacePanel");
        Object holder = workspacePanel != null ? workspacePanel : mcreator;

        Object resourcesPanValue = readField(holder, "resourcesPan");
        if (resourcesPanValue instanceof Container container) return container;

        if (holder != mcreator) {
            resourcesPanValue = readField(mcreator, "resourcesPan");
            if (resourcesPanValue instanceof Container container) return container;
        }

        Container legacyContainer = findContainerBySimpleClassName(mcreator.getContentPane(),
                LEGACY_RESOURCES_PANEL_CLASS_NAME, 0);
        if (legacyContainer != null) {
            MenuLog.info("Resolved resources panel through legacy Swing tree lookup for " + mcreator.getClass().getName());
            return legacyContainer;
        }

        MenuLog.error("Unable to resolve the resources panel for MCreator instance of type "
                + mcreator.getClass().getName(), null);
        return null;
    }

    public static JTabbedPane findResourceTabbedPane(MCreator mcreator) {
        Container resourcesPan = resolveResourcesPan(mcreator);
        if (resourcesPan == null) return null;
        for (Component component : resourcesPan.getComponents()) {
            if (component instanceof JTabbedPane tabbedPane) {
                return tabbedPane;
            }
        }
        return null;
    }

    public static boolean isTabPresent(JTabbedPane tabbedPane, JPanel panel) {
        if (tabbedPane == null || panel == null) return false;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) == panel) return true;
        }
        return false;
    }

    private static void removeTabsByTitle(JTabbedPane tabbedPane, String title, Component keep) {
        if (tabbedPane == null || title == null) return;
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            Component component = tabbedPane.getComponentAt(i);
            if (component == keep) continue;
            if (title.equals(tabbedPane.getTitleAt(i))) {
                tabbedPane.removeTabAt(i);
            }
        }
    }

    public static JPanel setupResourceTab(MCreator mcreator, JPanel panelInstance, String tabTitle, ResourceTabPanelFactory panelFactory) {
        Container resourcesPan = resolveResourcesPan(mcreator);
        if (resourcesPan == null) return panelInstance;

        JTabbedPane resourceTabs = findResourceTabbedPane(mcreator);

        if (panelInstance != null && isTabPresent(resourceTabs, panelInstance)) {
            removeTabsByTitle(resourceTabs, tabTitle, panelInstance);
            return panelInstance;
        }

        removeTabsByTitle(resourceTabs, tabTitle, null);

        JPanel panel = panelFactory.create(mcreator);
        boolean addedViaNewApi = false;

        try {
            var addMethod = resourcesPan.getClass().getMethod("addResourcesTab", String.class, JPanel.class);
            addMethod.invoke(resourcesPan, tabTitle, panel);
            addedViaNewApi = true;
        } catch (NoSuchMethodException e) {
            if (resourceTabs != null) resourceTabs.addTab(tabTitle, panel);
        } catch (Exception e) {
            MenuLog.warn("addResourcesTab invocation failed on " + resourcesPan.getClass().getName()
                    + ", falling back to JTabbedPane.addTab");
            if (resourceTabs != null) resourceTabs.addTab(tabTitle, panel);
        }

        if (addedViaNewApi) {
            resourceTabs = findResourceTabbedPane(mcreator);
            removeTabsByTitle(resourceTabs, tabTitle, panel);
        }
        if (resourceTabs != null) {
            resourceTabs.revalidate();
            resourceTabs.repaint();
        }
        return panel;
    }

    public static void removeResourceTab(MCreator mcreator, JPanel panelInstance) {
        JTabbedPane resourceTabs = findResourceTabbedPane(mcreator);
        if (resourceTabs == null) return;

        if (panelInstance != null) {
            for (int i = 0; i < resourceTabs.getTabCount(); i++) {
                if (resourceTabs.getComponentAt(i) == panelInstance) {
                    resourceTabs.remove(i);
                    resourceTabs.revalidate();
                    resourceTabs.repaint();
                    return;
                }
            }
        }
    }

    @FunctionalInterface
    public interface ResourceTabPanelFactory {
        JPanel create(MCreator mcreator);
    }
}

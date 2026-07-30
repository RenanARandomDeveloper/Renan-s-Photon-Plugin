package net.renan.photonplugin;

import net.mcreator.ui.MCreator;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

public final class WorkspaceRegistry {
    private static final Map<MCreator, File> ACTIVE_WORKSPACES = new WeakHashMap<>();
    private static boolean listenerInstalled = false;

    private WorkspaceRegistry() {
    }

    public static synchronized void register(MCreator mcreator, File workspaceFolder) {
        ensureListenerInstalled();
        ACTIVE_WORKSPACES.put(mcreator, workspaceFolder);
        Log.bindWorkspace(workspaceFolder);
    }

    public static synchronized void unregister(MCreator mcreator) {
        ACTIVE_WORKSPACES.remove(mcreator);
    }

    public static synchronized File workspaceOf(MCreator mcreator) {
        return ACTIVE_WORKSPACES.get(mcreator);
    }

    private static void ensureListenerInstalled() {
        if (listenerInstalled) {
            return;
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof WindowEvent windowEvent)) {
                return;
            }
            if (windowEvent.getID() != WindowEvent.WINDOW_ACTIVATED
                    && windowEvent.getID() != WindowEvent.WINDOW_GAINED_FOCUS) {
                return;
            }
            if (!(windowEvent.getSource() instanceof MCreator mcreator)) {
                return;
            }

            File workspaceFolder;
            synchronized (WorkspaceRegistry.class) {
                workspaceFolder = ACTIVE_WORKSPACES.get(mcreator);
            }
            if (workspaceFolder != null) {
                Log.bindWorkspace(workspaceFolder);
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK);

        listenerInstalled = true;
    }
}

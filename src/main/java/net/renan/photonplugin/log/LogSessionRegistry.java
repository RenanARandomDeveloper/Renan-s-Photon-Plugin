package net.renan.photonplugin.log;

import net.mcreator.ui.MCreator;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class LogSessionRegistry {

    private static final Map<String, LogSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ThreadLocal<LogSession> CURRENT = new ThreadLocal<>();
    private static final LogSession UNBOUND = new LogSession();

    private LogSessionRegistry() {
    }

    static void bindWorkspace(File workspaceFolder) {
        if (workspaceFolder == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(sessionFor(workspaceFolder));
    }

    static void unbindWorkspace() {
        CURRENT.remove();
    }

    static AutoCloseable bindWorkspaceScope(File workspaceFolder) {
        LogSession previous = CURRENT.get();
        CURRENT.set(sessionFor(workspaceFolder));
        return () -> {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        };
    }

    static void releaseWorkspace(File workspaceFolder) {
        String key = sessionKey(workspaceFolder);
        if (key == null) {
            return;
        }
        LogSession session = SESSIONS.remove(key);
        if (session != null) {
            session.markReleased();
            session.closeWriter();
        }
    }

    static LogSession sessionFor(File workspaceFolder) {
        if (workspaceFolder == null) {
            return UNBOUND;
        }
        String key = sessionKey(workspaceFolder);

        LogSession existing = SESSIONS.get(key);
        if (existing != null) {
            return existing;
        }
        return SESSIONS.computeIfAbsent(key, k -> LogSessionFactory.create(workspaceFolder));
    }

    static <T> T withWorkspace(File workspaceFolder, Supplier<T> task) {
        LogSession previous = CURRENT.get();
        CURRENT.set(sessionFor(workspaceFolder));
        try {
            return task.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    static void withWorkspace(File workspaceFolder, Runnable task) {
        withWorkspace(workspaceFolder, () -> {
            task.run();
            return null;
        });
    }

    static LogSession current() {
        LogSession bound = CURRENT.get();
        if (bound != null) {
            if (!bound.isReleased()) {
                return bound;
            }
            CURRENT.remove();
        }

        File workspaceFolder = activeWorkspaceFolder();
        return sessionFor(workspaceFolder);
    }

    private static String sessionKey(File workspaceFolder) {
        if (workspaceFolder == null) {
            return null;
        }
        try {
            return workspaceFolder.getCanonicalPath();
        } catch (IOException e) {
            return workspaceFolder.getAbsolutePath();
        }
    }

    private static File activeWorkspaceFolder() {
        try {
            Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            while (window != null) {
                if (window instanceof MCreator mcreator) {
                    return mcreator.getWorkspaceFolder();
                }
                window = window.getOwner();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

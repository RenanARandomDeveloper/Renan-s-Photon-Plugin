package net.renan.photonplugin.menus.resource.core.support;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public final class EdtSupport {

    private EdtSupport() {
    }

    public static <T> T onEdt(Supplier<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.get();
        }
        final Object[] box = new Object[1];
        try {
            SwingUtilities.invokeAndWait(() -> box[0] = task.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException ite) {
            MenuLog.error("Error while showing a dialog from a background task", ite.getCause());
        }
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }

    public static void onEdtVoid(Runnable task) {
        onEdt(() -> {
            task.run();
            return null;
        });
    }

    public static void runFileTaskInBackground(Component parent, Runnable ioWork, Runnable onDone) {
        Component root = SwingUtilities.getRoot(parent);
        Component cursorTarget = (root != null) ? root : parent;
        Cursor previousCursor = cursorTarget.getCursor();
        cursorTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                ioWork.run();
                return null;
            }

            @Override
            protected void done() {
                cursorTarget.setCursor(previousCursor);
                try {
                    get();
                } catch (Exception ex) {
                    MenuLog.error("Unexpected error during background file operation", ex);
                }
                onDone.run();
            }
        }.execute();
    }
}

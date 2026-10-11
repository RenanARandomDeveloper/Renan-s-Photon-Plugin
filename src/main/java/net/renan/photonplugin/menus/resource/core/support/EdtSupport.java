package net.renan.photonplugin.menus.resource.core.support;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public final class EdtSupport {

    private static final ThreadLocal<Boolean> LAST_CALL_FAILED = ThreadLocal.withInitial(() -> false);

    private EdtSupport() {
    }

    public static <T> T onEdt(Supplier<T> task) {
        LAST_CALL_FAILED.set(false);
        if (SwingUtilities.isEventDispatchThread()) {
            return task.get();
        }
        final Object[] box = new Object[1];
        try {
            SwingUtilities.invokeAndWait(() -> box[0] = task.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LAST_CALL_FAILED.set(true);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            MenuLog.error("Error while showing a dialog from a background task", ite.getCause());
            LAST_CALL_FAILED.set(true);
        }
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }

    public static int onEdtChoice(Supplier<Integer> task) {
        Integer result = onEdt(task);
        return result != null ? result : JOptionPane.CLOSED_OPTION;
    }

    public static boolean lastCallFailed() {
        return LAST_CALL_FAILED.get();
    }

    public static boolean isAborted() {
        return Thread.currentThread().isInterrupted() || LAST_CALL_FAILED.get();
    }

    public static void onEdtVoid(Runnable task) {
        onEdt(() -> {
            task.run();
            return null;
        });
    }

    public static void runFileTaskInBackground(Component parent, Runnable ioWork, Runnable onDone) {
        Component root = parent != null ? SwingUtilities.getRoot(parent) : null;
        Component cursorTarget = root != null ? root : parent;
        Cursor previousCursor = cursorTarget != null ? cursorTarget.getCursor() : null;
        if (cursorTarget != null) {
            cursorTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        try {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    ioWork.run();
                    return null;
                }

                @Override
                protected void done() {
                    restoreCursor(cursorTarget, previousCursor);
                    try {
                        get();
                    } catch (Exception ex) {
                        MenuLog.error("Unexpected error during background file operation", ex);
                    }
                    onDone.run();
                }
            }.execute();
        } catch (RuntimeException ex) {
            restoreCursor(cursorTarget, previousCursor);
            MenuLog.error("Could not start a background file operation", ex);
            onDone.run();
        }
    }

    private static void restoreCursor(Component target, Cursor cursor) {
        if (target != null && cursor != null) {
            target.setCursor(cursor);
        }
    }
}

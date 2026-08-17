package net.renan.photonplugin.menus.workspace.core;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.function.Consumer;

public final class SwingWorkers {
    private SwingWorkers() {
    }

    public static <T> void runInBackground(Window owner, BackgroundTask<T> task, Consumer<T> onSuccess,
                                           Consumer<IOException> onFailure) {
        runInBackground(owner, new JComponent[0], task, onSuccess, onFailure);
    }

    public static <T> void runInBackground(Window owner, JComponent[] componentsToToggle, BackgroundTask<T> task,
                                           Consumer<T> onSuccess, Consumer<IOException> onFailure) {
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setEnabled(componentsToToggle, false);
        new SwingWorker<T, Void>() {
            private IOException failure;

            @Override
            protected T doInBackground() {
                try {
                    return task.run();
                } catch (IOException e) {
                    failure = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                owner.setCursor(Cursor.getDefaultCursor());
                setEnabled(componentsToToggle, true);
                if (failure != null) {
                    onFailure.accept(failure);
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception e) {
                    onFailure.accept(new IOException(e));
                }
            }
        }.execute();
    }

    private static void setEnabled(JComponent[] components, boolean enabled) {
        for (JComponent component : components) {
            if (component != null) {
                component.setEnabled(enabled);
            }
        }
    }
}

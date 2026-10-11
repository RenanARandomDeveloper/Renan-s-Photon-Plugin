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

        JDialog dialogOwner = owner instanceof JDialog ? (JDialog) owner : null;
        int previousCloseOperation = -1;
        if (dialogOwner != null) {
            previousCloseOperation = dialogOwner.getDefaultCloseOperation();
            dialogOwner.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            DialogUtils.setEscapeCloseEnabled(dialogOwner, false);
        }
        int finalPreviousCloseOperation = previousCloseOperation;

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
                if (owner.isDisplayable()) {
                    owner.setCursor(Cursor.getDefaultCursor());
                }
                setEnabled(componentsToToggle, true);
                if (dialogOwner != null) {
                    dialogOwner.setDefaultCloseOperation(finalPreviousCloseOperation);
                    DialogUtils.setEscapeCloseEnabled(dialogOwner, true);
                }

                if (failure != null) {
                    onFailure.accept(failure);
                    return;
                }

                T result;
                try {
                    result = get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    onFailure.accept(cause instanceof IOException ? (IOException) cause : new IOException(cause));
                    return;
                }

                onSuccess.accept(result);
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
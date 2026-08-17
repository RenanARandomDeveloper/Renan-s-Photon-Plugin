package net.renan.photonplugin.menus.resource.core.browser;

import net.renan.photonplugin.menus.resource.core.support.MenuLog;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class FileDropSupport {

    private FileDropSupport() {
    }

    public static void install(Component target, FXBrowserPanel browser, String targetDescription,
                                Supplier<File> destinationSupplier, BooleanSupplier extraCondition,
                                Runnable onDragEnter, Runnable onDragExit) {
        new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (canAccept(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                    if (onDragEnter != null) onDragEnter.run();
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                if (onDragExit != null) onDragExit.run();
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                if (canAccept(dtde)) dtde.acceptDrag(DnDConstants.ACTION_MOVE);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                if (onDragExit != null) onDragExit.run();
                if (!browser.isDragAndDropEnabled() || (extraCondition != null && !extraCondition.getAsBoolean())) {
                    dtde.dropComplete(false);
                    return;
                }
                File dest = destinationSupplier.get();
                if (dest == null) {
                    dtde.dropComplete(false);
                    return;
                }
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    browser.performMoveWithFeedback(files, dest);
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    MenuLog.warn("Drag-and-drop onto " + targetDescription + " failed: " + ex.getMessage());
                    dtde.dropComplete(false);
                }
            }

            private boolean canAccept(DropTargetDragEvent dtde) {
                return browser.isDragAndDropEnabled() && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        && (extraCondition == null || extraCondition.getAsBoolean());
            }
        });
    }
}

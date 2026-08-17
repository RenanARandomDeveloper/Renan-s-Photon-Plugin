package net.renan.photonplugin.menus.resource.core.support;

import net.renan.photonplugin.WatchService;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;
import net.renan.photonplugin.menus.resource.core.fs.FileOps;
import net.renan.photonplugin.menus.resource.core.naming.FileNameParts;
import net.renan.photonplugin.menus.resource.core.naming.FileNameRules;
import net.renan.photonplugin.menus.resource.core.naming.StrictNameEnforcer;

import javax.swing.*;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public final class DirectoryWatcher {

    private DirectoryWatcher() {
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter) {
        attachDirectoryWatcher(panel, browser, targetDirGetter, null);
    }

    public static void attachDirectoryWatcher(JPanel panel, FXBrowserPanel browser, Supplier<File> targetDirGetter,
                                               WatchService.ChangeListener extraListener) {
        File dir = targetDirGetter.get();
        if (dir == null) return;
        if (!dir.exists() && !dir.mkdirs()) {
            MenuLog.warn("Could not create watched directory: " + dir.getAbsolutePath());
        }

        StrictNameEnforcer.validateExistingFileNamesBlocking(panel, dir, browser.getExtension());

        browser.refresh();

        WatchService watchService = new WatchService();
        watchService.start(List.of(dir.toPath()), p -> false, (root, changed, kind) -> {
            if (kind == WatchService.ChangeKind.CREATE) {
                File changedFile = changed.toFile();
                if (changedFile.isFile() && FileNameRules.requiresStrictNaming(browser.getExtension())
                        && FileNameRules.hasExtension(changedFile, browser.getExtension())) {

                    FileNameParts initialParts = FileNameParts.of(changedFile);
                    boolean nameAlreadyValid = FileNameRules.isValidStrictFileName(initialParts.baseName())
                            && initialParts.extension().equals(browser.getExtension());

                    if (!nameAlreadyValid) {
                        File result;
                        browser.beginInternalMutation();
                        try {
                            result = StrictNameEnforcer.enforceValidNameBlocking(panel, changedFile, browser.getExtension());
                        } finally {
                            browser.endInternalMutation();
                        }
                        if (!FileOps.isSamePathExact(result, changedFile)) {
                            SwingUtilities.invokeLater(browser::refresh);
                            return;
                        }
                    }
                }
            }

            if (extraListener != null) extraListener.onChange(root, changed, kind);
            if (browser.isWatcherRefreshSuppressed()) return;
            SwingUtilities.invokeLater(browser::refresh);
        });

        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !panel.isDisplayable()) {
                Thread stopper = new Thread(watchService::stop, "Photon-WatchService-Stop");
                stopper.setDaemon(true);
                stopper.start();
            }
        });
    }
}

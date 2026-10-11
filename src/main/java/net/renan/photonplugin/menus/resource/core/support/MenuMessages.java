package net.renan.photonplugin.menus.resource.core.support;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.browser.FXBrowserPanel;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MenuMessages {

    private MenuMessages() {
    }

    public static String tOr(String key, String fallback, Object... args) {
        String value;
        try {
            value = L10N.t(key, args);
        } catch (RuntimeException ex) {
            value = null;
        }
        if (value == null || value.isBlank() || value.equals(key) || value.equals("!" + key + "!")) {
            return args == null || args.length == 0 ? fallback : java.text.MessageFormat.format(fallback, args);
        }
        return value;
    }

    private static String andMore() {
        return "\n  " + tOr("plugin.photon.resourcemenu.and.more", "... and more");
    }

    public static String buildFailureSummary(List<String> failedNames) {
        if (failedNames == null || failedNames.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n").append(tOr("plugin.photon.resourcemenu.operation.failed.items",
                "{0} item(s) could not be processed:", failedNames.size()));
        int shown = Math.min(failedNames.size(), 10);
        for (int i = 0; i < shown; i++) {
            sb.append("\n    ").append(failedNames.get(i));
        }
        if (failedNames.size() > shown) {
            sb.append(andMore());
        }
        return sb.toString();
    }

    public static String buildItemLabel(int fileCount, int folderCount) {
        if (fileCount == 0 && folderCount == 0) return "0";
        StringBuilder sb = new StringBuilder();
        if (fileCount > 0) {
            sb.append(fileCount).append(' ').append(fileCount == 1
                    ? L10N.t("plugin.photon.resourcemenu.item.type.file")
                    : L10N.t("plugin.photon.resourcemenu.item.type.files"));
        }
        if (fileCount > 0 && folderCount > 0) {
            sb.append(' ').append(L10N.t("plugin.photon.resourcemenu.and")).append(' ');
        }
        if (folderCount > 0) {
            sb.append(folderCount).append(' ').append(folderCount == 1
                    ? L10N.t("plugin.photon.resourcemenu.item.type.folder")
                    : L10N.t("plugin.photon.resourcemenu.item.type.folders"));
        }
        return sb.toString();
    }

    public static String buildDeleteConfirmationMessage(FXBrowserPanel browser, List<File> fileTargets, List<File> folderTargets) {
        StringBuilder msgBuilder = new StringBuilder();
        if (fileTargets.size() == 1) {
            msgBuilder.append(L10N.t("plugin.photon.resourcemenu.delete.confirm.single.file", fileTargets.get(0).getName()));
        } else {
            msgBuilder.append(L10N.t("plugin.photon.resourcemenu.delete.confirm.multiple.files", fileTargets.size()));
            int shown = Math.min(fileTargets.size(), 10);
            for (int j = 0; j < shown; j++) {
                msgBuilder.append("\n    ").append(fileTargets.get(j).getName());
            }
            if (fileTargets.size() > shown) {
                msgBuilder.append(andMore());
            }
        }
        if (!folderTargets.isEmpty()) {
            msgBuilder.append("\n\n").append(L10N.t("plugin.photon.resourcemenu.delete.folders.also.selected", folderTargets.size())).append(":");
            int shownFolders = Math.min(folderTargets.size(), 10);
            for (int j = 0; j < shownFolders; j++) {
                msgBuilder.append("\n    \uD83D\uDCC2 ").append(browser.getFolderName(folderTargets.get(j))).append("/");
            }
            if (folderTargets.size() > shownFolders) {
                msgBuilder.append(andMore());
            }
        }
        return msgBuilder.toString();
    }

    public static String buildFolderContentsPreviewMessage(String progressPrefix, String folderLabel, List<File> contents) {
        StringBuilder folderMsg = new StringBuilder(
                progressPrefix + L10N.t(
                        "plugin.photon.resourcemenu.delete.folder.nonempty.question",
                        folderLabel, contents.size()));

        List<File> sorted = new ArrayList<>(contents);
        sorted.sort(Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        int shown = Math.min(sorted.size(), 10);
        for (int k = 0; k < shown; k++) {
            File child = sorted.get(k);
            if (child.isDirectory()) {
                folderMsg.append("\n  \uD83D\uDCC2 ").append(child.getName()).append("/");
            } else {
                folderMsg.append("\n    ").append(child.getName());
            }
        }
        if (sorted.size() > shown) {
            folderMsg.append(andMore());
        }
        return folderMsg.toString();
    }
}

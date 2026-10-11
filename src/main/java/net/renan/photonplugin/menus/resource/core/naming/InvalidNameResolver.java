package net.renan.photonplugin.menus.resource.core.naming;

import net.mcreator.ui.init.L10N;
import net.renan.photonplugin.menus.resource.core.support.EdtSupport;

import javax.swing.*;
import java.awt.*;

public class InvalidNameResolver {

    private static final int MIN_ITEMS_FOR_APPLY_ALL = 2;

    private final boolean showApplyAll;
    private Boolean globalAutoFix = null;

    public InvalidNameResolver(int totalItems) {
        this.showApplyAll = totalItems >= MIN_ITEMS_FOR_APPLY_ALL;
    }

    private static boolean isNameValid(String name, boolean strict) {
        return strict ? FileNameRules.isValidStrictFileName(name) : FileNameRules.isValidLooseFileName(name);
    }

    private static String sanitizeName(String name, boolean strict) {
        return strict ? FileNameRules.sanitizeStrictFileName(name) : FileNameRules.sanitizeLooseFileName(name);
    }

    public String resolve(Component parent, String originalName) {
        return resolve(parent, originalName, null, true);
    }

    public String resolve(Component parent, String originalName, String progress) {
        return resolve(parent, originalName, progress, true);
    }

    public String resolve(Component parent, String originalName, boolean strict) {
        return resolve(parent, originalName, null, strict);
    }

    public String resolve(Component parent, String originalName, String progress, boolean strict) {
        String name = originalName;
        if (isNameValid(name, strict)) return name;

        String messagePrefix = (progress != null && !progress.isEmpty()) ? progress + "  " : "";

        while (!isNameValid(name, strict)) {
            if (globalAutoFix != null) {
                return globalAutoFix ? sanitizeName(name, strict) : null;
            }

            String suggestion = sanitizeName(name, strict);

            JCheckBox applyAll = showApplyAll ? new JCheckBox(L10N.t("plugin.photon.resourcemenu.overwrite.apply.to.all")) : null;
            String message = messagePrefix + (strict
                    ? FileNameRules.buildInvalidNameMessage(
                    "plugin.photon.resourcemenu.name.invalid.chars.message",
                    "plugin.photon.resourcemenu.name.invalid.case.message",
                    name, suggestion)
                    : FileNameRules.buildInvalidNameMessageLoose(name, suggestion));
            Object[] content = showApplyAll ? new Object[]{message, applyAll} : new Object[]{message};
            Object[] options = {
                    L10N.t("plugin.photon.resourcemenu.name.auto.fix"),
                    L10N.t("plugin.photon.resourcemenu.name.type.manually"),
                    L10N.t("plugin.photon.resourcemenu.operation.cancel.button")
            };
            int choice = EdtSupport.onEdtChoice(() -> JOptionPane.showOptionDialog(
                    parent, content,
                    L10N.t("plugin.photon.resourcemenu.operation.warning"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]));

            if (choice == 0) {
                if (applyAll != null && applyAll.isSelected()) globalAutoFix = true;
                return suggestion;
            } else if (choice == 1) {
                String manualEntryKey = strict
                        ? "plugin.photon.resourcemenu.name.manual.entry.message"
                        : "plugin.photon.resourcemenu.name.manual.entry.loose.message";
                String typed = EdtSupport.onEdt(() -> JOptionPane.showInputDialog(parent,
                        messagePrefix + L10N.t(manualEntryKey), suggestion));
                if (typed == null) {
                    if (EdtSupport.isAborted()) return null;
                    continue;
                }
                typed = typed.trim();
                if (typed.isEmpty()) {
                    EdtSupport.onEdtVoid(() -> JOptionPane.showMessageDialog(parent,
                            messagePrefix + " " + L10N.t("plugin.photon.resourcemenu.rename.empty.name.error"),
                            L10N.t("plugin.photon.resourcemenu.operation.error"), JOptionPane.ERROR_MESSAGE));
                    continue;
                }
                name = typed;
            } else {
                if (applyAll != null && applyAll.isSelected()) globalAutoFix = false;
                return null;
            }
        }
        return name;
    }
}

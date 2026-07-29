package net.renan.photonplugin.workspaceclass;

import java.util.List;

public final class WorkspaceConstants {

    private WorkspaceConstants() {}

    public static final String TARGET_SUB_PACKAGE = "photon_plugin";

    private static final List<String> BASE_CLASS_NAMES = List.of(
            "ClientEffectScheduler",
            "SummonFXEntityServer", "SummonFXBlockServer",
            "RemoveFXEntityServer", "RemoveFXBlockServer",
            "SummonFXEntityClient", "SummonFXBlockClient",
            "RemoveFXEntityClient", "RemoveFXBlockClient",
            "CheckFXEntity", "CheckFXBlock"
    );

    public static List<String> getBaseClassNames() {
        return BASE_CLASS_NAMES;
    }

    public static String getTemplateName(String baseName) {
        return baseName + ".java.ftl";
    }

    public static String getJavaFileName(String baseName) {
        return baseName + ".java";
    }
}
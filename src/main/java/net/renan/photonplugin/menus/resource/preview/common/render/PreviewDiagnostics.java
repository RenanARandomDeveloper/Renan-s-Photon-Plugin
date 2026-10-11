package net.renan.photonplugin.menus.resource.preview.common.render;

public interface PreviewDiagnostics {
    PreviewDiagnostics SILENT = message -> {
    };
    PreviewDiagnostics CONSOLE = System.out::println;

    void warn(String message);
}

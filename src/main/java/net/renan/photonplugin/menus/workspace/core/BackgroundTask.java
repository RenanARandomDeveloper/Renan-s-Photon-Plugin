package net.renan.photonplugin.menus.workspace.core;

import java.io.IOException;

@FunctionalInterface
public interface BackgroundTask<T> {
    T run() throws IOException;
}

package net.renan.photonplugin.menus.resource.core.support;

import net.renan.photonplugin.log.Log;

public final class MenuLog {

    private MenuLog() {
    }

    public static void info(String message) {
        Log.info("%s", message);
    }

    public static void warn(String message) {
        Log.warn("%s", message);
    }

    public static void error(String message, Throwable throwable) {
        Log.error(message, throwable);
    }
}

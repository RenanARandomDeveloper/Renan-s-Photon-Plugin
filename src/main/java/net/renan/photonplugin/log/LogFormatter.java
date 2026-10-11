package net.renan.photonplugin.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IllegalFormatException;
import java.util.Locale;

final class LogFormatter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LOG_PACKAGE_PREFIX = LogFormatter.class.getPackageName();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private LogFormatter() {
    }

    static String formatLine(String level, String caller, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        return "[" + timestamp + "] [" + caller + "] [" + level + "] " + message;
    }

    static String safeFormat(String format, Object... args) {
        if (format == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return format;
        }
        try {
            return String.format(Locale.ROOT, format, args);
        } catch (IllegalFormatException e) {
            LogInternal.LOGGER.warn("[Photon] Invalid log format string, using raw message instead", e);
            return format;
        }
    }

    static String getCallerClassName() {
        return STACK_WALKER.walk(stream -> stream
                .map(StackWalker.StackFrame::getClassName)
                .filter(className -> !isInLogPackage(className))
                .findFirst()
                .map(className -> className.substring(className.lastIndexOf('.') + 1))
                .orElse("Unknown"));
    }

    private static boolean isInLogPackage(String className) {
        int lastDot = className.lastIndexOf('.');
        String packageName = lastDot >= 0 ? className.substring(0, lastDot) : "";
        return packageName.equals(LOG_PACKAGE_PREFIX);
    }
}
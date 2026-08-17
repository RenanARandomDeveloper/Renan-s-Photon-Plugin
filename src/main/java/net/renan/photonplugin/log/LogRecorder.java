package net.renan.photonplugin.log;

final class LogRecorder {

    private LogRecorder() {
    }

    static void record(LogSession session, String level, String message) {
        String caller = LogFormatter.getCallerClassName();
        String line = LogFormatter.formatLine(level, caller, message);
        boolean forceFlush = !"INFO".equals(level);
        session.record(line, forceFlush);
    }
}

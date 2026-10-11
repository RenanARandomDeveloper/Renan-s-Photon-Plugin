package net.renan.photonplugin.log;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

public final class Log {

    private Log() {
    }

    public static void bindWorkspace(File workspaceFolder) {
        LogSessionRegistry.bindWorkspace(workspaceFolder);
    }

    public static void unbindWorkspace() {
        LogSessionRegistry.unbindWorkspace();
    }

    public static AutoCloseable bindWorkspaceScope(File workspaceFolder) {
        return LogSessionRegistry.bindWorkspaceScope(workspaceFolder);
    }

    public static void releaseWorkspace(File workspaceFolder) {
        LogSessionRegistry.releaseWorkspace(workspaceFolder);
    }

    public static <T> T withWorkspace(File workspaceFolder, Supplier<T> task) {
        return LogSessionRegistry.withWorkspace(workspaceFolder, task);
    }

    public static void withWorkspace(File workspaceFolder, Runnable task) {
        LogSessionRegistry.withWorkspace(workspaceFolder, task);
    }

    public static boolean hasActiveSessionFor(File workspaceFolder) {
        return LogSessionRegistry.peekSessionFor(workspaceFolder) != null;
    }

    public static File getLogFileFor(File workspaceFolder) {
        return LogSessionRegistry.sessionFor(workspaceFolder).getLogFile();
    }

    public static File getLogsFolderFor(File workspaceFolder) {
        return LogSessionRegistry.sessionFor(workspaceFolder).getLogsFolder();
    }

    public static List<File> listAvailableLogFilesFor(File workspaceFolder) {
        return LogArchiver.listAvailableLogFiles(LogSessionRegistry.sessionFor(workspaceFolder).getLogsFolder());
    }

    public static long getTotalLinesRecordedFor(File workspaceFolder) {
        return LogSessionRegistry.sessionFor(workspaceFolder).getTotalLinesRecorded();
    }

    public static List<String> getBufferedLogSinceFor(File workspaceFolder, long sinceTotalCount) {
        return LogSessionRegistry.sessionFor(workspaceFolder).getBufferedSince(sinceTotalCount);
    }

    public static String readPersistedLogFor(File workspaceFolder) {
        return readPersisted(LogSessionRegistry.sessionFor(workspaceFolder));
    }

    public static AutoCloseable addLogListener(File workspaceFolder, Runnable onNewLine) {
        LogSession session = LogSessionRegistry.sessionFor(workspaceFolder);
        session.addListener(onNewLine);
        return () -> session.removeListener(onNewLine);
    }

    public static File getLogFile() {
        return LogSessionRegistry.current().getLogFile();
    }

    public static File getLogsFolder() {
        return LogSessionRegistry.current().getLogsFolder();
    }

    public static List<File> listAvailableLogFiles() {
        return LogArchiver.listAvailableLogFiles(LogSessionRegistry.current().getLogsFolder());
    }

    public static List<String> getBufferedLog() {
        return LogSessionRegistry.current().snapshotBuffer();
    }

    public static long getTotalLinesRecorded() {
        return LogSessionRegistry.current().getTotalLinesRecorded();
    }

    public static List<String> getBufferedLogSince(long sinceTotalCount) {
        return LogSessionRegistry.current().getBufferedSince(sinceTotalCount);
    }

    public static String readPersistedLog() {
        return readPersisted(LogSessionRegistry.current());
    }

    private static String readPersisted(LogSession session) {
        session.flushSync(LogConfig.FLUSH_WAIT_TIMEOUT_MS);

        File logFile = session.getLogFile();
        if (logFile != null && logFile.exists()) {
            try {
                return LogArchiver.readTail(logFile, LogConfig.MAX_PERSISTED_READ_BYTES);
            } catch (IOException e) {
                LogInternal.LOGGER.error("[Photon] Failed to read log file", e);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : session.snapshotBuffer()) {
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

    public static LocalDateTime getLogCreationDate(File file) {
        return LogArchiver.getLogCreationDate(file);
    }

    public static void exportLogs(List<File> selectedLogs, File destination) throws IOException {
        LogArchiver.exportLogs(selectedLogs, destination);
    }

    public static void info(String format, Object... args) {
        LogRecorder.record(LogSessionRegistry.current(), "INFO", LogFormatter.safeFormat(format, args));
    }

    public static void warn(String format, Object... args) {
        LogRecorder.record(LogSessionRegistry.current(), "WARN", LogFormatter.safeFormat(format, args));
    }

    public static void error(String message) {
        LogRecorder.record(LogSessionRegistry.current(), "ERROR", message);
    }

    public static void error(String message, Throwable throwable) {
        if (throwable == null) {
            error(message);
            return;
        }
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        LogRecorder.record(LogSessionRegistry.current(), "ERROR", message + System.lineSeparator() + stackTrace);
    }
}
package net.renan.photonplugin;

import net.mcreator.ui.MCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class Log {
    private static final Logger LOGGER = LogManager.getLogger("Photon");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("photon_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.(log|zip)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_MEMORY_LINES = 10000;
    private static final int MAX_LOG_FILES = 10;

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final Session UNBOUND = new Session();

    private Log() {
    }

    private static final class Session {
        final List<String> buffer = Collections.synchronizedList(new LinkedList<>());
        final List<Runnable> listeners = Collections.synchronizedList(new ArrayList<>());
        volatile File logDirectory;
        volatile File logsFolder;
        volatile File logFile;
        long totalLinesRecorded = 0;
        Writer activeWriter;
        File activeWriterFile;
    }

    public static void bindWorkspace(File workspaceFolder) {
        if (workspaceFolder == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(SESSIONS.computeIfAbsent(sessionKey(workspaceFolder), key -> createSession(workspaceFolder)));
    }

    public static void unbindWorkspace() {
        CURRENT.remove();
    }

    public static synchronized void releaseWorkspace(File workspaceFolder) {
        String key = sessionKey(workspaceFolder);
        if (key == null) {
            return;
        }
        Session session = SESSIONS.remove(key);
        if (session != null) {
            closeWriter(session);
        }
    }

    private static String sessionKey(File workspaceFolder) {
        if (workspaceFolder == null) {
            return null;
        }
        try {
            return workspaceFolder.getCanonicalPath();
        } catch (IOException e) {
            return workspaceFolder.getAbsolutePath();
        }
    }

    private static Session sessionFor(File workspaceFolder) {
        if (workspaceFolder == null) {
            return UNBOUND;
        }
        return SESSIONS.computeIfAbsent(sessionKey(workspaceFolder), key -> createSession(workspaceFolder));
    }

    public static <T> T withWorkspace(File workspaceFolder, java.util.function.Supplier<T> task) {
        Session previous = CURRENT.get();
        CURRENT.set(sessionFor(workspaceFolder));
        try {
            return task.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void withWorkspace(File workspaceFolder, Runnable task) {
        withWorkspace(workspaceFolder, () -> {
            task.run();
            return null;
        });
    }

    private static Session current() {
        Session bound = CURRENT.get();
        if (bound != null) {
            return bound;
        }

        File workspaceFolder = activeWorkspaceFolder();
        if (workspaceFolder == null) {
            return UNBOUND;
        }

        return SESSIONS.computeIfAbsent(sessionKey(workspaceFolder), key -> createSession(workspaceFolder));
    }

    private static File activeWorkspaceFolder() {
        try {
            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (activeWindow instanceof MCreator mcreator) {
                return mcreator.getWorkspaceFolder();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Session createSession(File workspaceFolder) {
        Session session = new Session();

        File photonFolder = new File(workspaceFolder, ".photon");
        if (!photonFolder.exists() && !photonFolder.mkdirs()) {
            LOGGER.error("[Photon] Failed to create .photon folder at {}", photonFolder.getAbsolutePath());
        }

        File logsDir = new File(photonFolder, "logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            LOGGER.error("[Photon] Failed to create logs folder at {}", logsDir.getAbsolutePath());
        }

        session.logDirectory = photonFolder;
        session.logsFolder = logsDir;

        String fileName = "photon_" + LocalDateTime.now().format(FILE_NAME_FORMAT) + ".log";
        session.logFile = new File(logsDir, fileName);

        compressOldLogs(session);
        enforceLogLimit(session);

        return session;
    }

    private static void compressOldLogs(Session session) {
        if (session.logsFolder == null || !session.logsFolder.isDirectory()) {
            return;
        }

        File[] files = session.logsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null) {
            return;
        }

        for (File oldLog : files) {
            if (session.logFile != null && oldLog.getAbsolutePath().equals(session.logFile.getAbsolutePath())) {
                continue;
            }
            compressLogFile(oldLog, true);
        }
    }

    private static File compressLogFile(File source, boolean deleteSource) {
        if (source == null || !source.exists() || !source.isFile()) {
            return null;
        }

        String zipName = source.getName().replaceAll("(?i)\\.log$", "") + ".zip";
        File zipFile = new File(source.getParentFile(), zipName);

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            ZipEntry entry = new ZipEntry(source.getName());
            zos.putNextEntry(entry);

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(source))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
            }
            zos.closeEntry();
        } catch (IOException e) {
            LOGGER.error("[Photon] Failed to compress log file {}", source.getAbsolutePath(), e);
            zipFile.delete();
            return null;
        }

        if (deleteSource && !source.delete()) {
            LOGGER.error("[Photon] Failed to delete original log file after compression: {}",
                    source.getAbsolutePath());
        }

        return zipFile;
    }

    private static void enforceLogLimit(Session session) {
        if (session.logsFolder == null || !session.logsFolder.isDirectory()) {
            return;
        }

        File[] files = session.logsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".log") || lower.endsWith(".zip");
        });
        if (files == null || files.length < MAX_LOG_FILES) {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int amountToDelete = files.length - MAX_LOG_FILES + 1;
        for (int i = 0; i < amountToDelete && i < files.length; i++) {
            File file = files[i];
            if (session.logFile != null && file.getAbsolutePath().equals(session.logFile.getAbsolutePath())) {
                continue;
            }
            if (!file.delete()) {
                LOGGER.error("[Photon] Failed to delete old log file {}", file.getAbsolutePath());
            }
        }
    }

    public static LocalDateTime getLogCreationDate(File file) {
        if (file != null) {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                try {
                    return LocalDateTime.parse(matcher.group(1), FILE_NAME_FORMAT);
                } catch (Exception ignored) {
                }
            }
        }

        long lastModified = file != null ? file.lastModified() : System.currentTimeMillis();
        return LocalDateTime.ofEpochSecond(lastModified / 1000, 0,
                java.time.ZoneOffset.systemDefault().getRules().getOffset(LocalDateTime.now()));
    }

    public static File getLogsFolder() {
        return current().logsFolder;
    }

    public static List<File> listAvailableLogFiles() {
        List<File> result = new ArrayList<>();
        File logsFolder = current().logsFolder;
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return result;
        }

        File[] files = logsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".log") || lower.endsWith(".zip");
        });
        if (files == null) {
            return result;
        }

        result.addAll(Arrays.asList(files));
        result.sort(Comparator.comparing(Log::getLogCreationDate).reversed());
        return result;
    }

    public static void exportLogs(List<File> selectedLogs, File destination) throws IOException {
        if (selectedLogs == null || selectedLogs.isEmpty()) {
            throw new IOException("No log files selected for export.");
        }
        if (destination == null) {
            throw new IOException("No destination file provided for export.");
        }

        List<File> validLogs = new ArrayList<>();
        for (File selected : selectedLogs) {
            if (selected != null && selected.exists()) {
                validLogs.add(selected);
            }
        }
        if (validLogs.isEmpty()) {
            throw new IOException("None of the selected log files exist.");
        }

        if (validLogs.size() == 1) {
            String content = readLogContent(validLogs.get(0));
            Files.writeString(destination.toPath(), content, StandardCharsets.UTF_8);
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(destination);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            for (File selected : validLogs) {
                String content = readLogContent(selected);
                String entryName = selected.getName().replaceAll("(?i)\\.(log|zip)$", "") + ".txt";

                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    private static String readLogContent(File logOrArchive) throws IOException {
        String name = logOrArchive.getName().toLowerCase();

        if (name.endsWith(".zip")) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(logOrArchive))) {
                ZipEntry entry = zis.getNextEntry();
                if (entry == null) {
                    throw new IOException("Compressed log file is empty: " + logOrArchive.getAbsolutePath());
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = zis.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }

        return Files.readString(logOrArchive.toPath(), StandardCharsets.UTF_8);
    }

    public static File getLogFile() {
        return current().logFile;
    }

    public static File getLogFileFor(File workspaceFolder) {
        return sessionFor(workspaceFolder).logFile;
    }

    public static File getLogsFolderFor(File workspaceFolder) {
        return sessionFor(workspaceFolder).logsFolder;
    }

    public static List<File> listAvailableLogFilesFor(File workspaceFolder) {
        List<File> result = new ArrayList<>();
        File logsFolder = sessionFor(workspaceFolder).logsFolder;
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return result;
        }

        File[] files = logsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".log") || lower.endsWith(".zip");
        });
        if (files == null) {
            return result;
        }

        result.addAll(Arrays.asList(files));
        result.sort(Comparator.comparing(Log::getLogCreationDate).reversed());
        return result;
    }

    public static long getTotalLinesRecordedFor(File workspaceFolder) {
        Session session = sessionFor(workspaceFolder);
        synchronized (session.buffer) {
            return session.totalLinesRecorded;
        }
    }

    public static List<String> getBufferedLogSinceFor(File workspaceFolder, long sinceTotalCount) {
        Session session = sessionFor(workspaceFolder);
        synchronized (session.buffer) {
            long oldestAvailableTotal = session.totalLinesRecorded - session.buffer.size();
            if (sinceTotalCount < oldestAvailableTotal) {
                return null;
            }
            int skip = (int) (sinceTotalCount - oldestAvailableTotal);
            if (skip >= session.buffer.size()) {
                return List.of();
            }
            return new ArrayList<>(session.buffer.subList(skip, session.buffer.size()));
        }
    }

    public static String readPersistedLogFor(File workspaceFolder) {
        Session session = sessionFor(workspaceFolder);
        File logFile = session.logFile;
        if (logFile != null && logFile.exists()) {
            try {
                return Files.readString(logFile.toPath());
            } catch (IOException e) {
                LOGGER.error("[Photon] Failed to read log file", e);
            }
        }

        StringBuilder sb = new StringBuilder();
        synchronized (session.buffer) {
            for (String line : session.buffer) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }


    public static AutoCloseable addLogListener(File workspaceFolder, Runnable onNewLine) {
        Session session = sessionFor(workspaceFolder);
        session.listeners.add(onNewLine);
        return () -> session.listeners.remove(onNewLine);
    }

    public static List<String> getBufferedLog() {
        Session session = current();
        synchronized (session.buffer) {
            return new LinkedList<>(session.buffer);
        }
    }

    public static long getTotalLinesRecorded() {
        Session session = current();
        synchronized (session.buffer) {
            return session.totalLinesRecorded;
        }
    }

    public static List<String> getBufferedLogSince(long sinceTotalCount) {
        Session session = current();
        synchronized (session.buffer) {
            long oldestAvailableTotal = session.totalLinesRecorded - session.buffer.size();
            if (sinceTotalCount < oldestAvailableTotal) {
                return null;
            }
            int skip = (int) (sinceTotalCount - oldestAvailableTotal);
            if (skip >= session.buffer.size()) {
                return List.of();
            }
            return new ArrayList<>(session.buffer.subList(skip, session.buffer.size()));
        }
    }

    public static String readPersistedLog() {
        File logFile = current().logFile;
        if (logFile != null && logFile.exists()) {
            try {
                return Files.readString(logFile.toPath());
            } catch (IOException e) {
                LOGGER.error("[Photon] Failed to read log file", e);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : getBufferedLog()) {
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String getCallerClassName() {
        return StackWalker.getInstance().walk(stream -> stream.map(StackWalker.StackFrame::getClassName)
                .filter(className -> !className.equals(Log.class.getName())).findFirst().
                map(className -> className.substring(className.lastIndexOf('.') + 1)).orElse("Unknown"));
    }

    private static void record(String level, String caller, String message) {
        Session session = current();
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String line = "[" + timestamp + "] [" + caller + "] [" + level + "] " + message;

        synchronized (session.buffer) {
            session.buffer.add(line);
            session.totalLinesRecorded++;
            while (session.buffer.size() > MAX_MEMORY_LINES) {
                session.buffer.remove(0);
            }
        }

        writeToFile(session, line);
        notifyListeners(session);
    }

    private static void notifyListeners(Session session) {
        if (session.listeners.isEmpty()) {
            return;
        }
        for (Runnable listener : new ArrayList<>(session.listeners)) {
            try {
                listener.run();
            } catch (Exception e) {
                LOGGER.error("[Photon] Log listener threw an exception", e);
            }
        }
    }

    private static synchronized void writeToFile(Session session, String line) {
        File target = session.logFile;
        if (target == null) {
            return;
        }

        try {
            if (session.activeWriter == null || !target.equals(session.activeWriterFile)) {
                closeWriter(session);
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                session.activeWriter = new BufferedWriter(new FileWriter(target, true));
                session.activeWriterFile = target;
            }
            session.activeWriter.write(line);
            session.activeWriter.write(System.lineSeparator());
            session.activeWriter.flush();
        } catch (IOException e) {
            LOGGER.error("[Photon] Failed to write to log file", e);
            closeWriter(session);
        }
    }

    private static synchronized void closeWriter(Session session) {
        if (session.activeWriter != null) {
            try {
                session.activeWriter.close();
            } catch (IOException e) {
                LOGGER.error("[Photon] Failed to close log file writer", e);
            }
            session.activeWriter = null;
            session.activeWriterFile = null;
        }
    }

    public static void info(String format, Object... args) {
        String caller = getCallerClassName();
        String message = String.format(format, args);
        record("INFO", caller, message);
    }

    public static void warn(String format, Object... args) {
        String caller = getCallerClassName();
        String message = String.format(format, args);
        record("WARN", caller, message);
    }

    public static void error(String message) {
        String caller = getCallerClassName();
        record("ERROR", caller, message);
    }

    public static void error(String message, Throwable throwable) {
        String caller = getCallerClassName();
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        record("ERROR", caller, message + System.lineSeparator() + stackTrace);
    }
}
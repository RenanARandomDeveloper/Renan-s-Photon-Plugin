package net.renan.photonplugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
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
    private static final List<String> LOG_BUFFER = Collections.synchronizedList(new LinkedList<>());
    private static volatile File logDirectory;
    private static volatile File logsFolder;
    private static volatile File logFile;
    private static long totalLinesRecorded = 0;
    private static Writer activeWriter;
    private static File activeWriterFile;

    private Log() {
    }

    public static synchronized void setWorkspaceFolder(File workspaceFolder) {
        closeActiveWriter();

        if (workspaceFolder == null) {
            logDirectory = null;
            logsFolder = null;
            logFile = null;
            return;
        }

        File photonFolder = new File(workspaceFolder, ".photon");
        if (!photonFolder.exists() && !photonFolder.mkdirs()) {
            LOGGER.error("[Photon] Failed to create .photon folder at {}", photonFolder.getAbsolutePath());
        }

        File logsDir = new File(photonFolder, "logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            LOGGER.error("[Photon] Failed to create logs folder at {}", logsDir.getAbsolutePath());
        }

        logDirectory = photonFolder;
        logsFolder = logsDir;

        String fileName = "photon_" + LocalDateTime.now().format(FILE_NAME_FORMAT) + ".log";
        logFile = new File(logsDir, fileName);

        compressOldLogs();
        enforceLogLimit();
    }

    private static void compressOldLogs() {
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return;
        }

        File[] files = logsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
        if (files == null) {
            return;
        }

        for (File oldLog : files) {
            if (logFile != null && oldLog.getAbsolutePath().equals(logFile.getAbsolutePath())) {
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

    private static void enforceLogLimit() {
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return;
        }

        File[] files = logsFolder.listFiles((dir, name) -> {
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
            if (logFile != null && file.getAbsolutePath().equals(logFile.getAbsolutePath())) {
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
        return logsFolder;
    }

    public static List<File> listAvailableLogFiles() {
        List<File> result = new ArrayList<>();
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

    public static synchronized void exportLogs(List<File> selectedLogs, File destination) throws IOException {
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
        return logFile;
    }

    public static List<String> getBufferedLog() {
        synchronized (LOG_BUFFER) {
            return new LinkedList<>(LOG_BUFFER);
        }
    }

    public static long getTotalLinesRecorded() {
        synchronized (LOG_BUFFER) {
            return totalLinesRecorded;
        }
    }

    public static List<String> getBufferedLogSince(long sinceTotalCount) {
        synchronized (LOG_BUFFER) {
            long oldestAvailableTotal = totalLinesRecorded - LOG_BUFFER.size();
            if (sinceTotalCount < oldestAvailableTotal) {
                return null;
            }
            int skip = (int) (sinceTotalCount - oldestAvailableTotal);
            if (skip >= LOG_BUFFER.size()) {
                return List.of();
            }
            return new ArrayList<>(LOG_BUFFER.subList(skip, LOG_BUFFER.size()));
        }
    }

    public static String readPersistedLog() {
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
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String line = "[" + timestamp + "] [" + caller + "] [" + level + "] " + message;

        synchronized (LOG_BUFFER) {
            LOG_BUFFER.add(line);
            totalLinesRecorded++;
            while (LOG_BUFFER.size() > MAX_MEMORY_LINES) {
                LOG_BUFFER.remove(0);
            }
        }

        writeToFile(line);
    }

    private static synchronized void writeToFile(String line) {
        File target = logFile;
        if (target == null) {
            return;
        }

        try {
            if (activeWriter == null || !target.equals(activeWriterFile)) {
                closeActiveWriter();
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                activeWriter = new BufferedWriter(new FileWriter(target, true));
                activeWriterFile = target;
            }
            activeWriter.write(line);
            activeWriter.write(System.lineSeparator());
            activeWriter.flush();
        } catch (IOException e) {
            LOGGER.error("[Photon] Failed to write to log file", e);
            closeActiveWriter();
        }
    }

    private static synchronized void closeActiveWriter() {
        if (activeWriter != null) {
            try {
                activeWriter.close();
            } catch (IOException e) {
                LOGGER.error("[Photon] Failed to close log file writer", e);
            }
            activeWriter = null;
            activeWriterFile = null;
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
        record("ERROR", caller, message + " - " + throwable);
    }
}
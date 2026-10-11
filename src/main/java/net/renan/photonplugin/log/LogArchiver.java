package net.renan.photonplugin.log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class LogArchiver {

    static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile(
            "photon_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3})\\.(log|zip)", Pattern.CASE_INSENSITIVE);

    private static final ExecutorService MAINTENANCE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Photon-Log-Maintenance");
        thread.setDaemon(true);
        return thread;
    });

    private LogArchiver() {
    }

    static void scheduleMaintenance(LogSession session) {
        MAINTENANCE_EXECUTOR.submit(() -> {
            try {
                compressOldLogs(session);
                enforceLogLimit(session);
            } catch (Exception e) {
                LogInternal.LOGGER.error("[Photon] Log maintenance task failed", e);
            }
        });
    }

    static void compressOldLogs(LogSession session) {
        File logsFolder = session.getLogsFolder();
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return;
        }

        File[] files = logsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".log"));
        if (files == null) {
            return;
        }

        File currentLogFile = session.getLogFile();
        for (File oldLog : files) {
            if (isSameFile(oldLog, currentLogFile)) {
                continue;
            }
            if (isTooRecentForMaintenance(oldLog)) {
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
            LogInternal.LOGGER.error("[Photon] Failed to compress log file " + source.getAbsolutePath(), e);
            zipFile.delete();
            return null;
        }

        if (deleteSource && !source.delete()) {
            LogInternal.LOGGER.error("[Photon] Failed to delete original log file after compression: {}",
                    source.getAbsolutePath());
        }

        return zipFile;
    }

    static void enforceLogLimit(LogSession session) {
        File logsFolder = session.getLogsFolder();
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return;
        }

        File[] files = logsFolder.listFiles(LogArchiver::isLogOrArchive);
        if (files == null || files.length < LogConfig.MAX_LOG_FILES) {
            return;
        }

        Arrays.sort(files, Comparator.comparing(LogArchiver::getLogCreationDate));

        File currentLogFile = session.getLogFile();
        int amountToDelete = files.length - LogConfig.MAX_LOG_FILES + 1;
        for (int i = 0; i < amountToDelete && i < files.length; i++) {
            File file = files[i];
            if (isSameFile(file, currentLogFile)) {
                continue;
            }
            if (isTooRecentForMaintenance(file)) {
                continue;
            }
            if (!file.delete()) {
                LogInternal.LOGGER.error("[Photon] Failed to delete old log file {}", file.getAbsolutePath());
            }
        }
    }

    private static boolean isTooRecentForMaintenance(File file) {
        long age = System.currentTimeMillis() - file.lastModified();
        return age >= 0 && age < LogConfig.MIN_FILE_AGE_BEFORE_MAINTENANCE_MS;
    }

    private static boolean isSameFile(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static boolean isLogOrArchive(File dir, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".log") || lower.endsWith(".zip");
    }

    static LocalDateTime getLogCreationDate(File file) {
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
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastModified), ZoneOffset.systemDefault());
    }

    static List<File> listAvailableLogFiles(File logsFolder) {
        List<File> result = new ArrayList<>();
        if (logsFolder == null || !logsFolder.isDirectory()) {
            return result;
        }

        File[] files = logsFolder.listFiles(LogArchiver::isLogOrArchive);
        if (files == null) {
            return result;
        }

        result.addAll(Arrays.asList(files));
        result.sort(Comparator.comparing(LogArchiver::getLogCreationDate).reversed());
        return result;
    }

    static void exportLogs(List<File> selectedLogs, File destination) throws IOException {
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
            File only = validLogs.get(0);
            try {
                String content = readLogContent(only);
                Files.writeString(destination.toPath(), content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IOException("Failed to export \"" + only.getName()
                        + "\" - it may have been rotated or deleted by log maintenance while exporting.", e);
            }
            return;
        }

        int exportedCount = 0;
        try (FileOutputStream fos = new FileOutputStream(destination);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            for (File selected : validLogs) {
                String content;
                try {
                    content = readLogContent(selected);
                } catch (IOException e) {
                    LogInternal.LOGGER.warn(
                            "[Photon] Skipping \"{}\" during export - it changed or was removed by log maintenance",
                            selected.getName());
                    continue;
                }

                String entryName = selected.getName().replaceAll("(?i)\\.(log|zip)$", "") + ".txt";

                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                exportedCount++;
            }
        }

        if (exportedCount == 0) {
            destination.delete();
            throw new IOException(
                    "None of the selected log files could be read during export (they may have been rotated "
                            + "or deleted by log maintenance).");
        }
    }

    private static String readLogContent(File logOrArchive) throws IOException {
        String name = logOrArchive.getName().toLowerCase(Locale.ROOT);

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

    static String readTail(File file, long maxBytes) throws IOException {
        long length = file.length();
        if (length <= maxBytes) {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = length - maxBytes;
            raf.seek(start);

            byte[] data = new byte[(int) (length - start)];
            raf.readFully(data);

            int firstNewline = indexOfByte(data, (byte) '\n');
            int contentStart = firstNewline >= 0 ? firstNewline + 1 : 0;

            String header = "[Photon] Log truncated - showing last "
                    + (maxBytes / (1024 * 1024)) + " MB" + System.lineSeparator();
            return header + new String(data, contentStart, data.length - contentStart, StandardCharsets.UTF_8);
        }
    }

    private static int indexOfByte(byte[] data, byte target) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
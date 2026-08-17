package net.renan.photonplugin.backup.core;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BackupNaming {
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    private static final Pattern BACKUP_NAME_PATTERN = Pattern.compile(
            "backup_(\\d{4}-\\d{2}-\\d{2}(?:_\\d{2}-\\d{2}-\\d{2}-\\d{3})?)(?:-\\d+)?(?:\\.zip)?",
            Pattern.CASE_INSENSITIVE);

    private BackupNaming() {
    }

    public static String todayAutomaticFolderName() {
        return dayFolderName(LocalDate.now());
    }

    public static String dayFolderName(LocalDate day) {
        return "backup_" + day.format(DAY_FORMAT);
    }

    public static String timeFolderName(LocalDateTime timestamp) {
        return "backup_" + timestamp.format(TIME_FORMAT);
    }

    public static boolean matches(String name) {
        return name != null && BACKUP_NAME_PATTERN.matcher(name).matches();
    }

    public static LocalDateTime getBackupCreationDate(File file) {
        if (file != null) {
            Matcher matcher = BACKUP_NAME_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                String raw = matcher.group(1);
                try {
                    if (raw.length() > 10) {
                        return LocalDateTime.parse(raw, TIME_FORMAT);
                    }
                    return LocalDate.parse(raw, DAY_FORMAT).atStartOfDay();
                } catch (Exception ignored) {
                }
            }
        }

        long lastModified = file != null ? file.lastModified() : System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        return LocalDateTime.ofEpochSecond(lastModified / 1000, 0, zone.getRules().getOffset(LocalDateTime.now()));
    }
}

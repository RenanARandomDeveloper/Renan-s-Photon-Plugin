package net.renan.photonplugin.log;

import java.io.File;
import java.time.LocalDateTime;

final class LogSessionFactory {

    private LogSessionFactory() {
    }

    static LogSession create(File workspaceFolder) {
        LogSession session = new LogSession();

        File photonFolder = new File(workspaceFolder, ".photon");
        ensureDirectoryExists(photonFolder);

        File logsDir = new File(photonFolder, "logs");
        ensureDirectoryExists(logsDir);

        session.setLogDirectory(photonFolder);
        session.setLogsFolder(logsDir);

        String fileName = "photon_" + LocalDateTime.now().format(LogArchiver.FILE_NAME_FORMAT) + ".log";
        session.setLogFile(new File(logsDir, fileName));

        LogArchiver.scheduleMaintenance(session);

        return session;
    }

    private static void ensureDirectoryExists(File directory) {
        if (directory.exists()) {
            return;
        }

        if (!directory.mkdirs() && !directory.exists()) {
            LogInternal.LOGGER.error("[Photon] Failed to create folder at {}", directory.getAbsolutePath());
        }
    }
}
package net.renan.photonplugin.log;

final class LogConfig {

    static final int MAX_MEMORY_LINES = 10000;
    static final int MAX_LOG_FILES = 10;
    static final int FLUSH_BATCH_SIZE = 20;
    static final long MAX_PERSISTED_READ_BYTES = 5L * 1024 * 1024;
    static final long MIN_FILE_AGE_BEFORE_MAINTENANCE_MS = 5_000L;
    static final long FLUSH_WAIT_TIMEOUT_MS = 500L;

    private LogConfig() {
    }
}
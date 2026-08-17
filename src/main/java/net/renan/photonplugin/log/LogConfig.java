package net.renan.photonplugin.log;

final class LogConfig {

    static final int MAX_MEMORY_LINES = 10000;

    static final int MAX_LOG_FILES = 10;

    static final int FLUSH_BATCH_SIZE = 20;

    static final long MAX_PERSISTED_READ_BYTES = 5L * 1024 * 1024;

    private LogConfig() {
    }
}

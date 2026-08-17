package net.renan.photonplugin.log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

final class LogSession {

    private static final class QueuedLine {
        final String line;
        final boolean forceFlush;

        QueuedLine(String line, boolean forceFlush) {
            this.line = line;
            this.forceFlush = forceFlush;
        }
    }

    private static final QueuedLine SHUTDOWN_SENTINEL = new QueuedLine(null, false);

    private final List<String> buffer = Collections.synchronizedList(new LinkedList<>());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final BlockingQueue<QueuedLine> writeQueue = new LinkedBlockingQueue<>();
    private final Object writerLock = new Object();

    private volatile File logDirectory;
    private volatile File logsFolder;
    private volatile File logFile;
    private volatile boolean released;

    private long totalLinesRecorded = 0;
    private Thread writerThread;
    private boolean writerRunning;

    File getLogDirectory() {
        return logDirectory;
    }

    void setLogDirectory(File logDirectory) {
        this.logDirectory = logDirectory;
    }

    File getLogsFolder() {
        return logsFolder;
    }

    void setLogsFolder(File logsFolder) {
        this.logsFolder = logsFolder;
    }

    File getLogFile() {
        return logFile;
    }

    void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    boolean isReleased() {
        return released;
    }

    void markReleased() {
        released = true;
    }

    void record(String line, boolean forceFlush) {
        synchronized (buffer) {
            buffer.add(line);
            totalLinesRecorded++;
            while (buffer.size() > LogConfig.MAX_MEMORY_LINES) {
                buffer.remove(0);
            }
            enqueueForWrite(line, forceFlush);
        }
        notifyListeners();
    }

    List<String> snapshotBuffer() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    long getTotalLinesRecorded() {
        synchronized (buffer) {
            return totalLinesRecorded;
        }
    }

    List<String> getBufferedSince(long sinceTotalCount) {
        synchronized (buffer) {
            long oldestAvailableTotal = totalLinesRecorded - buffer.size();
            if (sinceTotalCount < oldestAvailableTotal) {
                return null;
            }
            int skip = (int) (sinceTotalCount - oldestAvailableTotal);
            if (skip >= buffer.size()) {
                return List.of();
            }
            return new ArrayList<>(buffer.subList(skip, buffer.size()));
        }
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        if (listeners.isEmpty()) {
            return;
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LogInternal.LOGGER.error("[Photon] Log listener threw an exception", e);
            }
        }
    }

    private void enqueueForWrite(String line, boolean forceFlush) {
        synchronized (writerLock) {
            if (!writerRunning) {
                writerRunning = true;
                writerThread = new Thread(this::runWriterLoop, "Photon-Log-Writer");
                writerThread.setDaemon(true);
                writerThread.start();
            }
            writeQueue.add(new QueuedLine(line, forceFlush));
        }
    }

    private void runWriterLoop() {
        Writer writer = null;
        File writerFile = null;
        int linesSinceFlush = 0;

        try {
            while (true) {
                QueuedLine item;
                try {
                    item = writeQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (item == SHUTDOWN_SENTINEL) {
                    break;
                }

                File target = logFile;
                if (target == null) {
                    continue;
                }

                try {
                    if (writer == null || !Objects.equals(target, writerFile)) {
                        closeQuietly(writer);
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        writer = new BufferedWriter(
                                new OutputStreamWriter(new FileOutputStream(target, true), StandardCharsets.UTF_8));
                        writerFile = target;
                        linesSinceFlush = 0;
                    }

                    writer.write(item.line);
                    writer.write(System.lineSeparator());
                    linesSinceFlush++;

                    if (item.forceFlush || linesSinceFlush >= LogConfig.FLUSH_BATCH_SIZE) {
                        writer.flush();
                        linesSinceFlush = 0;
                    }
                } catch (IOException e) {
                    LogInternal.LOGGER.error("[Photon] Failed to write to log file", e);
                    closeQuietly(writer);
                    writer = null;
                    writerFile = null;
                }
            }
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(Writer writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            LogInternal.LOGGER.error("[Photon] Failed to close log file writer", e);
        }
    }

    void closeWriter() {
        synchronized (writerLock) {
            if (!writerRunning) {
                return;
            }
            writerRunning = false;
            writeQueue.add(SHUTDOWN_SENTINEL);
            Thread thread = writerThread;
            writerThread = null;
            if (thread != null) {
                try {
                    thread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

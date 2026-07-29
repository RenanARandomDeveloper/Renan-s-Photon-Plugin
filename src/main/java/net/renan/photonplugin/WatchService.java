package net.renan.photonplugin;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class WatchService {

    public enum ChangeKind {
        CREATE, MODIFY, DELETE, OVERFLOW
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onChange(Path watchedRoot, Path changedPath, ChangeKind kind);
    }

    private final Map<WatchKey, Path> watchKeyToDir = new ConcurrentHashMap<>();

    private volatile java.nio.file.WatchService nioWatchService;
    private volatile Thread watchThread;
    private volatile boolean running = false;

    public synchronized void start(List<Path> rootsToWatch, Predicate<Path> exclusionFilter, ChangeListener listener) {
        stop();

        List<Path> safeRoots = rootsToWatch != null ? rootsToWatch : List.of();
        Predicate<Path> exclusion = exclusionFilter != null ? exclusionFilter : p -> false;

        try {
            nioWatchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            Log.error("Failed to initialize WatchService.", e);
            throw new RuntimeException("[Photon] [WatchService] Failed to initialize WatchService", e);
        }

        running = true;
        watchThread = new Thread(() -> runLoop(safeRoots, exclusion, listener), "Photon-WatchService");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void runLoop(List<Path> rootsToWatch, Predicate<Path> exclusion, ChangeListener listener) {
        try {
            for (Path root : rootsToWatch) {
                try {
                    registerRootWithFallback(root, exclusion);
                } catch (ClosedWatchServiceException e) {
                    return;
                } catch (IOException e) {
                    Log.warn("Failed to register watch root '%s': %s", root, e.getMessage());
                }
            }

            WatchKey key;
            while (running && (key = nioWatchService.take()) != null) {
                Path dir = watchKeyToDir.get(key);

                if (dir != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            Log.warn("File system event queue overflowed for '%s'. Some raw events were dropped by the OS; reconciling the watched subtree so nothing gets silently missed.", dir);
                            if (listener != null) listener.onChange(dir, dir, ChangeKind.OVERFLOW);
                            try {
                                reconcileAfterOverflow(dir, exclusion, listener);
                            } catch (ClosedWatchServiceException e) {
                                return;
                            } catch (IOException e) {
                                Log.warn("Failed to reconcile '%s' after overflow: %s", dir, e.getMessage());
                            }
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        Path changedName = ((WatchEvent<Path>) event).context();
                        Path changed = dir.resolve(changedName);

                        if (exclusion.test(changed)) continue;

                        ChangeKind changeKind;
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            changeKind = ChangeKind.CREATE;
                            if (Files.isDirectory(changed)) {
                                try {
                                    registerAll(changed, exclusion);
                                } catch (ClosedWatchServiceException e) {
                                    return;
                                } catch (IOException e) {
                                    Log.warn("Failed to register new sub-folder '%s': %s", changed, e.getMessage());
                                }
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            changeKind = ChangeKind.DELETE;
                        } else {
                            changeKind = ChangeKind.MODIFY;
                        }

                        if (listener != null) listener.onChange(dir, changed, changeKind);
                    }
                }

                if (!key.reset()) {
                    watchKeyToDir.remove(key);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignored) {
        } catch (RuntimeException e) {
            Log.error("Unexpected error in Photon WatchService loop.", e);
        } finally {
            running = false;
        }
    }

    private void registerRootWithFallback(Path root, Predicate<Path> exclusion) throws IOException {
        Path current = root;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }

        if (current == null) {
            Log.warn("Cannot watch '%s': no existing ancestor directory found on this filesystem.", root);
            return;
        }

        if (!current.equals(root)) {
            Log.warn("Watch root '%s' does not exist yet. Watching nearest existing ancestor '%s' until it is created.", root, current);
        }

        registerAll(current, exclusion);
    }

    private void reconcileAfterOverflow(Path start, Predicate<Path> exclusion, ChangeListener listener) throws IOException {
        if (!Files.exists(start) || exclusion.test(start)) {
            return;
        }

        registerAll(start, exclusion);

        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (exclusion.test(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!exclusion.test(file) && listener != null) {
                    listener.onChange(file.getParent(), file, ChangeKind.CREATE);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to access '%s' during overflow reconciliation: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerAll(Path start, Predicate<Path> exclusion) throws IOException {
        registerAll(start, exclusion, new HashSet<>());
    }

    private void registerAll(Path start, Predicate<Path> exclusion, Set<Path> visitedRealPaths) throws IOException {
        if (!Files.exists(start) || !Files.isDirectory(start)) return;
        if (exclusion.test(start)) return;

        Path realPath;
        try {
            realPath = start.toRealPath();
        } catch (IOException e) {
            Log.warn("Could not resolve real path for '%s', skipping: %s", start, e.getMessage());
            return;
        }

        if (!visitedRealPaths.add(realPath)) {
            return;
        }

        WatchKey key = start.register(nioWatchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchKeyToDir.put(key, start);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
            for (Path child : stream) {
                if (Files.isDirectory(child) && !exclusion.test(child)) {
                    registerAll(child, exclusion, visitedRealPaths);
                }
            }
        }
    }

    public synchronized void stop() {
        running = false;

        if (nioWatchService != null) {
            try {
                nioWatchService.close();
            } catch (IOException e) {
                Log.warn("Error while closing WatchService: %s", e.getMessage());
            }
            nioWatchService = null;
        }

        if (watchThread != null) {
            watchThread.interrupt();

            if (watchThread != Thread.currentThread()) {
                try {
                    watchThread.join(5000);
                    if (watchThread.isAlive()) {
                        Log.warn("Photon-WatchService thread did not terminate within the expected time.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            watchThread = null;
        }

        watchKeyToDir.clear();
    }

    public boolean isRunning() {
        return running;
    }
}
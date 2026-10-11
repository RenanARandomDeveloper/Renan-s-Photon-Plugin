package net.renan.photonplugin;

import net.renan.photonplugin.log.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;


public final class WatchService {

    private static final long STOP_JOIN_TIMEOUT_MS = 5000;

    private static final WatchEvent.Kind<?>[] EVENT_KINDS = {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
    };

    private volatile Session session;

    private static List<Path> sanitizeRoots(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Set<Path> unique = new LinkedHashSet<>();
        for (Path root : new ArrayList<>(roots)) {
            if (root != null) {
                unique.add(root.isAbsolute() ? root : root.toAbsolutePath());
            }
        }
        return List.copyOf(unique);
    }

    private static void logFailure(String message, Throwable t) {
        if (t instanceof Exception) {
            Log.error(message, t);
        } else {
            Log.error(message, new RuntimeException(t));
        }
    }

    public synchronized void start(List<Path> rootsToWatch, Predicate<Path> exclusionFilter, ChangeListener listener) {
        stop();

        List<Path> safeRoots = sanitizeRoots(rootsToWatch);
        Predicate<Path> exclusion = exclusionFilter != null ? exclusionFilter : p -> false;

        java.nio.file.WatchService nio;
        try {
            nio = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            Log.error("Failed to initialize WatchService.", e);
            throw new RuntimeException("[Photon] [WatchService] Failed to initialize WatchService", e);
        }

        Session fresh = new Session(nio, safeRoots, exclusion, listener);
        session = fresh;
        try {
            fresh.begin();
        } catch (RuntimeException | Error e) {
            session = null;
            fresh.shutdown();
            throw e;
        }
    }

    public synchronized void stop() {
        Session current = session;
        session = null;
        if (current != null) {
            current.shutdown();
        }
    }

    public boolean isRunning() {
        Session current = session;
        return current != null && current.running;
    }

    public enum ChangeKind {
        CREATE, MODIFY, DELETE, OVERFLOW
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onChange(Path watchedRoot, Path changedPath, ChangeKind kind);
    }

    private static final class Session implements Runnable {

        private final java.nio.file.WatchService service;
        private final List<Path> roots;
        private final Predicate<Path> exclusion;
        private final ChangeListener listener;
        private final Map<WatchKey, Path> keyToDir = new ConcurrentHashMap<>();
        private final Set<Path> activeRoots = new HashSet<>();
        private final Set<Path> pendingRoots = new LinkedHashSet<>();
        private boolean exclusionFailureLogged = false;

        private volatile boolean running = true;
        private volatile Thread thread;

        Session(java.nio.file.WatchService service, List<Path> roots, Predicate<Path> exclusion, ChangeListener listener) {
            this.service = service;
            this.roots = roots;
            this.exclusion = exclusion;
            this.listener = listener;
        }

        private static Path nearestExisting(Path path) {
            Path current = path;
            while (current != null && !Files.exists(current)) {
                current = current.getParent();
            }
            return current;
        }

        void begin() {
            Thread t = new Thread(this, "Photon-WatchService");
            t.setDaemon(true);
            thread = t;
            t.start();
        }

        void shutdown() {
            running = false;
            closeService();

            Thread t = thread;
            if (t != null && t != Thread.currentThread()) {
                t.interrupt();
                try {
                    t.join(STOP_JOIN_TIMEOUT_MS);
                    if (t.isAlive()) {
                        Log.warn("Photon-WatchService thread did not terminate within the expected time.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            thread = null;
            keyToDir.clear();
        }

        private void closeService() {
            try {
                service.close();
            } catch (IOException e) {
                Log.warn("Error while closing WatchService: %s", e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                for (Path root : roots) {
                    if (!running) {
                        return;
                    }
                    try {
                        armRoot(root, true);
                    } catch (ClosedWatchServiceException e) {
                        return;
                    } catch (IOException | RuntimeException e) {
                        Log.warn("Failed to register watch root '%s': %s", root, e.getMessage());
                    }
                }
                eventLoop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException ignored) {
            } catch (RuntimeException | Error e) {
                logFailure("Unexpected error in Photon WatchService loop; the watcher has stopped.", e);
            } finally {
                running = false;
                closeService();
                keyToDir.clear();
            }
        }

        private void eventLoop() throws InterruptedException {
            while (running) {
                WatchKey key = service.take();
                Path dir = keyToDir.get(key);

                try {
                    List<WatchEvent<?>> events = key.pollEvents();
                    if (dir != null) {
                        for (WatchEvent<?> event : events) {
                            if (!running) {
                                return;
                            }
                            handleEvent(dir, event);
                        }
                    }
                } catch (ClosedWatchServiceException e) {
                    return;
                } catch (RuntimeException e) {
                    Log.error("Error while handling events for '" + dir + "'; the watcher keeps running.", e);
                }

                if (!running) {
                    return;
                }

                if (!key.reset()) {
                    Path gone = keyToDir.remove(key);
                    try {
                        if (gone != null) {
                            handleInvalidKey(gone);
                        }
                    } catch (ClosedWatchServiceException e) {
                        return;
                    } catch (RuntimeException e) {
                        Log.error("Error while recovering after '" + gone + "' became unavailable; the watcher keeps running.", e);
                    }
                }
            }
        }

        private void handleEvent(Path dir, WatchEvent<?> event) {
            WatchEvent.Kind<?> kind = event.kind();
            boolean covered = isUnderActiveRoot(dir);

            if (kind == StandardWatchEventKinds.OVERFLOW) {
                if (!covered) {
                    Log.warn("File system event queue overflowed while waiting for missing watch root(s) under '%s'; re-checking them.", dir);
                    rearmPendingRoots(dir);
                    return;
                }
                Log.warn("File system event queue overflowed for '%s'. Some raw events were dropped by the OS; reconciling the watched subtree so nothing gets silently missed.", dir);
                notifyListener(dir, dir, ChangeKind.OVERFLOW);
                try {
                    reconcileAfterOverflow(dir);
                } catch (ClosedWatchServiceException e) {
                    throw e;
                } catch (IOException e) {
                    Log.warn("Failed to reconcile '%s' after overflow: %s", dir, e.getMessage());
                }
                return;
            }

            Object context = event.context();
            if (!(context instanceof Path)) {
                return;
            }
            Path changed = dir.resolve((Path) context);

            if (!covered) {
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    activatePendingRoots(changed);
                }
                return;
            }

            if (isExcluded(changed)) {
                return;
            }

            ChangeKind changeKind;
            boolean newDirectory = false;
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                changeKind = ChangeKind.CREATE;
                newDirectory = Files.isDirectory(changed);
                if (newDirectory) {
                    if (pendingRoots.remove(changed)) {
                        activeRoots.add(changed);
                    }
                    try {
                        registerAll(changed);
                    } catch (ClosedWatchServiceException e) {
                        throw e;
                    } catch (IOException e) {
                        Log.warn("Failed to register new sub-folder '%s': %s", changed, e.getMessage());
                    }
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                changeKind = ChangeKind.DELETE;
            } else {
                changeKind = ChangeKind.MODIFY;
            }

            notifyListener(dir, changed, changeKind);

            if (newDirectory) {
                catchUp(changed, true);
            }
        }

        private void handleInvalidKey(Path dir) {
            if (activeRoots.remove(dir)) {
                pendingRoots.add(dir);
            }
            rearmPendingRoots(dir);
        }

        private boolean armRoot(Path root, boolean announce) throws IOException {
            while (true) {
                Path current = nearestExisting(root);

                if (current == null) {
                    if (announce) {
                        Log.warn("Cannot watch '%s': no existing ancestor directory found on this filesystem.", root);
                    }
                    return false;
                }

                if (current.equals(root)) {
                    pendingRoots.remove(root);
                    activeRoots.add(root);
                    registerAll(root);
                    return true;
                }

                pendingRoots.add(root);
                if (announce) {
                    Log.warn("Watch root '%s' does not exist yet. Watching nearest existing ancestor '%s' (non-recursively) "
                            + "until it is created.", root, current);
                    announce = false;
                }
                registerSingleDirectory(current);

                Path after = nearestExisting(root);
                if (after == null || after.equals(current)) {
                    return false;
                }
            }
        }

        private void activatePendingRoots(Path changed) {
            for (Path pending : new ArrayList<>(pendingRoots)) {
                if (pending.startsWith(changed)) {
                    rearm(pending);
                }
            }
        }

        private void rearmPendingRoots(Path under) {
            for (Path pending : new ArrayList<>(pendingRoots)) {
                if (pending.startsWith(under)) {
                    rearm(pending);
                }
            }
        }

        private void rearm(Path root) {
            try {
                if (armRoot(root, false)) {
                    if (!isExcluded(root)) {
                        Path parent = root.getParent() != null ? root.getParent() : root;
                        notifyListener(parent, root, ChangeKind.CREATE);
                        catchUp(root, true);
                    }
                }
            } catch (ClosedWatchServiceException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                Log.warn("Failed to (re)register watch root '%s': %s", root, e.getMessage());
            }
        }

        private boolean isUnderActiveRoot(Path dir) {
            for (Path root : activeRoots) {
                if (dir.startsWith(root)) {
                    return true;
                }
            }
            return false;
        }

        private void register(Path dir) throws IOException {
            WatchKey key = dir.register(service, EVENT_KINDS);
            keyToDir.put(key, dir);
        }

        private void registerSingleDirectory(Path dir) throws IOException {
            if (!Files.isDirectory(dir) || isExcluded(dir)) {
                return;
            }
            register(dir);
        }

        private void registerAll(Path start) throws IOException {
            registerAll(start, new HashSet<>());
        }

        private void registerAll(Path start, Set<Path> visitedRealPaths) throws IOException {
            if (!Files.isDirectory(start)) return;
            if (isExcluded(start)) return;

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

            register(start);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
                Iterator<Path> children = stream.iterator();
                while (running) {
                    Path child;
                    try {
                        if (!children.hasNext()) break;
                        child = children.next();
                    } catch (DirectoryIteratorException e) {
                        Log.warn("Failed to list '%s' while registering: %s", start, e.getCause().getMessage());
                        break;
                    }
                    if (Files.isDirectory(child) && !isExcluded(child)) {
                        try {
                            registerAll(child, visitedRealPaths);
                        } catch (ClosedWatchServiceException e) {
                            throw e;
                        } catch (IOException e) {
                            Log.warn("Failed to register sub-folder '%s': %s", child, e.getMessage());
                        }
                    }
                }
            }
        }

        private void reconcileAfterOverflow(Path start) throws IOException {
            if (!Files.exists(start) || isExcluded(start)) {
                return;
            }
            registerAll(start);
            scanSubtree(start, false);
        }

        private void catchUp(Path start, boolean includeDirectories) {
            try {
                scanSubtree(start, includeDirectories);
            } catch (IOException e) {
                Log.warn("Failed to scan '%s': %s", start, e.getMessage());
            }
        }

        private void scanSubtree(Path start, boolean includeDirectories) throws IOException {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!running) return FileVisitResult.TERMINATE;
                    if (isExcluded(dir)) return FileVisitResult.SKIP_SUBTREE;
                    if (includeDirectories && !dir.equals(start)) {
                        notifyListener(dir.getParent(), dir, ChangeKind.CREATE);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!running) return FileVisitResult.TERMINATE;
                    if (!file.equals(start) && !isExcluded(file)) {
                        notifyListener(file.getParent(), file, ChangeKind.CREATE);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    Log.warn("Failed to access '%s' during scan: %s", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        Log.warn("Failed to finish scanning '%s': %s", dir, exc.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private boolean isExcluded(Path path) {
            try {
                return exclusion.test(path);
            } catch (RuntimeException e) {
                if (!exclusionFailureLogged) {
                    exclusionFailureLogged = true;
                    Log.error("The exclusion filter threw an exception for '" + path
                            + "'; treating the path as not excluded (further failures are not logged).", e);
                }
                return false;
            }
        }

        private void notifyListener(Path watchedRoot, Path changedPath, ChangeKind kind) {
            if (listener == null) return;
            try {
                listener.onChange(watchedRoot, changedPath, kind);
            } catch (RuntimeException | LinkageError | AssertionError e) {
                logFailure("A WatchService listener threw an exception while handling " + kind + " for '" + changedPath
                        + "'; the watcher keeps running.", e);
            }
        }
    }
}
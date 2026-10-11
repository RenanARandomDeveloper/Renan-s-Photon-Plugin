package net.renan.photonplugin.backup.core;

import net.renan.photonplugin.log.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileTreeUtils {
    private FileTreeUtils() {
    }

    public static void copyDirectory(Path source, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path fileName = destination.getFileName();
        Path tempDestination = (parent != null ? parent : destination.toAbsolutePath().getParent())
                .resolve("." + fileName + ".tmp-" + System.nanoTime());

        if (Files.exists(tempDestination)) {
            deleteRecursively(tempDestination);
        }

        boolean copyCompleted = false;
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(tempDestination.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, tempDestination.resolve(source.relativize(file)),
                            StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    Log.warn("Failed to copy '%s' during backup: %s", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
            copyCompleted = true;

            if (Files.exists(destination)) {
                deleteRecursively(destination);
            }
            try {
                Files.move(tempDestination, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempDestination, destination);
            }
        } catch (IOException e) {
            if (!copyCompleted) {
                deleteRecursively(tempDestination);
            } else {
                Log.warn("Backup swap failed after copying to '%s'; destination '%s' may be missing or "
                                + "incomplete. The completed copy was left at '%s' for manual recovery: %s",
                        destination, destination, tempDestination, e.getMessage());
            }
            throw e;
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    Log.warn("Failed to delete '%s': %s", file, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                Log.warn("Failed to access '%s' for deletion: %s", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    Log.warn("Failed to delete directory '%s': %s", dir, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
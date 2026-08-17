package net.renan.photonplugin.backup.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtils {
    private ZipUtils() {
    }

    public static void compressDirectoryToZip(Path sourceDir, File destinationZip) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destinationZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            appendDirectoryToZip(zos, sourceDir, "");
        } catch (IOException e) {
            destinationZip.delete();
            throw e;
        }
    }

    public static void appendDirectoryToZip(ZipOutputStream zos, Path baseDir, String entryPrefix)
            throws IOException {
        String prefix = normalizePrefix(entryPrefix);
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = baseDir.relativize(file).toString().replace(File.separatorChar, '/');
                zos.putNextEntry(new ZipEntry(prefix + relative));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void appendZipEntries(ZipOutputStream zos, File sourceZipFile, String entryPrefix)
            throws IOException {
        String prefix = normalizePrefix(entryPrefix);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceZipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(prefix + entry.getName()));
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeEntry();
            }
        }
    }

    public static void extractZip(File zipFile, Path targetDir) throws IOException {
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTargetDir);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = resolveSafeEntryPath(normalizedTargetDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                    zis.closeEntry();
                    continue;
                }

                Path parent = outPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }

    private static Path resolveSafeEntryPath(Path normalizedTargetDir, String entryName) throws IOException {
        Path outPath = normalizedTargetDir.resolve(entryName).normalize();
        if (!outPath.startsWith(normalizedTargetDir)) {
            throw new IOException(
                    "Refusing to extract zip entry outside of the target directory (zip-slip attempt): "
                            + entryName);
        }
        return outPath;
    }

    private static String normalizePrefix(String entryPrefix) {
        if (entryPrefix == null || entryPrefix.isEmpty()) {
            return "";
        }
        return entryPrefix.endsWith("/") ? entryPrefix : entryPrefix + "/";
    }
}

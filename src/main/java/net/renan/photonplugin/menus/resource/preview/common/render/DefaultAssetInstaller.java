package net.renan.photonplugin.menus.resource.preview.common.render;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DefaultAssetInstaller {
    public static final String INSTALLED_FOLDER_NAME = ".photon";

    private static final String MARKER_FILE_NAME = ".bundle-checksum";

    private DefaultAssetInstaller() {
    }

    public static File ensureInstalled(File workspaceFolder, PreviewDiagnostics diagnostics) {
        if (workspaceFolder == null) {
            return null;
        }
        byte[] bundleBytes = AssetResolver.readBundleBytes(diagnostics);
        if (bundleBytes == null) {
            return null;
        }
        File target = new File(workspaceFolder, INSTALLED_FOLDER_NAME);
        File marker = new File(target, MARKER_FILE_NAME);
        String expected = Long.toHexString(checksum(bundleBytes));
        if (expected.equals(readMarker(marker))) {
            return target;
        }
        if (!extract(bundleBytes, target, diagnostics)) {
            return null;
        }
        writeMarker(marker, expected, diagnostics);
        return target;
    }

    private static boolean extract(byte[] bundleBytes, File targetDir, PreviewDiagnostics diagnostics) {
        Path targetRoot = targetDir.toPath().normalize();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundleBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File outFile = new File(targetDir, entry.getName());
                Path outPath = outFile.toPath().normalize();
                if (!outPath.startsWith(targetRoot)) {
                    diagnostics.warn("FX preview: skipped a default-asset entry outside the install folder: \""
                            + entry.getName() + "\"");
                    continue;
                }
                File parent = outFile.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    diagnostics.warn("FX preview: couldn't create " + parent + " while installing default assets");
                    return false;
                }
                try (OutputStream out = new FileOutputStream(outFile)) {
                    zip.transferTo(out);
                }
            }
            return true;
        } catch (IOException e) {
            diagnostics.warn("FX preview: failed to install default assets into " + targetDir
                    + " (" + e.getMessage() + ")");
            return false;
        }
    }

    private static long checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static String readMarker(File marker) {
        try {
            return Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeMarker(File marker, String value, PreviewDiagnostics diagnostics) {
        try {
            Files.writeString(marker.toPath(), value, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.warn("FX preview: installed default assets but failed to write the version marker ("
                    + e.getMessage() + ")");
        }
    }
}

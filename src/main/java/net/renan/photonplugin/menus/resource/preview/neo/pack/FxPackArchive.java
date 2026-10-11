package net.renan.photonplugin.menus.resource.preview.neo.pack;

import net.mcreator.ui.init.L10N;

import net.renan.photonplugin.menus.resource.preview.common.render.AssetSource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FxPackArchive implements AssetSource {
    public static final String EXTENSION = ".fxpack";
    public static final String ASSETS_PREFIX = "assets/";

    private static final Pattern FX_ENTRY = Pattern.compile("^assets/([^/]+)/fx/(.+)\\.fx$");
    private static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    private final Map<String, byte[]> entries;
    private final List<Effect> effects;

    private FxPackArchive(Map<String, byte[]> entries) {
        this.entries = entries;
        this.effects = discoverEffects(entries.keySet());
    }

    public static boolean isFxPack(File file) {
        return file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    public static FxPackArchive read(File file) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (FileSystem zip = FileSystems.newFileSystem(file.toPath())) {
            Path assets = zip.getPath("assets");
            if (Files.isDirectory(assets)) {
                long total = 0;
                try (Stream<Path> walk = Files.walk(assets)) {
                    for (Path path : walk.filter(Files::isRegularFile).toList()) {
                        total += Files.size(path);
                        if (total > MAX_TOTAL_BYTES) {
                            throw new IOException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.pack_too_large", file.getName()));
                        }
                        entries.put(entryKey(path), Files.readAllBytes(path));
                    }
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (ProviderNotFoundException | UnsupportedOperationException e) {
            throw new IOException(L10N.t("plugin.photon.resourcemenu.fx.preview.error.pack_invalid", file.getName(), EXTENSION), e);
        }
        return new FxPackArchive(entries);
    }

    private static List<Effect> discoverEffects(Collection<String> entryPaths) {
        return entryPaths.stream()
                .map(FX_ENTRY::matcher)
                .filter(Matcher::matches)
                .map(m -> new Effect(m.group(1), m.group(2), m.group(0)))
                .sorted(Comparator.comparing(Effect::id))
                .toList();
    }

    private static String entryKey(Path path) {
        String key = path.toString().replace('\\', '/');
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return key;
    }

    public List<Effect> effects() {
        return effects;
    }

    @Override
    public byte[] entry(String path) {
        return entries.get(path);
    }

    public byte[] effectBytes(Effect effect) {
        return entries.get(effect.entryPath());
    }

    public record Effect(String namespace, String name, String entryPath) {
        public String id() {
            return namespace + ":" + name;
        }

        @Override
        public String toString() {
            return id();
        }
    }
}

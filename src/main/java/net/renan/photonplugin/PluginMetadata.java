package net.renan.photonplugin;

import net.renan.photonplugin.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginMetadata {
    private static final String PLUGIN_JSON_PATH = "/plugin.json";
    private static final String PHOTON_PLUGIN_YAML_PATH = "/apis/photon_plugin.yaml";
    private static final String UNKNOWN = "unknown";
    private static final Pattern PLUGIN_VERSION_PATTERN = Pattern.compile("\\\"info\\\"\\s*:\\s*\\{.*?\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);
    private static final Pattern TOP_LEVEL_YAML_KEY_PATTERN = Pattern.compile("(?m)^([A-Za-z0-9_.-]+):\\s*$");
    private static final Pattern PHOTON_DEPENDENCY_PATTERN = dependencyPattern("com\\.lowdragmc\\.photon");
    private static final Pattern LDLIB_DEPENDENCY_PATTERN = dependencyPattern("com\\.lowdragmc\\.ldlib2?");
    private static final Map<String, ApiVersions> API_VERSIONS_CACHE = new ConcurrentHashMap<>();
    private static volatile String pluginVersion;
    private static volatile Optional<String> pluginYaml;

    private PluginMetadata() {
        throw new AssertionError("Utility class must not be instantiated");
    }

    public static String getPluginVersion() {
        String cachedVersion = pluginVersion;
        if (cachedVersion == null) {
            synchronized (PluginMetadata.class) {
                cachedVersion = pluginVersion;
                if (cachedVersion == null) {
                    cachedVersion = readPluginVersion();
                    pluginVersion = cachedVersion;
                }
            }
        }
        return cachedVersion;
    }

    public static ApiVersions getApiVersions(String yamlFlavorKey) {
        String flavorKey = requireFlavorKey(yamlFlavorKey);
        return API_VERSIONS_CACHE.computeIfAbsent(flavorKey, PluginMetadata::readApiVersions);
    }

    private static String readPluginVersion() {
        return readResource(PLUGIN_JSON_PATH)
                .flatMap(PluginMetadata::extractPluginVersion)
                .orElseGet(() -> {
                    Log.warn("Could not read 'info.version' from %s; falling back to '%s' plugin version",
                            PLUGIN_JSON_PATH, UNKNOWN);
                    return UNKNOWN;
                });
    }

    private static ApiVersions readApiVersions(String yamlFlavorKey) {
        String yaml = getPluginYaml().orElse(null);
        if (yaml == null) {
            Log.warn("Could not locate %s on the classpath; falling back to '%s' API versions",
                    PHOTON_PLUGIN_YAML_PATH, UNKNOWN);
            return ApiVersions.UNKNOWN_VERSIONS;
        }

        String block = extractTopLevelYamlBlock(yaml, yamlFlavorKey).orElse(null);
        if (block == null) {
            Log.warn("Could not find a '%s:' block inside %s", yamlFlavorKey, PHOTON_PLUGIN_YAML_PATH);
            return ApiVersions.UNKNOWN_VERSIONS;
        }

        String photonVersion = extractDependencyVersion(block, PHOTON_DEPENDENCY_PATTERN);
        String ldLibVersion = extractDependencyVersion(block, LDLIB_DEPENDENCY_PATTERN);
        logMissingDependency(yamlFlavorKey, "com.lowdragmc.photon", photonVersion);
        logMissingDependency(yamlFlavorKey, "com.lowdragmc.ldlib(2)", ldLibVersion);
        return new ApiVersions(photonVersion, ldLibVersion);
    }

    private static Optional<String> getPluginYaml() {
        Optional<String> cachedYaml = pluginYaml;
        if (cachedYaml == null) {
            synchronized (PluginMetadata.class) {
                cachedYaml = pluginYaml;
                if (cachedYaml == null) {
                    cachedYaml = readResource(PHOTON_PLUGIN_YAML_PATH);
                    pluginYaml = cachedYaml;
                }
            }
        }
        return cachedYaml;
    }

    private static Optional<String> extractPluginVersion(String json) {
        Matcher matcher = PLUGIN_VERSION_PATTERN.matcher(json);
        return matcher.find() ? Optional.of(matcher.group(1).strip()) : Optional.empty();
    }

    private static Optional<String> extractTopLevelYamlBlock(String yaml, String topLevelKey) {
        Matcher matcher = TOP_LEVEL_YAML_KEY_PATTERN.matcher(yaml);
        while (matcher.find()) {
            if (matcher.group(1).equals(topLevelKey)) {
                int start = matcher.end();
                return Optional.of(yaml.substring(start, matcher.find() ? matcher.start() : yaml.length()));
            }
        }
        return Optional.empty();
    }

    private static String extractDependencyVersion(String block, Pattern dependencyPattern) {
        Matcher matcher = dependencyPattern.matcher(block);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Pattern dependencyPattern(String groupRegex) {
        return Pattern.compile(groupRegex + ":[^:'\\\"]+:([^:'\\\"]+)");
    }

    private static Optional<String> readResource(String path) {
        try (InputStream input = PluginMetadata.class.getResourceAsStream(path)) {
            if (input == null) {
                return Optional.empty();
            }
            return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            Log.warn("I/O error reading resource %s: %s", path, exception.getMessage());
            return Optional.empty();
        }
    }

    private static String requireFlavorKey(String yamlFlavorKey) {
        Objects.requireNonNull(yamlFlavorKey, "yamlFlavorKey cannot be null");
        String flavorKey = yamlFlavorKey.strip();
        if (flavorKey.isEmpty()) {
            throw new IllegalArgumentException("yamlFlavorKey cannot be empty");
        }
        return flavorKey;
    }

    private static String normalizeVersion(String version) {
        return version == null || version.isBlank() ? UNKNOWN : version.strip();
    }

    private static void logMissingDependency(String flavorKey, String dependency, String version) {
        if (version == null) {
            Log.warn("Could not find a '%s' dependency coordinate inside the '%s:' block", dependency, flavorKey);
        }
    }

    public record ApiVersions(String photonVersion, String ldLibVersion) {
        private static final ApiVersions UNKNOWN_VERSIONS = new ApiVersions(UNKNOWN, UNKNOWN);

        public ApiVersions {
            photonVersion = normalizeVersion(photonVersion);
            ldLibVersion = normalizeVersion(ldLibVersion);
        }

        public boolean isUnknown() {
            return this.equals(UNKNOWN_VERSIONS);
        }
    }
}
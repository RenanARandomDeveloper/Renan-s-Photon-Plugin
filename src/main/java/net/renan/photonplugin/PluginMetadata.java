package net.renan.photonplugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginMetadata {
    private static final String[] PLUGIN_JSON_CANDIDATE_PATH = {
            "/plugin.json"
    };
    private static final String[] YAML_CANDIDATE_PATH = {
            "/apis/photon_plugin.yaml"
    };

    private static final String UNKNOWN = "unknown";

    private static final Pattern PLUGIN_VERSION_PATTERN =
            Pattern.compile("\"info\"\\s*:\\s*\\{[^}]*?\"version\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);

    private static volatile String cachedPluginVersion;
    private static final Map<String, ApiVersions> API_VERSIONS_CACHE = new ConcurrentHashMap<>();

    public record ApiVersions(String photonVersion, String ldLibVersion) {
    }

    private PluginMetadata() {
    }

    public static String getPluginVersion() {
        String cached = cachedPluginVersion;
        if (cached != null) {
            return cached;
        }
        synchronized (PluginMetadata.class) {
            if (cachedPluginVersion == null) {
                cachedPluginVersion = readPluginVersion();
            }
            return cachedPluginVersion;
        }
    }

    public static ApiVersions getApiVersions(String yamlFlavorKey) {
        return API_VERSIONS_CACHE.computeIfAbsent(yamlFlavorKey, PluginMetadata::readApiVersions);
    }

    private static String readPluginVersion() {
        String json = readFirstAvailableResource(PLUGIN_JSON_CANDIDATE_PATH);
        if (json == null) {
            Log.warn("Could not locate plugin.json on the classpath (tried %s); falling back to '%s' plugin version.",
                    String.join(", ", PLUGIN_JSON_CANDIDATE_PATH), UNKNOWN);
            return UNKNOWN;
        }

        Matcher matcher = PLUGIN_VERSION_PATTERN.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Log.warn("Could not find an 'info.version' field inside plugin.json.");
        return UNKNOWN;
    }

    private static ApiVersions readApiVersions(String yamlFlavorKey) {
        String yaml = readFirstAvailableResource(YAML_CANDIDATE_PATH);
        if (yaml == null) {
            Log.warn("Could not locate photon_plugin.yaml on the classpath (tried %s); falling back to '%s' API versions.",
                    String.join(", ", YAML_CANDIDATE_PATH), UNKNOWN);
            return new ApiVersions(UNKNOWN, UNKNOWN);
        }

        String block = extractTopLevelYamlBlock(yaml, yamlFlavorKey);
        if (block == null) {
            Log.warn("Could not find a '%s:' block inside photon_plugin.yaml.", yamlFlavorKey);
            return new ApiVersions(UNKNOWN, UNKNOWN);
        }

        String photonVersion = extractDependencyVersion(block, "photon");
        String ldLibVersion = extractDependencyVersion(block, "ldlib2?");

        if (photonVersion == null) {
            Log.warn("Could not find a 'com.lowdragmc.photon' dependency coordinate inside the '%s:' block.", yamlFlavorKey);
        }
        if (ldLibVersion == null) {
            Log.warn("Could not find a 'com.lowdragmc.ldlib(2)' dependency coordinate inside the '%s:' block.", yamlFlavorKey);
        }

        return new ApiVersions(
                photonVersion != null ? photonVersion : UNKNOWN,
                ldLibVersion != null ? ldLibVersion : UNKNOWN);
    }

    private static String extractTopLevelYamlBlock(String yaml, String topLevelKey) {
        Pattern keyPattern = Pattern.compile("(?m)^" + Pattern.quote(topLevelKey) + ":\\s*$");
        Matcher matcher = keyPattern.matcher(yaml);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.end();

        Pattern nextTopLevelKeyPattern = Pattern.compile("(?m)^[A-Za-z0-9_.\\-]+:\\s*.*$");
        Matcher nextMatcher = nextTopLevelKeyPattern.matcher(yaml);
        nextMatcher.region(start, yaml.length());
        int end = nextMatcher.find() ? nextMatcher.start() : yaml.length();

        return yaml.substring(start, end);
    }

    private static String extractDependencyVersion(String block, String artifactIdRegex) {
        Pattern pattern = Pattern.compile("com\\.lowdragmc\\.(?:" + artifactIdRegex + "):[^:'\"]+:([^:'\"]+)");
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String readFirstAvailableResource(String[] candidatePaths) {
        for (String path : candidatePaths) {
            try (InputStream is = PluginMetadata.class.getResourceAsStream(path)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}
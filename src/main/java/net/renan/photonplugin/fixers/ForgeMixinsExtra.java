package net.renan.photonplugin.fixers;

import net.renan.photonplugin.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ForgeMixinsExtra {

    private static final String MIXIN_EXTRAS_LINE = "    compileOnly(annotationProcessor(\"io.github.llamalad7:mixinextras-common:0.5.0\"))";
    private static final String TO_REMOVE = "mixinextras-neoforge";

    public static void execute(String projectPath) {
        if (projectPath == null) {
            Log.error("Project path cannot be null.");
            return;
        }

        File buildGradle = new File(projectPath, "build.gradle");
        Path buildGradlePath = buildGradle.toPath();

        if (!buildGradle.exists()) {
            Log.warn("build.gradle not found in '%s'. Skipping ForgeMixinsExtra fix.", projectPath);
            return;
        }

        Log.info("Starting Mixins configuration fix for: %s", buildGradle.getAbsolutePath());

        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(buildGradlePath, StandardCharsets.UTF_8));

            int initialSize = lines.size();
            lines.removeIf(line -> line.contains(TO_REMOVE));
            int linesRemoved = initialSize - lines.size();

            if (linesRemoved > 0) {
                Log.info("Removed %d outdated line(s) containing '%s'.", linesRemoved, TO_REMOVE);
            }

            boolean dependencyExists = false;
            int dependenciesBlockIndex = -1;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                if (line.contains("mixinextras-common")) {
                    dependencyExists = true;
                }

                if (line.trim().startsWith("dependencies") && line.contains("{")) {
                    if (dependenciesBlockIndex == -1) {
                        dependenciesBlockIndex = i;
                    }
                }
            }

            boolean dependencyAdded = false;
            if (dependencyExists) {
                Log.info("'mixinextras-common' already specified in build.gradle. Injection skipped.");
            } else if (dependenciesBlockIndex != -1) {
                lines.add(dependenciesBlockIndex + 1, MIXIN_EXTRAS_LINE);
                dependencyAdded = true;
                Log.info("Successfully injected MixinExtras dependency into the dependencies block.");
            } else {
                Log.warn("Could not locate a valid 'dependencies {' block. MixinExtras dependency was NOT added.");
            }

            if (linesRemoved > 0 || dependencyAdded) {
                Files.write(buildGradlePath, lines, StandardCharsets.UTF_8);
                Log.info("build.gradle has been successfully updated and saved.");
            } else {
                Log.info("No modifications were required for build.gradle.");
            }

        } catch (IOException e) {
            Log.error(String.format("Failed to process or write build.gradle at %s", buildGradle.getAbsolutePath()), e);
        }
    }
}
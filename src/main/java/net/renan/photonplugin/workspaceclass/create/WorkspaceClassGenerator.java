package net.renan.photonplugin.workspaceclass.create;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.settings.WorkspaceSettings;
import net.renan.photonplugin.log.Log;
import net.renan.photonplugin.workspaceclass.WorkspaceConstants;
import net.renan.photonplugin.workspaceclass.WorkspaceOperationLock;
import net.renan.photonplugin.workspaceclass.WorkspacePaths;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public abstract class WorkspaceClassGenerator {

    protected final MCreator mcreator;
    protected final Configuration freemarkerConfig;

    protected WorkspaceClassGenerator(MCreator mcreator, String templateRoot) {
        this.mcreator = mcreator;
        this.freemarkerConfig = buildFreemarkerConfiguration(templateRoot);
    }

    private static String toJavaModName(String modID) {
        if (modID.isEmpty()) {
            return modID;
        }
        return Character.toTitleCase(modID.charAt(0)) + modID.substring(1);
    }

    private Configuration buildFreemarkerConfiguration(String templateRoot) {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setClassForTemplateLoading(this.getClass(), templateRoot);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }

    public GenerationResult generate() {
        GenerationResult result = new GenerationResult();

        if (this.mcreator == null) {
            Log.error("No MCreator instance provided, aborting.");
            result.addError("No MCreator workspace window provided.");
            return result;
        }

        Workspace workspace = this.mcreator.getWorkspace();
        if (workspace == null) {
            Log.error("No workspace is currently open, aborting.");
            result.addError("No workspace is currently open.");
            return result;
        }

        WorkspaceSettings settings = workspace.getWorkspaceSettings();
        if (settings == null) {
            Log.error("Workspace settings are unavailable, aborting.");
            result.addError("Workspace settings are unavailable.");
            return result;
        }

        String modID = settings.getModID();
        String basePackage = settings.getModElementsPackage();

        if (modID == null || modID.isBlank() || basePackage == null || basePackage.isBlank()) {
            Log.error(String.format(
                    "Workspace settings are incomplete (modID='%s', basePackage='%s').",
                    modID, basePackage));
            result.addError("Workspace settings are incomplete (missing mod ID or base package).");
            return result;
        }

        String javaModName = toJavaModName(modID);

        Path targetPackageDir;
        try {
            targetPackageDir = WorkspacePaths.resolveTargetPackageDir(workspace, settings);
        } catch (WorkspacePaths.InvalidWorkspacePathException e) {
            Log.error(e.getMessage());
            result.addError(e.getMessage());
            return result;
        }

        if (targetPackageDir == null) {
            Log.error("Could not resolve the workspace folder, aborting.");
            result.addError("Could not resolve the workspace folder.");
            return result;
        }

        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("package", basePackage);
        dataModel.put("modid", modID);
        dataModel.put("JavaModName", javaModName);
        String lockKey = WorkspacePaths.sessionKey(this.mcreator);
        ReentrantLock lock = lockKey != null ? WorkspaceOperationLock.forKey(lockKey) : null;
        if (lock != null) {
            lock.lock();
        }
        try {
            Log.info("Generating photon_plugin package at: %s", targetPackageDir);

            for (String baseClassName : WorkspaceConstants.getBaseClassNames()) {
                writeFromTemplate(baseClassName, targetPackageDir, dataModel, result);
            }

            Log.info("Done. Written: %d, rewritten: %d, errors: %d",
                    result.getWrittenCount(), result.getRewrittenCount(), result.getErrors().size());
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }

        return result;
    }

    private void writeFromTemplate(String baseClassName, Path targetPackageDir, Map<String, Object> dataModel, GenerationResult result) {
        String templateName = WorkspaceConstants.getTemplateName(baseClassName);
        String javaFileName = WorkspaceConstants.getJavaFileName(baseClassName);
        Path outputFile = targetPackageDir.resolve(javaFileName);

        boolean alreadyExists = Files.exists(outputFile);

        try {
            Template template = this.freemarkerConfig.getTemplate(templateName);

            StringWriter rendered = new StringWriter();
            template.process(dataModel, rendered);

            Path parentDir = outputFile.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            try (Writer fileWriter = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                fileWriter.write(rendered.toString());
            }

            if (alreadyExists) {
                Log.info("Rewrote '%s'.", outputFile);
                result.addRewritten(outputFile);
            } else {
                Log.info("Wrote '%s'.", outputFile);
                result.addWritten(outputFile);
            }

        } catch (IOException | TemplateException e) {
            Log.error(String.format("Failed to generate '%s' from template '%s': %s",
                    outputFile, templateName, e.getMessage()), e);
            result.addError("Failed to generate '" + javaFileName + "': " + e.getMessage());
        }
    }

    public static final class GenerationResult {
        private final List<Path> writtenFiles = new ArrayList<>();
        private final List<Path> rewrittenFiles = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        void addWritten(Path file) {
            this.writtenFiles.add(file);
        }

        void addRewritten(Path file) {
            this.rewrittenFiles.add(file);
        }

        void addError(String message) {
            this.errors.add(message);
        }

        public int getWrittenCount() {
            return this.writtenFiles.size();
        }

        public int getRewrittenCount() {
            return this.rewrittenFiles.size();
        }

        public List<Path> getWrittenFiles() {
            return Collections.unmodifiableList(this.writtenFiles);
        }

        public List<Path> getRewrittenFiles() {
            return Collections.unmodifiableList(this.rewrittenFiles);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(this.errors);
        }

        public boolean hasErrors() {
            return !this.errors.isEmpty();
        }
    }
}
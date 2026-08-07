package net.renan.photonplugin.workspaceclass.create;

import net.mcreator.ui.MCreator;

public class WorkspaceClassGeneratorForge extends WorkspaceClassGenerator {

    private static final String TEMPLATE_ROOT = "/forge-1.20.1/templates/";

    public WorkspaceClassGeneratorForge(MCreator mcreator) {
        super(mcreator, TEMPLATE_ROOT);
    }

    public static GenerationResult generate(MCreator mcreator) {
        return new WorkspaceClassGeneratorForge(mcreator).generate();
    }
}
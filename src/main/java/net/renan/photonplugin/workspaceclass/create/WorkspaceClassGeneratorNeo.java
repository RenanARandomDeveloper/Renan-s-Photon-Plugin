package net.renan.photonplugin.workspaceclass.create;

import net.mcreator.ui.MCreator;

public class WorkspaceClassGeneratorNeo extends WorkspaceClassGeneratorCommon {

    private static final String TEMPLATE_ROOT = "/neoforge-1.21.1/templates/";

    public WorkspaceClassGeneratorNeo(MCreator mcreator) {
        super(mcreator, TEMPLATE_ROOT);
    }

    public static GenerationResult generate(MCreator mcreator) {
        return new WorkspaceClassGeneratorNeo(mcreator).generate();
    }
}
package net.renan.photonplugin.workspaceclass.create;

import net.mcreator.ui.MCreator;

public class WorkspaceClassGeneratorNeo extends WorkspaceClassGenerator {

    private static final String TEMPLATE_ROOT_1_21_1 = "/neoforge-1.21.1/templates/";
    private static final String TEMPLATE_ROOT_26_1_2 = "/neoforge-26.1.2/templates/";

    public WorkspaceClassGeneratorNeo(MCreator mcreator) {
        super(mcreator, resolveTemplateRoot(mcreator));
    }

    private static String resolveTemplateRoot(MCreator mcreator) {
        String generatorName = mcreator.getGenerator().getGeneratorName();

        if (generatorName != null && generatorName.contains("26.1.2")) {
            return TEMPLATE_ROOT_26_1_2;
        }

        return TEMPLATE_ROOT_1_21_1;
    }

    public static GenerationResult generate(MCreator mcreator) {
        return new WorkspaceClassGeneratorNeo(mcreator).generate();
    }
}